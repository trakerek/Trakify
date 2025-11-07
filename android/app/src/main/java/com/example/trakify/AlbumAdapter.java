// AlbumAdapter.java
package com.example.trakify;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.trakify.HomeFragment.Album;

import java.util.List;

public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder> {

    private List<Album> albums;
    private final OnAlbumClickListener listener;

    public interface OnAlbumClickListener {
        void onAlbumClick(Album album);
    }

    public AlbumAdapter(List<Album> albums, OnAlbumClickListener listener) {
        this.albums = albums;
        this.listener = listener;
    }

    public void setItems(List<Album> newAlbums) {
        this.albums = newAlbums;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_album, parent, false);
        return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        Album album = albums.get(position);
        holder.tvAlbumName.setText(album.name);
        holder.tvArtist.setText(album.artist);

        // TODO: Load image with Glide or Picasso if you add those libraries
        // For now, use placeholder
        if (!album.imageUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(album.imageUrl)
                    .placeholder(R.drawable.ic_album_placeholder)
                    .into(holder.ivAlbum);
        } else {
            holder.ivAlbum.setImageResource(R.drawable.ic_album_placeholder);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAlbumClick(album);
        });
    }

    @Override
    public int getItemCount() {
        return albums != null ? albums.size() : 0;
    }

    static class AlbumViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAlbum;
        TextView tvAlbumName;
        TextView tvArtist;

        public AlbumViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAlbum = itemView.findViewById(R.id.ivAlbum);
            tvAlbumName = itemView.findViewById(R.id.tvAlbumName);
            tvArtist = itemView.findViewById(R.id.tvArtist);
        }
    }
}
