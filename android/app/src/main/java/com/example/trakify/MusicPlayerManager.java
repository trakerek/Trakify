package com.example.trakify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MusicPlayerManager {
    private static final String TAG = "MusicPlayerManager";
    private static MusicPlayerManager instance;
    private final Set<PlaybackListener> listeners = Collections.synchronizedSet(new HashSet<>());

    private Context appContext;
    private boolean isPlaying = false;
    private int currentPosition = 0;
    private int duration = 0;
    private String currentTitle = null;
    private String currentPath = null;
    private String currentImageUrl = null;
    private String currentAlbumImageUrl = null;

    // Queue system
    private final List<QueueItem> queue = new ArrayList<>();
    private final List<QueueItem> history = new ArrayList<>();
    private int currentQueueIndex = -1;
    private boolean isBuffering = false;
    private int currentIndex = 0;


    // prevent parallel downloads for the same item
    // implemented per QueueItem via isDownloading flag

    public void registerListener(PlaybackListener l) {
        if (l != null) {
            listeners.add(l);
            Log.d(TAG, "Listener registered. Total listeners: " + listeners.size());
        }
    }

    public void unregisterListener(PlaybackListener l) {
        if (l != null) {
            listeners.remove(l);
            Log.d(TAG, "Listener unregistered. Total listeners: " + listeners.size());
        }
    }
    public void setQueue(List<QueueItem> q) {
        queue.clear();
        queue.addAll(q);
        currentIndex = 0;
        for (QueueItem item : queue) {
            if (item.imageUrl == null || item.imageUrl.isEmpty()) {
                item.imageUrl = currentAlbumImageUrl; // 👈 ujednolicenie obrazka albumu dla całej kolejki
            }
        }
    }
    public void setCurrentAlbumImageUrl(String url) {
        currentAlbumImageUrl = url;
    }
    private void notifyStateChanged() {
        Log.d(TAG, "notifyStateChanged - listeners: " + listeners.size());
        for (PlaybackListener l : listeners) {
            try {
                l.onPlaybackStateChanged();
            } catch (Exception e) {
                Log.e(TAG, "Error in listener", e);
            }
        }
    }

    private void notifyPrepared() {
        Log.d(TAG, "notifyPrepared - listeners: " + listeners.size());
        for (PlaybackListener l : listeners) {
            try {
                l.onPrepared();
            } catch (Exception e) {
                Log.e(TAG, "Error in listener", e);
            }
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String act = intent.getAction();

            try {
                // --- PLAYBACK STATE (service mówi czy gra / buffering / title / path / image / error) ---
                if (MusicPlayerService.BROADCAST_PLAYBACK_STATE.equals(act)) {
                    boolean newPlaying = intent.getBooleanExtra("playing", false);
                    // serwis może wysyłać extra buffering/isBuffering
                    boolean buffering = intent.getBooleanExtra("buffering", false)
                            || intent.getBooleanExtra("isBuffering", false);

                    String title = intent.getStringExtra("title");
                    String path = intent.getStringExtra("path");
                    String image = intent.getStringExtra("image");
                    String error = intent.getStringExtra("error"); // opcjonalne

                    Log.d(TAG, "Received PLAYBACK_STATE - playing: " + newPlaying
                            + " buffering: " + buffering
                            + " title: " + title
                            + " path: " + path
                            + " error: " + error);

                    // jeśli serwis raportuje błąd — zatrzymujemy buforowanie/odtwarzanie
                    if (error != null && !error.isEmpty()) {
                        isBuffering = false;
                        isPlaying = false;
                        if (title != null) currentTitle = title;
                        notifyStateChanged();
                        return;
                    }

                    // aktualizuj path/title/image jeśli są obecne
                    if (path != null) currentPath = path;
                    if (title != null) currentTitle = title;
                    if (image != null) currentImageUrl = image;

                    // priorytet: jeśli serwis mówi że buforuje -> ustaw buffering
                    if (buffering) {
                        isBuffering = true;
                        isPlaying = false;
                    } else {
                        // jeśli nie buforuje, ustaw podle playing flagi
                        isBuffering = false;
                        isPlaying = newPlaying;
                    }

                    notifyStateChanged();

                    // --- PLAYBACK POS (pozycja / duration) ---
                } else if (MusicPlayerService.BROADCAST_PLAYBACK_POS.equals(act)) {
                    int pos = intent.getIntExtra("position", 0);
                    int dur = intent.getIntExtra("duration", 0);

                    Log.v(TAG, "Received PLAYBACK_POS - pos: " + pos + ", dur: " + dur);

                    currentPosition = pos;
                    duration = dur;

                    // Jeżeli serwis raportuje długość > 0 to najpewniej playback jest przygotowany
                    if (dur > 0) {
                        isBuffering = false; // service potwierdził, że ma info o czasie -> koniec bufferingu
                    }

                    notifyPrepared();

                    // --- TRACK COMPLETED (automatyczne przejście do następnego) ---
                } else if (MusicPlayerService.BROADCAST_TRACK_COMPLETED.equals(act)) {
                    Log.d(TAG, "Track completed broadcast received -> playNext()");
                    playNext();

                    // --- fallback / nieobsługiwane akcje ---
                } else {
                    Log.v(TAG, "Received unknown broadcast action: " + act);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onReceive", e);
            }
        }
    };


    private MusicPlayerManager() {}

    public static synchronized MusicPlayerManager getInstance() {
        if (instance == null) instance = new MusicPlayerManager();
        return instance;
    }

    public void init(Context ctx) {
        if (ctx == null) return;
        this.appContext = ctx.getApplicationContext();

        IntentFilter f = new IntentFilter();
        f.addAction(MusicPlayerService.BROADCAST_PLAYBACK_STATE);
        f.addAction(MusicPlayerService.BROADCAST_PLAYBACK_POS);
        f.addAction(MusicPlayerService.BROADCAST_TRACK_COMPLETED);

        Log.d(TAG, "Registering broadcast receiver");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            ContextCompat.registerReceiver(appContext, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
        }

        Log.d(TAG, "MusicPlayerManager initialized");
    }

    public void release() {
        if (appContext != null) {
            try {
                appContext.unregisterReceiver(receiver);
                Log.d(TAG, "Broadcast receiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver", e);
            }
        }
    }

    public void updateTrackPath(String title, String newPath,String albumimageURL) {
        synchronized (queue) {
            for (QueueItem item : queue) {
                if (item.title != null && item.title.equals(title)) {
                    item.path = newPath;
                    item.isDownloading = false;
                    if (albumimageURL != null && !albumimageURL.isEmpty()) {
                        item.imageUrl = albumimageURL;
                    }
                    Log.d(TAG, "Zaktualizowano path dla: " + title + " -> " + newPath);
                    // jeśli to aktualnie odtwarzany element i jest brak odtwarzania teraz -> spróbuj odtworzyć
                    if (currentQueueIndex >= 0 && currentQueueIndex < queue.size() && queue.get(currentQueueIndex) == item) {
                        if (newPath != null) {
                            play(newPath, item.title, item.imageUrl);
                        }
                    }
                    break;
                }
            }
        }
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public interface PlaybackListener {
        void onPrepared();
        void onPlaybackStateChanged();
    }

    // Play a single track (immediately) - we will NOT call service if path == null
    public void play(String path, String title) {
        play(path, title, null);
    }

    public void play(String path, String title, String imageUrl) {
        if (appContext == null) return;

        // Add current song to history before playing new one
        if (currentPath != null && !currentPath.equals(path)) {
            addToHistory(new QueueItem(currentPath, currentTitle, null ,currentImageUrl));
        }

        if (path == null || path.isEmpty()) {
            Log.w(TAG, "play() refused: path is null -> will not call service. title: " + title);
            currentTitle = title != null ? title : currentTitle;
            // set buffering - we're waiting for download
            isBuffering = true;
            isPlaying = false;
            notifyStateChanged();
            return;
        }

        // Reset position/duration for new track
        currentPosition = 0;
        duration = 0;

        currentPath = path;
        currentTitle = title != null ? title : "Brak utworu";

        // CRITICAL FIX: Always update imageUrl, even if null
        // This clears old album art when playing songs without images
        currentImageUrl = imageUrl;

        Log.d(TAG, "play() - path: " + path + ", title: " + title + ", imageUrl: " + imageUrl);

        // we start the service and wait for service broadcast to mark playing
        isBuffering = true;
        isPlaying = false;

        notifyStateChanged();

        Intent i = new Intent(appContext, MusicPlayerService.class);
        i.setAction(MusicPlayerService.ACTION_PLAY_PATH);
        i.putExtra(MusicPlayerService.EXTRA_PATH, path);
        i.putExtra(MusicPlayerService.EXTRA_TITLE, title);
        i.putExtra(MusicPlayerService.EXTRA_IMAGE, imageUrl);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(appContext, i);
        } else {
            appContext.startService(i);
        }
    }


    // Add song to queue
    // note: third parameter is used as "meta" (e.g. artist) in many call sites
    public void addToQueue(String path, String title, String meta,String url) {
        synchronized (queue) {
            QueueItem qi = new QueueItem(path, title, meta,url);
            queue.add(qi);
            Log.d(TAG, "Added to queue: " + title + ". Queue size: " + queue.size());

            // if queue was empty before, set currentQueueIndex to 0 so playNext/playFromQueue behaves well
            if (currentQueueIndex < 0) {
                currentQueueIndex = 0;
            }
        }
    }

    // Play from queue with index
    public void playFromQueue(int index) {
        synchronized (queue) {
            if (index >= 0 && index < queue.size()) {
                currentQueueIndex = index;
                QueueItem item = queue.get(index);
                Log.d(TAG, "playFromQueue index=" + index + " item=" + item);
                // if we have valid local file -> play
                if (item.path != null && new File(item.path).exists()) {
                    play(item.path, item.title, item.imageUrl);
                    Log.d(TAG, "Playing existing file: " + item.path);
                } else {
                    // no file yet -> start async download and play when ready
                    if (!item.isDownloading) {
                        item.isDownloading = true;
                        startDownloadForQueueItem(item, index, true);
                    } else {
                        Log.d(TAG, "Item is already downloading: " + item.title);
                    }
                }
            } else {
                Log.d(TAG, "playFromQueue: index out of bounds: " + index);
            }
        }
    }

    // Play next song in queue
    public void playNext() {
        synchronized (queue) {
            if (queue.isEmpty()) {
                Log.w(TAG, "playNext: queue empty");
                return;
            }

            currentQueueIndex++;
            if (currentQueueIndex >= queue.size()) {
                currentQueueIndex = 0; // możesz też tu zakończyć odtwarzanie, jeśli nie chcesz zapętlać
            }

            QueueItem item = queue.get(currentQueueIndex);
            Log.d(TAG, "playNext -> NEW index: " + currentQueueIndex + "/" + queue.size());
            Log.d(TAG, "playNext -> will play: " + item);

            if (item.path != null && new File(item.path).exists()) {
                play(item.path, item.title, item.imageUrl);
            } else {
                Log.w(TAG, "playNext: no local file, downloading...");
                if (!item.isDownloading) {
                    item.isDownloading = true;
                    startDownloadForQueueItem(item, currentQueueIndex, true);
                } else {
                    Log.d(TAG, "playNext: item already downloading -> wait");
                    isBuffering = true;
                    isPlaying = false;
                    notifyStateChanged();
                }
            }
        }
    }



    // Play previous song: prefer queue previous item, else history
    public void playPrevious() {
        synchronized (queue) {
            if (queue.isEmpty()) {
                Log.w(TAG, "playPrevious: queue empty");
                return;
            }

            currentQueueIndex--;
            if (currentQueueIndex < 0) {
                currentQueueIndex = queue.size() - 1;
            }

            QueueItem item = queue.get(currentQueueIndex);
            Log.d(TAG, "playPrevious -> NEW index: " + currentQueueIndex + "/" + queue.size());
            Log.d(TAG, "playPrevious -> will play: " + item);

            if (item.path != null && new File(item.path).exists()) {
                play(item.path, item.title, item.imageUrl);
            } else {
                Log.w(TAG, "playPrevious: no local file, downloading...");
                if (!item.isDownloading) {
                    item.isDownloading = true;
                    startDownloadForQueueItem(item, currentQueueIndex, true);
                } else {
                    Log.d(TAG, "playPrevious: item already downloading -> wait");
                    isBuffering = true;
                    isPlaying = false;
                    notifyStateChanged();
                }
            }
        }
    }



    // Check if there's a next song
    public boolean hasNext() {
        synchronized (queue) {
            return !queue.isEmpty() && (currentQueueIndex < queue.size() - 1 || queue.size() > 1);
        }
    }

    // Check if there's previous in history or queue
    public boolean hasPrevious() {
        synchronized (queue) {
            return !history.isEmpty() || currentQueueIndex > 0 || currentPosition > 5000;
        }
    }

    private void addToHistory(QueueItem item) {
        if (item == null) return;
        history.add(item);
        // Keep history limited to last 50 songs
        if (history.size() > 50) {
            history.remove(0);
        }
    }

    public void clearQueue() {
        synchronized (queue) {
            queue.clear();
            currentQueueIndex = -1;
        }
        Log.d(TAG, "Queue cleared");
    }

    public void pause() {
        if (appContext == null) return;
        Log.d(TAG, "pause() called");

        Intent i = new Intent(appContext, MusicPlayerService.class);
        i.setAction(MusicPlayerService.ACTION_PAUSE);
        appContext.startService(i);

        isPlaying = false;
        notifyStateChanged();
    }

    public void resume() {
        if (appContext == null) return;
        Log.d(TAG, "resume() called");

        Intent i = new Intent(appContext, MusicPlayerService.class);
        i.setAction(MusicPlayerService.ACTION_PLAY);
        appContext.startService(i);

        isPlaying = true;
        notifyStateChanged();
    }

    public void stop() {
        if (appContext == null) return;
        Log.d(TAG, "stop() called");

        Intent i = new Intent(appContext, MusicPlayerService.class);
        i.setAction(MusicPlayerService.ACTION_STOP);
        appContext.startService(i);
    }

    public void seekTo(int ms) {
        if (appContext == null) return;
        Log.d(TAG, "seekTo() called - ms: " + ms);

        Intent i = new Intent(appContext, MusicPlayerService.class);
        i.setAction(MusicPlayerService.ACTION_SEEK);
        i.putExtra(MusicPlayerService.EXTRA_SEEK_MS, ms);
        appContext.startService(i);
    }

    // Getters
    public boolean isPlaying() { return isPlaying; }
    public int getCurrentPosition() { return currentPosition; }
    public int getDuration() { return duration; }
    public String getCurrentTitle() { return currentTitle; }
    public String getCurrentImageUrl() { return currentImageUrl; }

    public void setCurrentImageUrl(String url) { currentImageUrl = url; }
    public List<QueueItem> getQueue() { synchronized (queue) { return new ArrayList<>(queue); } }
    public int getQueueSize() { synchronized (queue) { return queue.size(); } }

    // Async download helper — uses Chaquopy main.play_song(query, outputPath) like your fragments
    private void startDownloadForQueueItem(QueueItem item, int index, boolean playWhenReady) {
        if (item == null) return;

        new Thread(() -> {
            try {
                // prepare paths
                File dir = appContext.getExternalFilesDir("Music");
                if (dir == null) {
                    Log.e(TAG, "Storage unavailable for downloads");
                    item.isDownloading = false;
                    return;
                }

                // meta often contains artist — fallback to title if not
                String artist = item.meta != null ? item.meta : "";
                String filename = (artist + "_" + item.title).replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".mp3";
                File outFile = new File(dir, filename);
                String outputPath = outFile.getAbsolutePath();

                // If file already downloaded by someone else while we queued -> just set path
                if (outFile.exists()) {
                    item.path = outFile.getAbsolutePath();
                    item.isDownloading = false;
                    Log.d(TAG, "startDownload: file already exists -> " + item.path);
                    if (playWhenReady && index == currentQueueIndex) {
                        play(item.path, item.title, item.imageUrl);
                    }
                    notifyStateChanged();
                    return;
                }

                // call python main.play_song(query, outputPath)
                Python py = Python.getInstance();
                PyObject mainModule = py.getModule("main");

                String query = (artist.isEmpty() ? item.title : (artist + " " + item.title)).trim();
                Log.d(TAG, "startDownload: downloading: " + query + " -> " + outputPath);

                PyObject result = mainModule.callAttr("play_song", query, outputPath);
                String path = (result != null) ? result.toString() : null;

                if (path != null && !path.isEmpty()) {
                    item.path = path;
                    item.isDownloading = false;
                    Log.d(TAG, "startDownload: downloaded -> " + path);

                    // If this is the currently requested to play item, play it now
                    synchronized (queue) {
                        if (playWhenReady && index == currentQueueIndex) {
                            play(path, item.title, item.imageUrl);
                        }
                    }

                    // notify UI
                    notifyStateChanged();
                } else {
                    item.isDownloading = false;
                    Log.w(TAG, "startDownload: download returned no path for " + item.title);
                }
            } catch (Exception e) {
                item.isDownloading = false;
                Log.e(TAG, "Error downloading queue item: " + item.title, e);
            }
        }).start();
    }

    public boolean isBuffering() { return isBuffering; }
    // Queue item data class
    public static class QueueItem {
        public String path;
        public String title;
        public String imageUrl;
        public String meta; // used for artist or other metadata
        public boolean isDownloading = false;

        public QueueItem(String path, String title, String meta, String imgurl) {
            this.path = path;
            this.title = title;
            this.imageUrl = imgurl;
            this.meta = meta; // jeśli meta to też artist, nie szkodzi
        }



        @Override
        public String toString() {
            return "QueueItem{title=" + title + ", path=" + path + ", meta=" + meta + ", isDownloading=" + isDownloading + "}";
        }

    }
}

