package com.example.trakify;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AlbumDetailFragment extends Fragment {

    private static final String TAG = "AlbumDetailFragment";
    private static final String ARG_ALBUM_ID = "album_id";
    private static final String ARG_ALBUM_NAME = "album_name";
    private static final String ARG_ARTIST_NAME = "artist_name";
    private static final String ARG_ALBUM_URL = "album_url";

    private String albumId;
    private String albumName;
    private String artistName;
    private String albumimageurl;
    private TextView tvAlbumName;
    private TextView tvArtistName;
    private ImageButton btnBack;
    private RecyclerView rvTracks;
    private ProgressBar progressBar;
    List<AlbumTrack> tracks = null;

    private AlbumTrackAdapter trackAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static AlbumDetailFragment newInstance(String albumId, String albumName, String artistName,String AlbumURl) {
        AlbumDetailFragment fragment = new AlbumDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ALBUM_ID, albumId);
        args.putString(ARG_ALBUM_NAME, albumName);
        args.putString(ARG_ARTIST_NAME, artistName);
        args.putString(ARG_ALBUM_URL, AlbumURl);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            albumId = getArguments().getString(ARG_ALBUM_ID);
            albumName = getArguments().getString(ARG_ALBUM_NAME);
            artistName = getArguments().getString(ARG_ARTIST_NAME);
            albumimageurl = getArguments().getString(ARG_ALBUM_URL);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_album_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvAlbumName = view.findViewById(R.id.tvAlbumDetailName);
        tvArtistName = view.findViewById(R.id.tvAlbumDetailArtist);
        btnBack = view.findViewById(R.id.btnBack);
        rvTracks = view.findViewById(R.id.rvAlbumTracks);
        progressBar = view.findViewById(R.id.progressBar);

        tvAlbumName.setText(albumName);
        tvArtistName.setText(artistName);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        rvTracks.setLayoutManager(new LinearLayoutManager(getContext()));

        trackAdapter = new AlbumTrackAdapter(new ArrayList<>(), track -> {
            downloadAndPlay(track);
        });

        rvTracks.setAdapter(trackAdapter);

        loadAlbumTracks();
    }

    private void loadAlbumTracks() {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject module = py.getModule("api_spotify");

                PyObject result = module.callAttr("get_album_tracks", albumId);
                String json = result.toString();

                Log.d(TAG, "Album tracks JSON: " + json);

                tracks = parseTracks(json);

                mainHandler.post(() -> {
                    trackAdapter.setItems(tracks);
                    progressBar.setVisibility(View.GONE);

                    // Add all tracks to queue when album loads
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading album tracks", e);
                e.printStackTrace();
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void addTracksToQueue(List<AlbumTrack> tracks,String url) {
        if (tracks == null || tracks.isEmpty()) {
            Log.w(TAG, "Brak utworów do dodania do kolejki");
            return;
        }

        MusicPlayerManager player = MusicPlayerManager.getInstance();
        player.clearQueue();

        File dir = requireActivity().getExternalFilesDir("Music");
        if (dir == null) {
            Toast.makeText(getContext(), "Brak dostępu do pamięci", Toast.LENGTH_SHORT).show();
            return;
        }

        // Dodaj wszystko do kolejki od razu (bez pobierania)
        for (AlbumTrack track : tracks) {
            String filename = (track.artist + "_" + track.name)
                    .replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".mp3";
            File localFile = new File(dir, filename);

            String path = localFile.exists() ? localFile.getAbsolutePath() : null;
            player.addToQueue(path, track.name, track.artist,url);
        }

        Log.d(TAG, "Dodano " + tracks.size() + " utworów do kolejki");

        // 🔥 Uruchom asynchroniczne pobieranie w tle
        new Thread(() -> preloadTracks(tracks, dir)).start();
    }

    private void preloadTracks(List<AlbumTrack> tracks, File dir) {
        Python py = Python.getInstance();
        PyObject mainModule = py.getModule("main");

        for (AlbumTrack track : tracks) {
            try {
                String filename = (track.artist + "_" + track.name)
                        .replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".mp3";
                File outFile = new File(dir, filename);

                if (outFile.exists()) {
                    Log.d(TAG, "Plik już istnieje: " + filename);
                    continue; // pomiń, jeśli już pobrano
                }

                String query = track.artist + " " + track.name;
                String outputPath = outFile.getAbsolutePath();

                Log.d(TAG, "Pobieram w tle: " + track.name);

                PyObject result = mainModule.callAttr("play_song", query, outputPath);
                String path = (result != null) ? result.toString() : null;

                if (path != null && !path.isEmpty()) {
                    Log.d(TAG, "Pobrano: " + track.name);
                } else {
                    Log.w(TAG, "Nie udało się pobrać: " + track.name);
                }

                // mała pauza, by nie przeciążać sieci/CPU
                Thread.sleep(1500);

            } catch (Exception e) {
                Log.e(TAG, "Błąd podczas pobierania w tle: " + track.name, e);
            }
        }

        Log.d(TAG, "Preload zakończony.");
    }



    private void downloadAndPlay(AlbumTrack track) {
        String query = artistName + " " + track.name;
        Toast.makeText(getContext(), "Downloading: " + track.name, Toast.LENGTH_SHORT).show();
        addTracksToQueue(tracks,albumimageurl);

        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject mainModule = py.getModule("main");

                File dir = requireActivity().getExternalFilesDir("Music");
                if (dir == null) {
                    mainHandler.post(() ->
                            Toast.makeText(getContext(), "Storage error", Toast.LENGTH_SHORT).show());
                    return;
                }
                Log.d("dupa", "cos dziala");
                String filename = (artistName + "_" + track.name)
                        .replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".mp3";
                File outFile = new File(dir, filename);
                String outputPath = outFile.getAbsolutePath();

                PyObject result = mainModule.callAttr("play_song", query, outputPath);
                String path = (result != null) ? result.toString() : null;

                if (path != null && !path.isEmpty()) {
                    mainHandler.post(() -> {
                        Toast.makeText(getContext(), "Playing: " + track.name, Toast.LENGTH_SHORT).show();
                        Log.d("dupa", "dziala");
                        MusicPlayerManager player = MusicPlayerManager.getInstance();
                        player.setCurrentImageUrl(albumimageurl);
                        player.setCurrentAlbumImageUrl(albumimageurl);
                        player.updateTrackPath(track.name, path, albumimageurl); // dodaj taką metodę w MusicPlayerManager


                        player.play(path, track.name,albumimageurl);
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showMiniPlayer(track.name);
                        }
                    });
                } else {
                    mainHandler.post(() ->
                            Toast.makeText(getContext(), "Download failed", Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                Log.e(TAG, "Error downloading track", e);
                mainHandler.post(() ->
                        Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private List<AlbumTrack> parseTracks(String json) throws Exception {
        List<AlbumTrack> tracks = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray tracksArray = root.getJSONArray("tracks");

        for (int i = 0; i < tracksArray.length(); i++) {
            JSONObject obj = tracksArray.getJSONObject(i);
            AlbumTrack track = new AlbumTrack();
            track.id = obj.getString("id");
            track.name = obj.getString("name");
            track.artist = obj.getString("artist");
            track.durationMs = obj.optInt("duration_ms", 0);
            track.trackNumber = obj.optInt("track_number", 0);
            tracks.add(track);
        }

        return tracks;
    }

    // Simple track class for album tracks
    public static class AlbumTrack {
        public String id;
        public String name;
        public String artist;
        public int durationMs;
        public int trackNumber;
    }
}