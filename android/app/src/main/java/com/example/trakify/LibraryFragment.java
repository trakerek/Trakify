package com.example.trakify;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LibraryFragment extends Fragment {

    private RecyclerView rvSongs;
    private TextView tvEmpty;
    private Button btnClearAll;
    private Button btnSort;
    private EditText etSearch;

    private SongAdapter adapter;
    private File musicDir;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private List<File> allSongs = new ArrayList<>(); // pełna lista (niefiltrowana)

    private static final int SEARCH_DELAY_MS = 300;

    private static final String PREFS = "trakify_prefs";
    private static final String PREF_SORT = "library_sort";

    // sort modes
    private static final int SORT_NAME_ASC = 0;
    private static final int SORT_NAME_DESC = 1;
    private static final int SORT_DATE_NEWEST = 2;
    private static final int SORT_DATE_OLDEST = 3;
    private static final int SORT_SIZE_LARGEST = 4;
    private static final int SORT_SIZE_SMALLEST = 5;

    private int currentSortMode = SORT_DATE_NEWEST; // domyślnie

    public LibraryFragment() { /* required empty */ }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container, false);

        rvSongs = view.findViewById(R.id.rvSongs);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        btnClearAll = view.findViewById(R.id.btnClearAll);
        btnSort = view.findViewById(R.id.btnSort);      // dodaj do layoutu
        etSearch = view.findViewById(R.id.etSearch);    // dodaj EditText do layoutu fragment_library.xml

        rvSongs.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SongAdapter(getContext(), new ArrayList<>(), new SongAdapter.SongActionListener() {
            @Override
            public void onPlay(File file) {
                playFile(file);
            }

            @Override
            public void onDelete(File file, int position) {
                confirmDelete(file, position);
            }
        });
        rvSongs.setAdapter(adapter);

        btnClearAll.setOnClickListener(v -> {
            if (adapter.getItemCount() == 0) {
                Toast.makeText(getContext(), "Brak plików do usunięcia", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Wyczyść wszystkie")
                    .setMessage("Czy na pewno usunąć wszystkie pobrane piosenki?")
                    .setPositiveButton("Tak", (d, w) -> clearAllFiles())
                    .setNegativeButton("Anuluj", null)
                    .show();
        });

        btnSort.setOnClickListener(v -> showSortDialog());

        // wczytaj zapisany tryb sortowania
        currentSortMode = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PREF_SORT, SORT_DATE_NEWEST);

        // TextWatcher + debounce dla etSearch
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                    final String q = s.toString();
                    searchRunnable = () -> filterAndShow(q);
                    searchHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        loadSongsAsync();
        return view;
    }

    private void showSortDialog() {
        final String[] options = new String[]{
                "Nazwa (A → Z)",
                "Nazwa (Z → A)",
                "Data (najnowsze)",
                "Data (najstarsze)",
                "Rozmiar (największe)",
                "Rozmiar (najmniejsze)"
        };

        int checked = currentSortMode;

        new AlertDialog.Builder(requireContext())
                .setTitle("Sortuj pobrane")
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    currentSortMode = which;
                    // zapisz wybór
                    requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putInt(PREF_SORT, currentSortMode).apply();
                    // posortuj aktualną (filtrowaną) listę i odśwież UI
                    sortCurrentAdapterList();
                    dialog.dismiss();
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private void loadSongsAsync() {
        new Thread(() -> {
            Context ctx = requireContext();
            musicDir = ctx.getExternalFilesDir("Music");
            List<File> songs = new ArrayList<>();
            if (musicDir != null && musicDir.exists() && musicDir.isDirectory()) {
                File[] files = musicDir.listFiles();
                if (files != null) {
                    Collections.addAll(songs, files);
                }
            }

            // zapisz pełną listę
            allSongs.clear();
            allSongs.addAll(songs);

            // posortuj pełną listę wg ustawienia
            sortListInPlace(allSongs, currentSortMode);

            // pokaż (bez filtra -> całość)
            final List<File> toShow = new ArrayList<>(allSongs);

            mainHandler.post(() -> {
                adapter.setItems(toShow);
                updateEmptyView();
            });
        }).start();
    }

    private void filterAndShow(String query) {
        try {
            String q = query == null ? "" : query.trim().toLowerCase();
            List<File> filtered;
            if (q.isEmpty()) {
                filtered = new ArrayList<>(allSongs);
            } else {
                filtered = new ArrayList<>();
                for (File f : allSongs) {
                    String name = f.getName().toLowerCase();
                    // usuń rozszerzenie przy porównaniu (opcjonalnie)
                    String nameNoExt = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                    if (name.contains(q) || nameNoExt.contains(q)) {
                        filtered.add(f);
                    }
                }
            }

            // posortuj wynik
            sortListInPlace(filtered, currentSortMode);

            final List<File> show = filtered;
            mainHandler.post(() -> {
                adapter.setItems(show);
                updateEmptyView();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void sortCurrentAdapterList() {
        List<File> current = adapter.getItems(); // upewnij się, że adapter ma getItems()
        if (current == null) {
            loadSongsAsync();
            return;
        }
        sortListInPlace(current, currentSortMode);
        adapter.setItems(current);
        updateEmptyView();
    }

    /**
     * Sortuje listę plików inplace według trybu sortowania.
     */
    private void sortListInPlace(List<File> list, int sortMode) {
        if (list == null || list.isEmpty()) return;

        switch (sortMode) {
            case SORT_NAME_ASC:
                Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                break;
            case SORT_NAME_DESC:
                Collections.sort(list, (a, b) -> b.getName().compareToIgnoreCase(a.getName()));
                break;
            case SORT_DATE_NEWEST:
                Collections.sort(list, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                break;
            case SORT_DATE_OLDEST:
                Collections.sort(list, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                break;
            case SORT_SIZE_LARGEST:
                Collections.sort(list, (a, b) -> Long.compare(b.length(), a.length()));
                break;
            case SORT_SIZE_SMALLEST:
                Collections.sort(list, (a, b) -> Long.compare(a.length(), b.length()));
                break;
            default:
                Collections.sort(list, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                break;
        }
    }

    private void updateEmptyView() {
        if (adapter.getItemCount() == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvSongs.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvSongs.setVisibility(View.VISIBLE);
        }
    }

    // Add this method to your LibraryFragment.java:

    private void playFile(File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(getContext(), "Plik nie istnieje", Toast.LENGTH_SHORT).show();
            return;
        }

        MusicPlayerManager player = MusicPlayerManager.getInstance();
        String path = file.getAbsolutePath();
        String title = file.getName();

        // Clear queue and add all songs from current filtered list
        player.clearQueue();

        List<File> currentSongs = adapter.getItems();
        int clickedIndex = -1;

        // Add all songs to queue
        for (int i = 0; i < currentSongs.size(); i++) {
            File f = currentSongs.get(i);
            if (f.equals(file)) {
                clickedIndex = i;
            }
            // Add to queue with path (already downloaded)
            player.addToQueue(f.getAbsolutePath(), f.getName(), null,null);
        }

        // Play the clicked song (starting from that position in queue)
        if (clickedIndex >= 0) {
            player.playFromQueue(clickedIndex);
        } else {
            // Fallback if index not found
            player.play(path, title, null);
        }

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showMiniPlayer(title);
        }

        Log.d("LibraryFragment", "Added " + currentSongs.size() + " songs to queue, starting at index " + clickedIndex);
    }

    private void confirmDelete(File file, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Usuń plik")
                .setMessage("Usunąć " + file.getName() + "?")
                .setPositiveButton("Usuń", (dialog, which) -> deleteFileAsync(file, position))
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private void deleteFileAsync(File file, int position) {
        new Thread(() -> {
            boolean ok = false;
            try {
                ok = file.delete();
            } catch (Exception e) {
                ok = false;
            }
            final boolean deleted = ok;
            mainHandler.post(() -> {
                if (deleted) {
                    adapter.removeAt(position);
                    // usuń też z allSongs
                    allSongs.remove(file);
                    Toast.makeText(getContext(), "Usunięto " + file.getName(), Toast.LENGTH_SHORT).show();
                    updateEmptyView();
                } else {
                    Toast.makeText(getContext(), "Nie udało się usunąć pliku", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void clearAllFiles() {
        new Thread(() -> {
            boolean any = false;
            if (musicDir != null && musicDir.exists() && musicDir.isDirectory()) {
                File[] files = musicDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        try {
                            if (f.delete()) any = true;
                        } catch (Exception ignored) {}
                    }
                }
            }
            final boolean cleared = any;
            mainHandler.post(() -> {
                if (cleared) {
                    adapter.setItems(new ArrayList<>());
                    allSongs.clear();
                    updateEmptyView();
                    Toast.makeText(getContext(), "Wyczyszczono wszystkie pliki", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Brak plików do usunięcia", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}