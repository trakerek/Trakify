package com.example.trakify;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

public class PlayerFragment extends Fragment implements MusicPlayerManager.PlaybackListener {

    private static final String TAG = "PlayerFragment";
    private TextView tvTitle, tvElapsed, tvTotal;
    private SeekBar seekBar;
    private final Handler checkHandler = new Handler(Looper.getMainLooper());
    private int lastProgress = -1;
    private int stalledCount = 0;
    private ImageButton btnPlayPause, btnNext, btnPrevious;
    private ImageView ivAlbumArt;
    private Handler handler;
    private Runnable updater;
    private MusicPlayerManager player;
    private boolean userSeeking = false;
    private static final int UPDATE_MS = 200;

    private static final int MAX_STALLED_COUNT = 15; // 15 * 200ms = 3s
    private Runnable checkRunnable;

    public PlayerFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_player, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvTitle = view.findViewById(R.id.tvPlayerTitle);
        tvElapsed = view.findViewById(R.id.tvElapsed);
        tvTotal = view.findViewById(R.id.tvTotal);
        seekBar = view.findViewById(R.id.seekBar);
        btnPlayPause = view.findViewById(R.id.btnPlayPauseFull);
        btnNext = view.findViewById(R.id.btnNext);
        btnPrevious = view.findViewById(R.id.btnPrevious);
        ivAlbumArt = view.findViewById(R.id.ivAlbumArt);

        player = MusicPlayerManager.getInstance();
        if (player == null) throw new IllegalStateException("MusicPlayerManager not initialized");

        handler = new Handler(Looper.getMainLooper());

        updater = new Runnable() {
            @Override
            public void run() {
                if (player != null) {
                    if (!userSeeking) {
                        updateUI();
                    }
                    handler.postDelayed(this, UPDATE_MS);
                }
            }
        };

