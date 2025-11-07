package com.example.trakify;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.VH> {

    public List<File> getItems() {
        return items;
    }

    public interface SongActionListener {
        void onPlay(File file);
        void onDelete(File file, int position);
    }

    private final Context ctx;
    private List<File> items;
    private final SongActionListener listener;

    public SongAdapter(Context ctx, List<File> items, SongActionListener listener) {
        this.ctx = ctx;
        this.items = items;
        this.listener = listener;
    }

    public void setItems(List<File> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public void removeAt(int pos) {
        if (pos >= 0 && pos < items.size()) {
            items.remove(pos);
            notifyItemRemoved(pos);
            notifyItemRangeChanged(pos, items.size());
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_song, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        File file = items.get(position);
        String name = file.getName();
        // usuń rozszerzenie jeśli chcesz
        int idx = name.lastIndexOf('.');
        if (idx > 0) name = name.substring(0, idx);
        holder.tvName.setText(name);

        holder.btnPlay.setOnClickListener(v -> {
            if (listener != null) listener.onPlay(file);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(file, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName;
        ImageButton btnPlay;
        ImageButton btnDelete;

        public VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvSongName);
            btnPlay = itemView.findViewById(R.id.btnPlay);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
