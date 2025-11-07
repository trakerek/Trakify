package com.example.trakify;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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

public class SearchFragment extends Fragment {

    private static final String TAG = "SearchFragment";

    private EditText etSearch;
    private Button btnManualDownload;
    private RecyclerView rvSearchResults;
    private ProgressBar progressBar;
    private TextView tvNoResults;

    private SearchResultAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private static final int SEARCH_DELAY_MS = 500;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etSearch = view.findViewById(R.id.etSearch);
        btnManualDownload = view.findViewById(R.id.btnManualDownload);
        rvSearchResults = view.findViewById(R.id.rvSearchResults);
        progressBar = view.findViewById(R.id.progressBar);
        tvNoResults = view.findViewById(R.id.tvNoResults);

        rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new SearchResultAdapter(new ArrayList<>(), new SearchResultAdapter.OnResultClickListener() {
            @Override
            public void onResultClick(SearchResult result) {
                handleResultClick(result);
            }

            @Override
            public void onDownloadClick(SearchResult result) {
                downloadAndPlay(result);
            }
        });

        rvSearchResults.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                String query = s.toString().trim();

                if (query.isEmpty()) {
                    adapter.setItems(new ArrayList<>());
                    tvNoResults.setVisibility(View.GONE);
                    rvSearchResults.setVisibility(View.GONE);
                    return;
                }

                searchRunnable = () -> performSearch(query);
                searchHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnManualDownload.setOnClickListener(v -> {
            String query = etSearch.getText().toString().trim();
            if (!query.isEmpty()) {
                SearchResult manual = new SearchResult();
                manual.name = query;
                manual.artist = "";
                manual.type = "manual";
                downloadAndPlay(manual);
            } else {
                Toast.makeText(getContext(), "Enter a song name", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performSearch(String query) {
        progressBar.setVisibility(View.VISIBLE);
        tvNoResults.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject module = py.getModule("api_spotify");

                PyObject result = module.callAttr("search_spotify", query, 8);
                String json = result.toString();

                List<SearchResult> results = parseSearchResults(json);

                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (results.isEmpty()) {
                        tvNoResults.setVisibility(View.VISIBLE);
                        rvSearchResults.setVisibility(View.GONE);
                    } else {
                        tvNoResults.setVisibility(View.GONE);
                        rvSearchResults.setVisibility(View.VISIBLE);
                        adapter.setItems(results);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Search error", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Search error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void handleResultClick(SearchResult result) {
        switch (result.type) {
            case "track":
                // Download and play directly
                downloadAndPlay(result);
                break;
            case "album":
                // Navigate to album view
                navigateToAlbum(result);
                break;
            case "artist":
                // Navigate to artist view
                navigateToArtist(result);
                break;
        }
    }

    private void navigateToAlbum(SearchResult album) {
        AlbumDetailFragment fragment = AlbumDetailFragment.newInstance(
                album.id, album.name, album.artist, album.imageUrl
        );
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void navigateToArtist(SearchResult artist) {
        ArtistDetailFragment fragment = ArtistDetailFragment.newInstance(
                artist.id, artist.name
        );
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void downloadAndPlay(SearchResult result) {
        String query = result.type.equals("manual")
                ? result.name
                : result.artist + " " + result.name;

        Toast.makeText(getContext(), "Downloading: " + result.name, Toast.LENGTH_SHORT).show();

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

                String filename = (result.artist + "_" + result.name)
                        .replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".mp3";
                File outFile = new File(dir, filename);
                String outputPath = outFile.getAbsolutePath();

                Log.d("dupa", "Downloading with query: " + query);
                PyObject pyResult = mainModule.callAttr("play_song", query, outputPath);
                String path = (pyResult != null) ? pyResult.toString() : null;
                Log.d("dupa","path: "+ path);
                if (path != null && !path.isEmpty()) {
                    mainHandler.post(() -> {
                        Toast.makeText(getContext(), "Playing: " + result.name, Toast.LENGTH_SHORT).show();

                        MusicPlayerManager.getInstance().play(path, result.name,result.imageUrl);

                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showMiniPlayer(result.name);
                        }
                    });
                } else {
                    mainHandler.post(() ->
                            Toast.makeText(getContext(), "Download failed", Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                Log.e(TAG, "Download error", e);
                mainHandler.post(() ->
                        Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private List<SearchResult> parseSearchResults(String json) throws Exception {
        List<SearchResult> results = new ArrayList<>();
        JSONObject root = new JSONObject(json);

        // Parse ARTISTS first (priority)
        if (root.has("artists")) {
            JSONObject artists = root.getJSONObject("artists");
            if (artists.has("items")) {
                JSONArray items = artists.getJSONArray("items");

                for (int i = 0; i < Math.min(2, items.length()); i++) {
                    JSONObject artist = items.getJSONObject(i);

                    SearchResult result = new SearchResult();
                    result.type = "artist";
                    result.id = artist.getString("id");
                    result.name = artist.getString("name");
                    result.artist = "";

                    if (artist.has("images")) {
                        JSONArray images = artist.getJSONArray("images");
                        if (images.length() > 0) {
                            result.imageUrl = images.getJSONObject(0).getString("url");
                        }
                    }

                    results.add(result);
                }
            }
        }

        // Parse ALBUMS second
        if (root.has("albums")) {
            JSONObject albums = root.getJSONObject("albums");
            if (albums.has("items")) {
                JSONArray items = albums.getJSONArray("items");

                for (int i = 0; i < Math.min(3, items.length()); i++) {
                    JSONObject album = items.getJSONObject(i);

                    SearchResult result = new SearchResult();
                    result.type = "album";
                    result.id = album.getString("id");
                    result.name = album.getString("name");

                    if (album.has("artists")) {
                        JSONArray artists = album.getJSONArray("artists");
                        if (artists.length() > 0) {
                            result.artist = artists.getJSONObject(0).getString("name");
                        }
                    }

                    if (album.has("images")) {
                        JSONArray images = album.getJSONArray("images");
                        if (images.length() > 0) {
                            result.imageUrl = images.getJSONObject(0).getString("url");
                        }
                    }

                    results.add(result);
                }
            }
        }

        // Parse TRACKS last
        if (root.has("tracks")) {
            JSONObject tracks = root.getJSONObject("tracks");
            if (tracks.has("items")) {
                JSONArray items = tracks.getJSONArray("items");

                for (int i = 0; i < items.length(); i++) {
                    JSONObject track = items.getJSONObject(i);

                    SearchResult result = new SearchResult();
                    result.type = "track";
                    result.name = track.getString("name");

                    if (track.has("artists")) {
                        JSONArray artists = track.getJSONArray("artists");
                        if (artists.length() > 0) {
                            result.artist = artists.getJSONObject(0).getString("name");
                        }
                    }

                    if (track.has("album")) {
                        JSONObject album = track.getJSONObject("album");
                        result.album = album.optString("name", "");

                        if (album.has("images")) {
                            JSONArray images = album.getJSONArray("images");
                            if (images.length() > 0) {
                                result.imageUrl = images.getJSONObject(0).getString("url");
                            }
                        }
                    }

                    result.durationMs = track.optInt("duration_ms", 0);
                    result.id = track.optString("id", "");

                    results.add(result);
                }
            }
        }

        return results;
    }

    public static class SearchResult {
        public String id;
        public String type;
        public String name;
        public String artist;
        public String album;
        public String imageUrl;
        public int durationMs;
    }
}