        // Play/Pause button
        btnPlayPause.setOnClickListener(v -> {
            Log.d(TAG, "Play/Pause clicked. Currently playing: " + player.isPlaying());
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.resume();
            }
            updatePlayPauseIcon();
        });

        // Next button
        btnNext.setOnClickListener(v -> {
            Log.d(TAG, "Next clicked");
            player.playNext();
        });

        // Previous button
        btnPrevious.setOnClickListener(v -> {
            Log.d(TAG, "Previous clicked");
            player.playPrevious();
        });

        // SeekBar listener
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvElapsed.setText(formatTime(progress * 1000));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
                Log.d(TAG, "User started seeking");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int seekMs = seekBar.getProgress() * 1000;
                Log.d(TAG, "Seeking to: " + seekMs + "ms");
                player.seekTo(seekMs);

                handler.postDelayed(() -> {
                    userSeeking = false;
                    updateUI();
                }, 150);
            }
        });

        updateUI();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart - registering listener");
        player.registerListener(this);
        handler.removeCallbacks(updater);
        handler.post(updater);
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop - unregistering listener");
        player.unregisterListener(this);
        handler.removeCallbacks(updater);
    }

    private void updateUI() {
        try {
            // jeśli buforowanie — pokaż status ładowania tylko gdy serwis NIE MA duration
            int pos = player.getCurrentPosition();
            int dur = player.getDuration();

            boolean hasDuration = dur > 0;

            if (player.isBuffering() && !hasDuration) {
                tvElapsed.setText("Ładowanie...");
                tvTotal.setText("--:--");
                seekBar.setEnabled(false);
                seekBar.setProgress(0);
                // ustaw ikonkę play (bo jeszcze nie gra)
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                // tytuł nadal aktualizujemy
                String title = player.getCurrentTitle();
                tvTitle.setText(title != null ? title : "Ładowanie...");
                // album art: możesz zostawić placeholder
                ivAlbumArt.setImageResource(R.drawable.ic_album_placeholder);
                return;
            }

            // Jeśli mamy duration lub serwis nie jest w stanie 'bez-danych' - odblokuj seek
            int maxSeconds = hasDuration ? dur / 1000 : 100;
            seekBar.setMax(maxSeconds);
            seekBar.setProgress(Math.max(0, pos / 1000));
            seekBar.setEnabled(true); // OD BLOKOWANE: gdy już jest info o czasie, pozwól seekować

            // Update time labels
            tvElapsed.setText(formatTime(pos));
            tvTotal.setText(hasDuration ? formatTime(dur) : "--:--");

            // Update title
            String title = player.getCurrentTitle();
            tvTitle.setText(title != null ? title : "Brak utworu");
            // Update album art
            updateAlbumArt();

            // Update buttons
            updatePlayPauseIcon();
            updateNavigationButtons();
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI", e);
        }
    }



    private void updateAlbumArt() {
        String defaultUrl = "https://img.freepik.com/premium-wektory/plyta-winylowa-i-wykres-dzwiekowy-na-czarnym-tle-ilustracja-muzyczna-ikona-wektor_484720-2957.jpg";
        String imageUrl = player.getCurrentImageUrl();
        // jeśli currentImageUrl jest null/empty -> użyj defaultUrl
        String toLoad = (imageUrl != null ) ? imageUrl : defaultUrl;

        // Glide ładuje, pokazuje placeholder i error
        try {
            Glide.with(ivAlbumArt.getContext())
                    .load(toLoad)
                    .placeholder(R.drawable.ic_album_placeholder) // masz taki placeholder?
                    .error(R.drawable.ic_album_placeholder)
                    .centerCrop()
                    .into(ivAlbumArt);
        } catch (Exception e) {
            Log.e(TAG, "Glide load error", e);
            // fallback - ustaw lokalny placeholder
            ivAlbumArt.setImageResource(R.drawable.ic_album_placeholder);
        }
    }


    private void updatePlayPauseIcon() {
        try {
            if (player.isBuffering()) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                return;
            }
            boolean playing = player.isPlaying();
            btnPlayPause.setImageResource(playing ?
                    android.R.drawable.ic_media_pause :
                    android.R.drawable.ic_media_play);
        } catch (Exception e) {
            Log.e(TAG, "Error updating play/pause icon", e);
        }
    }


    private void updateNavigationButtons() {
        // Enable/disable next/previous based on availability
        btnNext.setEnabled(player.hasNext());
        btnNext.setAlpha(player.hasNext() ? 1.0f : 0.3f);

        btnPrevious.setEnabled(player.hasPrevious());
        btnPrevious.setAlpha(player.hasPrevious() ? 1.0f : 0.3f);
    }

    private String formatTime(int ms) {
        int totalSec = Math.max(0, ms / 1000);
        int mins = totalSec / 60;
        int secs = totalSec % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    @Override
    public void onPrepared() {
        Log.d(TAG, "onPrepared callback");
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (!userSeeking) {
                    updateUI();
                }
            });
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        startPlaybackMonitor();
    }
    @Override
    public void onPause() {
        super.onPause();
        checkHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onPlaybackStateChanged() {
        Log.d(TAG, "onPlaybackStateChanged callback");
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                updateUI(); // pełny update UI (seekbar, times, buttons)
            });
        }
    }
    private void startPlaybackMonitor() {
        if (checkRunnable != null) checkHandler.removeCallbacks(checkRunnable);

        checkRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    int pos = player.getCurrentPosition();
                    int dur = player.getDuration();

                    // jeśli duration == 0 -> nadal ładuje
                    boolean notReady = dur <= 0;

                    // jeśli progress się nie zmienia -> możliwe zablokowanie
                    boolean stalled = 0 == lastProgress;
                    lastProgress = pos;

                    if (stalled) {
                        stalledCount++;
                    } else {
                        stalledCount = 0;
                    }

                    if (notReady || stalledCount >= MAX_STALLED_COUNT) {
                        Log.w(TAG, "Player seems stuck. Reloading track...");
                        reloadCurrentTrack();
                        stalledCount = 0;
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error in playback monitor", e);
                } finally {
                    checkHandler.postDelayed(this, 200); // co 200ms
                }
            }
        };

        checkHandler.post(checkRunnable);
    }
    private void reloadCurrentTrack() {
        try {
            String path = player.getCurrentPath();
            String title = player.getCurrentTitle();
            String image = player.getCurrentImageUrl();

            if (path != null && !path.isEmpty()) {
                Log.d(TAG, "Reloading track: " + title);
                player.play(path, title, image);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to reload track", e);
        }
    }


}