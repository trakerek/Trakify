package com.example.trakify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.File;
import java.io.IOException;

public class MusicPlayerService extends Service {
    public static final String TAG = "MusicPlayerService";

    public static final String ACTION_PLAY = "com.example.trakify.action.PLAY";
    public static final String ACTION_PAUSE = "com.example.trakify.action.PAUSE";
    public static final String ACTION_STOP = "com.example.trakify.action.STOP";
    public static final String ACTION_PLAY_PATH = "com.example.trakify.action.PLAY_PATH";
    public static final String ACTION_SEEK = "com.example.trakify.action.SEEK";
    public static final String ACTION_NEXT = "com.example.trakify.action.NEXT";
    public static final String ACTION_PREV = "com.example.trakify.action.PREV";

    public static final String EXTRA_PATH = "extra_path";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_SEEK_MS = "extra_seek_ms";
    public static final String EXTRA_IMAGE = "extra_image";

    public static final String BROADCAST_PLAYBACK_STATE = "com.example.trakify.BROADCAST_PLAYBACK_STATE";
    public static final String BROADCAST_PLAYBACK_POS = "com.example.trakify.BROADCAST_PLAYBACK_POS";
    public static final String BROADCAST_TRACK_COMPLETED = "com.example.trakify.BROADCAST_TRACK_COMPLETED";

    private static final String CHANNEL_ID = "trakify_media_channel";
    private static final int NOTIF_ID = 0x1001;

    private MediaSessionCompat mediaSession;
    private MediaPlayer mediaPlayer;
    private NotificationManagerCompat notifMgr;

    private String currentTitle;
    private String currentPath;
    private Bitmap currentImageBitmap;
    private String currentImageUrl;
    private String currentAlbumImageUrl;
    private boolean albumMode = false;

