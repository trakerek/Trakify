package com.example.trakify;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.chaquo.python.PyObject;
import com.chaquo.python.android.AndroidPlatform;
import com.chaquo.python.Python;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private MusicPlayerManager player;
    private MusicPlayerManager.PlaybackListener activityListener;

    private LinearLayout miniPlayer;
    private TextView txtSongTitle;
    private ImageButton btnPlayPause;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        MusicPlayerManager.getInstance().init(getApplicationContext());
        setContentView(R.layout.activity_main);


        // inicjalizacja Pythona (jeśli używasz Chaquopy)
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        // UI
        miniPlayer = findViewById(R.id.mini_player);
        txtSongTitle = findViewById(R.id.txtSongTitle);
        btnPlayPause = findViewById(R.id.btnPlayPause);

        // Pobierz singleton playera
        player = MusicPlayerManager.getInstance();

        // przygotuj listener aktywności (wykona się, gdy player powiadomi)
        activityListener = new MusicPlayerManager.PlaybackListener() {
            @Override
            public void onPrepared() {
                Log.d(TAG, "activityListener.onPrepared()");
                runOnUiThread(() -> {
                    showMiniPlayer(player.getCurrentTitle());
                    updateMiniPlayPauseIcon();
                });
            }

            @Override
            public void onPlaybackStateChanged() {
                Log.d(TAG, "activityListener.onPlaybackStateChanged()");
                runOnUiThread(() -> {
                    updateMiniPlayPauseIcon();
                    // opcjonalnie: showMiniPlayer(player.getCurrentTitle());
                });
            }
        };

        // mini-panel: kliknięcie mini przycisku Play/Pause
        btnPlayPause.setOnClickListener(v -> {
            try {
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.resume();
                }
                // natychmiastowa lokalna aktualizacja (listener też zaktualizuje)
                updateMiniPlayPauseIcon();
            } catch (Exception e) {
                Log.e(TAG, "btnPlayPause click error", e);
                Toast.makeText(this, "Błąd odtwarzacza: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // kliknięcie w mini-panel -> otwórz pełny PlayerFragment
        miniPlayer.setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new PlayerFragment())
                    .addToBackStack(null)
                    .commit();
        });

        // na start ustaw domyślny fragment
        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .commit();

        bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (id == R.id.nav_search) {
                selectedFragment = new SearchFragment();
            } else if (id == R.id.nav_library) {
                selectedFragment = new LibraryFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // rejestruj listener, żeby aktywność reagowała na zmiany w playerze
        if (player != null && activityListener != null) {
            player.registerListener(activityListener);
            Log.d(TAG, "Registered activityListener");
        }
        // odśwież UI mini-playera (np. po powrocie do aktywności)
        updateMiniPlayPauseIcon();
        String title = (player != null) ? player.getCurrentTitle() : null;
        if (title != null) showMiniPlayer(title);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // wyrejestruj listener żeby nie trzymać referencji poza lifecycle
        if (player != null && activityListener != null) {
            player.unregisterListener(activityListener);
            Log.d(TAG, "Unregistered activityListener");
        }
    }

    private void updateMiniPlayPauseIcon() {
        if (btnPlayPause == null) return;
        try {
            if (player != null && player.isPlaying()) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            }
        } catch (Exception e) {
            Log.e(TAG, "updateMiniPlayPauseIcon error", e);
        }
    }

    public void showMiniPlayer(String title) {
        if (miniPlayer == null || txtSongTitle == null) return;
        miniPlayer.setVisibility(View.VISIBLE);
        txtSongTitle.setText(title != null ? title : "Brak utworu");
        updateMiniPlayPauseIcon();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.stop();  // zatrzymuje muzykę
        }
    }
}
