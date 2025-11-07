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

import java.util.ArrayList;
import java.util.List;

public class ArtistDetailFragment extends Fragment {

    private static final String TAG = "ArtistDetailFragment";
    private static final String ARG_ARTIST_ID = "artist_id";
    private static final String ARG_ARTIST_NAME = "artist_name";

    private String artistId;
    private String artistName;

    private TextView tvArtistName;
    private ImageButton btnBack;
    private RecyclerView rvAlbums;
    private ProgressBar progressBar;

    private AlbumAdapter albumAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static ArtistDetailFragment newInstance(String artistId, String artistName) {
        ArtistDetailFragment fragment = new ArtistDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ARTIST_ID, artistId);
        args.putString(ARG_ARTIST_NAME, artistName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            artistId = getArguments().getString(ARG_ARTIST_ID);
            artistName = getArguments().getString(ARG_ARTIST_NAME);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_artist_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvArtistName = view.findViewById(R.id.tvArtistDetailName);
        btnBack = view.findViewById(R.id.btnBack);
        rvAlbums = view.findViewById(R.id.rvArtistAlbums);
        progressBar = view.findViewById(R.id.progressBar);

        tvArtistName.setText(artistName);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        rvAlbums.setLayoutManager(new LinearLayoutManager(getContext()));

        albumAdapter = new AlbumAdapter(new ArrayList<>(), album -> {
            // Navigate to album detail
            AlbumDetailFragment fragment = AlbumDetailFragment.newInstance(
                    album.id, album.name, artistName,album.imageUrl
            );
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        });

        rvAlbums.setAdapter(albumAdapter);

        loadArtistAlbums();
    }

    private void loadArtistAlbums() {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                Python py = Python.getInstance();
                PyObject module = py.getModule("api_spotify");

                PyObject result = module.callAttr("get_artist_albums", artistId);
                String json = result.toString();

                List<HomeFragment.Album> albums = parseAlbums(json);

                mainHandler.post(() -> {
                    albumAdapter.setItems(albums);
                    progressBar.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading artist albums", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private List<HomeFragment.Album> parseAlbums(String json) throws Exception {
        List<HomeFragment.Album> albums = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray albumsArray = root.getJSONArray("albums");

        for (int i = 0; i < albumsArray.length(); i++) {
            JSONObject obj = albumsArray.getJSONObject(i);
            HomeFragment.Album album = new HomeFragment.Album();
            album.id = obj.getString("id");
            album.name = obj.getString("name");
            album.artist = artistName;
            album.imageUrl = obj.optString("image", "");
            albums.add(album);
        }

        return albums;
    }
}