    private final Handler posHandler = new Handler(Looper.getMainLooper());
    private final Runnable posRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null) {
                try {
                    sendPositionBroadcast();
                    // Update notification every 1 second to refresh progress
                    updateNotification(mediaPlayer.isPlaying());
                    posHandler.postDelayed(this, 1000);
                } catch (Exception e) {
                    Log.e(TAG, "Error in posRunnable", e);
                    posHandler.postDelayed(this, 1000);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        notifMgr = NotificationManagerCompat.from(this);

        createNotificationChannel();

        mediaSession = new MediaSessionCompat(this, "TrakifyMediaSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setActive(true);

        // handle incoming media button actions via callback
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                resume();
            }

            @Override
            public void onPause() {
                super.onPause();
                pause();
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                Log.d(TAG, "MediaSession skip to next");
                MusicPlayerManager.getInstance().playNext();
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                Log.d(TAG, "MediaSession skip to prev");
                MusicPlayerManager.getInstance().playPrevious();
            }

            @Override
            public void onStop() {
                super.onStop();
                stopAndCleanup();
            }
        });

        // init media player but keep it null until needed (we'll create on demand)
        initMediaPlayerIfNeeded();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand - action: " + action);

        if (ACTION_PLAY_PATH.equals(action)) {
            String path = intent.getStringExtra(EXTRA_PATH);
            String title = intent.getStringExtra(EXTRA_TITLE);
            String image = intent.getStringExtra(EXTRA_IMAGE);

            if (image != null && !image.equals(currentAlbumImageUrl)) {
                albumMode = true;
                currentAlbumImageUrl = image;
            }

            String usedImage = albumMode && currentAlbumImageUrl != null ? currentAlbumImageUrl : image;
            currentImageUrl = usedImage;

            playPath(path, title, usedImage);

        } else if (ACTION_PLAY.equals(action)) {
            resume();

        } else if (ACTION_PAUSE.equals(action)) {
            pause();

        } else if (ACTION_STOP.equals(action)) {
            stopAndCleanup();

        } else if (ACTION_SEEK.equals(action)) {
            int ms = intent.getIntExtra(EXTRA_SEEK_MS, -1);
            if (ms >= 0) seekTo(ms);

        } else if (ACTION_NEXT.equals(action)) {
            Log.d(TAG, "ACTION_NEXT received");
            try {
                MusicPlayerManager.getInstance().playNext();
            } catch (Exception e) {
                Log.e(TAG, "Error ACTION_NEXT", e);
            }
            updateNotification(mediaPlayer != null && mediaPlayer.isPlaying());

        } else if (ACTION_PREV.equals(action)) {
            Log.d(TAG, "ACTION_PREV received");
            try {
                MusicPlayerManager.getInstance().playPrevious();
            } catch (Exception e) {
                Log.e(TAG, "Error ACTION_PREV", e);
            }
            updateNotification(mediaPlayer != null && mediaPlayer.isPlaying());
        }

        return START_STICKY;
    }

    private void playPath(String path, String title, @Nullable String imageUrl) {
        if (path == null) return;
        File f = new File(path);
        if (!f.exists() || f.isDirectory()) {
            Log.e(TAG, "playPath: invalid file: " + path);
            return;
        }

        currentPath = path;
        currentTitle = title != null ? title : f.getName();
        currentImageUrl = imageUrl;

        // initialize/reset player safely
        initMediaPlayerIfNeeded();

        try {
            mediaPlayer.reset();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());

            try {
                mediaPlayer.setDataSource(path);
            } catch (SecurityException se) {
                Log.e(TAG, "SecurityException setDataSource: " + se.getMessage(), se);
                // notify UI of error
                Intent b = new Intent(BROADCAST_PLAYBACK_STATE);
                b.setPackage(getPackageName());
                b.putExtra("playing", false);
                b.putExtra("error", "permission_denied");
                sendBroadcast(b);
                return;
            }

            // show initial notification (buffering state)
            startForeground(NOTIF_ID, buildNotification(false));

            // prepare async
            mediaPlayer.prepareAsync();

            // load art async if url given
            if (currentImageUrl != null && !currentImageUrl.isEmpty()) {
                loadAlbumArtAsync(currentImageUrl);
            } else {
                currentImageBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_album_placeholder);
                updateNotification(false);
            }

        } catch (IOException | IllegalStateException e) {
            Log.e(TAG, "playPath failed", e);
            stopAndCleanup();
        }
    }

    private void initMediaPlayerIfNeeded() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

            mediaPlayer.setOnPreparedListener(mp -> {
                try {
                    mp.start();
                    updateNotification(true);
                    sendStateBroadcast(true);

                    // start position updates
                    posHandler.removeCallbacks(posRunnable);
                    posHandler.post(posRunnable);

                    // update playback state on media session with proper actions
                    updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING);
                } catch (Exception e) {
                    Log.e(TAG, "onPrepared error", e);
                }
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "MediaPlayer onCompletion");
                sendStateBroadcast(false);
                posHandler.removeCallbacks(posRunnable);

                // forward to manager so it can start next (and potentially download it)
                Intent completedIntent = new Intent(BROADCAST_TRACK_COMPLETED);
                completedIntent.setPackage(getPackageName());
                sendBroadcast(completedIntent);

                // update media session state
                updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED);
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error what=" + what + " extra=" + extra);
                stopAndCleanup();
                return true;
            });
        } else {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (Exception ignored) {}
            try { mediaPlayer.reset(); } catch (Exception ignored) {}
        }
    }

    private void updateMediaSessionPlaybackState(int state) {
        long position = mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setState(state, position, 1.0f)
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_STOP |
                                PlaybackStateCompat.ACTION_SEEK_TO
                );

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void resume() {
        Log.d(TAG, "resume()");
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
                updateNotification(true);
                sendStateBroadcast(true);
                posHandler.removeCallbacks(posRunnable);
                posHandler.post(posRunnable);
                updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING);
            } catch (Exception e) {
                Log.e(TAG, "resume error", e);
            }
        }
    }

    private void pause() {
        Log.d(TAG, "pause()");
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
                updateNotification(false);
                sendStateBroadcast(false);
                updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED);
            } catch (Exception e) {
                Log.e(TAG, "pause error", e);
            }
        }
    }

    private void seekTo(int ms) {
        Log.d(TAG, "seekTo - ms: " + ms);
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(ms);
                sendPositionBroadcast();
                updateMediaSessionPlaybackState(
                        mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED
                );
            } catch (Exception e) {
                Log.e(TAG, "seekTo error", e);
            }
        }
    }

    private void stopAndCleanup() {
        Log.d(TAG, "stopAndCleanup()");
        try {
            if (mediaPlayer != null) {
                try { mediaPlayer.stop(); } catch (Exception ignored) {}
                try { mediaPlayer.reset(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping mediaPlayer", e);
        }

        albumMode = false;
        currentAlbumImageUrl = null;
        posHandler.removeCallbacks(posRunnable);
        try {
            stopForeground(true);
        } catch (Exception ignored) {}
        stopSelf();
        sendStateBroadcast(false);
        updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED);
    }

    private void sendStateBroadcast(boolean playing) {
        Intent b = new Intent(BROADCAST_PLAYBACK_STATE);
        b.setPackage(getPackageName());
        b.putExtra("playing", playing);
        if (currentTitle != null) b.putExtra("title", currentTitle);
        if (currentPath != null) b.putExtra("path", currentPath);
        if (currentImageUrl != null) b.putExtra("image", currentImageUrl);
        sendBroadcast(b);
    }

    private void sendPositionBroadcast() {
        if (mediaPlayer == null) return;
        try {
            int pos = mediaPlayer.getCurrentPosition();
            int dur = mediaPlayer.getDuration();
            Intent b = new Intent(BROADCAST_PLAYBACK_POS);
            b.setPackage(getPackageName());
            b.putExtra("position", pos);
            b.putExtra("duration", dur);
            if (currentPath != null) b.putExtra("path", currentPath);
            if (currentImageUrl != null) b.putExtra("image", currentImageUrl);
            sendBroadcast(b);
        } catch (Exception e) {
            Log.e(TAG, "Error sending position broadcast", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Trakify odtwarzacz", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Kanał powiadomień odtwarzacza");
            NotificationManager nm = (NotificationManager) getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private PendingIntent makeServicePendingIntent(String action, int requestCode) {
        Intent i = new Intent(this, MusicPlayerService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification(boolean isPlaying) {
        String title = currentTitle != null ? currentTitle : "Brak utworu";
        String artist = "";

        // Unique request codes to avoid PendingIntent collisions
        PendingIntent prevPending = makeServicePendingIntent(ACTION_PREV, 101);
        PendingIntent playPending = makeServicePendingIntent(isPlaying ? ACTION_PAUSE : ACTION_PLAY, 102);
        PendingIntent nextPending = makeServicePendingIntent(ACTION_NEXT, 103);
        PendingIntent stopPending = makeServicePendingIntent(ACTION_STOP, 104);
        PendingIntent openAppPending = PendingIntent.getActivity(this, 105,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Use custom drawable resources instead of system icons
        NotificationCompat.Action actionPrev =
                new NotificationCompat.Action(R.drawable.ic_media_previous, "Previous", prevPending);
        NotificationCompat.Action actionPlay =
                new NotificationCompat.Action(
                        isPlaying ? R.drawable.ic_media_pause : R.drawable.ic_media_play,
                        isPlaying ? "Pause" : "Play",
                        playPending
                );
        NotificationCompat.Action actionNext =
                new NotificationCompat.Action(R.drawable.ic_media_next, "Next", nextPending);

        MediaStyle style = new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(R.drawable.ic_stat_music)
                .setContentIntent(openAppPending)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setStyle(style)
                .addAction(actionPrev)
                .addAction(actionPlay)
                .addAction(actionNext);

        // large icon (album art)
        Bitmap large = currentImageBitmap != null
                ? currentImageBitmap
                : BitmapFactory.decodeResource(getResources(), R.drawable.ic_album_placeholder);
        b.setLargeIcon(large);

        return b.build();
    }

    private void updateNotification(boolean isPlaying) {
        try {
            Notification n = buildNotification(isPlaying);
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            notifMgr.notify(NOTIF_ID, n);
            startForeground(NOTIF_ID, n);
        } catch (Exception e) {
            Log.e(TAG, "updateNotification error", e);
        }
    }

    private void loadAlbumArtAsync(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            Glide.with(this)
                    .asBitmap()
                    .load(url)
                    .placeholder(R.drawable.ic_album_placeholder)
                    .error(R.drawable.ic_album_placeholder)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                            currentImageBitmap = resource;
                            updateNotification(mediaPlayer != null && mediaPlayer.isPlaying());
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            // ignore
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            currentImageBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_album_placeholder);
                            updateNotification(mediaPlayer != null && mediaPlayer.isPlaying());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "loadAlbumArtAsync error", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        posHandler.removeCallbacks(posRunnable);
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        }
        try {
            stopForeground(true);
        } catch (Exception ignored) {}
        mediaSession.release();
    }
}