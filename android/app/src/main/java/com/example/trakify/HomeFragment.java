package com.example.trakify;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
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

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    private RecyclerView rvNewReleases;
    private RecyclerView rvTopTracks;
    private ProgressBar progressBar;

    private AlbumAdapter albumAdapter;
    private TrackAdapter trackAdapter;
    private String kraj = "GLOBAL";
    private Button btnPL;
    private Button btnUS;
    private Button btnGL;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Python py = Python.getInstance();
        PyObject module = py.getModule("api_spotify");
        rvNewReleases = view.findViewById(R.id.rvNewReleases);
        rvTopTracks = view.findViewById(R.id.rvTopTracks);
        progressBar = view.findViewById(R.id.progressBar);
        btnPL = view.findViewById(R.id.btnPL);
        btnPL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kraj = "PL";
                load_top(module);
                mainHandler.post(() -> updateButtonColors(btnPL));


            }
        });
        btnUS = view.findViewById(R.id.btnUS);
        btnUS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kraj = "US";
                load_top(module);
                mainHandler.post(() -> updateButtonColors(btnUS));

            }
        });
        btnGL = view.findViewById(R.id.btnGL);
        btnGL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                kraj = "GLOBAL";
                mainHandler.post(() -> updateButtonColors(btnGL));
                load_top(module);
            }
        });


        // Setup horizontal RecyclerView for albums
        rvNewReleases.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false)
        );

        // Setup vertical RecyclerView for tracks
        rvTopTracks.setLayoutManager(new LinearLayoutManager(getContext()));

        // Setup adapters with click listeners
        albumAdapter = new AlbumAdapter(new ArrayList<>(), album -> {
            // When album is clicked, show tracks
            loadAlbumTracks(album.id, album.name,album.imageUrl);
        });

        trackAdapter = new TrackAdapter(new ArrayList<>(), track -> {
            // When track is clicked, download and play
            downloadAndPlay(track);

        });
        rvNewReleases.setAdapter(albumAdapter);
        rvTopTracks.setAdapter(trackAdapter);

        // Load data
        loadSpotifyData();
    }
    private void setButtonBackground(Button button, int color) {
        if (button.getBackground() != null) {
            Drawable drawable = button.getBackground().mutate();
            drawable.setTint(color);
            button.setBackground(drawable);
        }
    }
    private void updateButtonColors(Button selected) {
        Button[] buttons = {btnPL, btnUS, btnGL};
        for (Button b : buttons) {
            if (b == selected) {
                b.setBackgroundResource(R.drawable.spotify_button);
            } else {
                b.setBackgroundResource(R.drawable.spotify_button_bg);
            }
        }
    }


    // Przykład użycia:

    private void load_top(PyObject module){
        PyObject topTracksResult = module.callAttr("get_top_tracks",kraj, 40);
        String topTracksJson = topTracksResult.toString();
        try {
            List<Track> tracks = parseTracks(topTracksJson);
            mainHandler.post(() -> {
                trackAdapter.setItems(tracks);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error parsing tracks", e);
        }
    }

    private void loadSpotifyData() {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject module = py.getModule("api_spotify");

                // Get new releases
                load_top(module);
                PyObject newReleasesResult = module.callAttr("get_new_releases", 20);
                String newReleasesJson = newReleasesResult.toString();

                // Get top tracks
//
//                PyObject topTracksResult = module.callAttr("get_top_tracks",kraj, 40);
//                String topTracksJson = topTracksResult.toString();
//                Log.d(TAG, "TOP TRACKS JSON: " + topTracksJson);
                load_top(module);
                // Parse and update UI
                List<Album> albums = parseAlbums(newReleasesJson);
//                List<Track> tracks = parseTracks(topTracksJson);

                mainHandler.post(() -> {
                    albumAdapter.setItems(albums);
//                    trackAdapter.setItems(tracks);
                    progressBar.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading Spotify data", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Error loading data: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void loadAlbumTracks(String albumId, String albumName, String imgUrl) {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject module = py.getModule("api_spotify");

                PyObject result = module.callAttr("get_album_tracks", albumId);
                String json = result.toString();

                List<Track> tracks = parseAlbumTracks(json, albumName, imgUrl);

                mainHandler.post(() -> {
                    trackAdapter.setItems(tracks);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Playing album: " + albumName, Toast.LENGTH_SHORT).show();

                    if (tracks.isEmpty()) {
                        Toast.makeText(getContext(), "No songs found in album", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 🎶 Ustaw cover albumu dla całej kolejki
                    MusicPlayerManager manager = MusicPlayerManager.getInstance();
                    manager.setCurrentAlbumImageUrl(imgUrl);

                    // 🧩 Zbuduj kolejkę
                    // 🔥 Zbuduj kolejkę — ale jeszcze bez lokalnych ścieżek
                    List<MusicPlayerManager.QueueItem> queue = new ArrayList<>();
                    for (Track t : tracks) {
                        queue.add(new MusicPlayerManager.QueueItem(null, t.name, t.artist, imgUrl));
                    }

// Ustaw kolejkę z albumu
                    manager.setQueue(queue);

// Pobierz pierwszy utwór i zaktualizuj dane
                    Track firstTrack = tracks.get(0);

// 🔽 Pobieramy pierwszy utwór, a gdy się ściągnie, podmieniamy path w kolejce
                    new Thread(() -> {
                        try {
                            PyObject mainModule = py.getModule("main");
                            File dir = requireActivity().getExternalFilesDir("Music");
                            File outFile = new File(dir,
                                    (firstTrack.artist + "_" + firstTrack.name).replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".mp3");
                            String outputPath = outFile.getAbsolutePath();

                            PyObject res = mainModule.callAttr("play_song",
                                    firstTrack.artist + " " + firstTrack.name, outputPath);
                            String path = (res != null) ? result.toString() : null;

                            if (path != null && !path.isEmpty()) {
                                // Ustaw ścieżkę w kolejce
                                queue.get(0).path = path;
                                manager.setQueue(queue); // aktualizacja

                                mainHandler.post(() -> {
                                    MusicPlayerManager.getInstance().play(path, firstTrack.name, imgUrl);
                                    if (getActivity() instanceof MainActivity) {
                                        ((MainActivity) getActivity()).showMiniPlayer(firstTrack.name);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error downloading first track", e);
                        }
                    }).start();


                    // Pokaż mini player jeśli go ukryty
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showMiniPlayer(firstTrack.name);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading album tracks", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }


    private void downloadAndPlay(Track track) {
        Toast.makeText(getContext(), "Downloading: " + track.name, Toast.LENGTH_SHORT).show();

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

                String query = track.artist + " " + track.name;
                File outFile = new File(dir,
                        (track.artist + "_" + track.name).replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".mp3");
                String outputPath = outFile.getAbsolutePath();

                PyObject result = mainModule.callAttr("play_song", query, outputPath);
                String path = (result != null) ? result.toString() : null;

                if (path != null && !path.isEmpty()) {
                    mainHandler.post(() -> {
                        Toast.makeText(getContext(), "Playing: " + track.name, Toast.LENGTH_SHORT).show();

                        // Play the track
                        MusicPlayerManager.getInstance().play(path, track.name,track.imageUrl);

                        // Show mini player
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

    private List<Album> parseAlbums(String json) throws Exception {
        List<Album> albums = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray albumsArray = root.getJSONArray("albums");

        for (int i = 0; i < albumsArray.length(); i++) {
            JSONObject obj = albumsArray.getJSONObject(i);
            Album album = new Album();
            album.id = obj.getString("id");
            album.name = obj.getString("name");
            album.artist = obj.getString("artist");
            album.imageUrl = obj.optString("image", "");
            albums.add(album);
        }

        return albums;
    }

    private List<Track> parseTracks(String json) throws Exception {
        List<Track> tracks = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray tracksArray = root.getJSONArray("tracks");

        for (int i = 0; i < tracksArray.length(); i++) {
            JSONObject obj = tracksArray.getJSONObject(i);
            Track track = new Track();
            track.id = obj.getString("id");
            track.name = obj.getString("name");
            track.artist = obj.getString("artist");
            track.album = obj.optString("album", "");
            track.imageUrl = obj.optString("image", "");
            track.durationMs = obj.optInt("duration_ms", 0);
            tracks.add(track);
        }

        return tracks;
    }

    private List<Track> parseAlbumTracks(String json, String albumName,String ImgUrl) throws Exception {
        List<Track> tracks = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray tracksArray = root.getJSONArray("tracks");

        for (int i = 0; i < tracksArray.length(); i++) {
            JSONObject obj = tracksArray.getJSONObject(i);
            Track track = new Track();
            track.id = obj.getString("id");
            track.name = obj.getString("name");
            track.artist = obj.getString("artist");
            track.album = albumName;
            track.imageUrl = ImgUrl;
            track.durationMs = obj.optInt("duration_ms", 0);
            tracks.add(track);
        }

        return tracks;
    }

    // Data classes
    public static class Album {
        public String id;
        public String name;
        public String artist;
        public String imageUrl;
    }

    public static class Track {
        public String id;
        public String name;
        public String artist;
        public String album;
        public String imageUrl;
        public int durationMs;
    }
}