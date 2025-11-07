
// TrackAdapter.java
package com.example.trakify;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.trakify.HomeFragment.Track;

import java.util.List;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    private List<Track> tracks;
    private final OnTrackClickListener listener;

    public interface OnTrackClickListener {
        void onTrackClick(Track track);
    }

    public TrackAdapter(List<Track> tracks, OnTrackClickListener listener) {
        this.tracks = tracks;
        this.listener = listener;
    }

    public void setItems(List<Track> newTracks) {
        this.tracks.clear();
        this.tracks = newTracks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Track track = tracks.get(position);
        holder.tvTrackName.setText(track.name);
        holder.tvArtistName.setText(track.artist);

        if (track.album != null && !track.album.isEmpty()) {
            holder.tvAlbumName.setText(track.album);
            holder.tvAlbumName.setVisibility(View.VISIBLE);
        } else {
            holder.tvAlbumName.setVisibility(View.GONE);
        }

        // Format duration
        if (track.durationMs > 0) {
            int minutes = (track.durationMs / 1000) / 60;
            int seconds = (track.durationMs / 1000) % 60;
            holder.tvDuration.setText(String.format("%d:%02d", minutes, seconds));
        } else {
            holder.tvDuration.setText("");
        }

        // TODO: Load image with Glide if you add the library
//        holder.ivTrack.setImageResource(R.drawable.ic_music_placeholder);
        if (!track.imageUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(track.imageUrl)
                    .placeholder(R.drawable.ic_album_placeholder)
                    .into(holder.ivTrack);
        } else {
            holder.ivTrack.setImageResource(R.drawable.ic_album_placeholder);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTrackClick(track);
        });
    }

    @Override
    public int getItemCount() {
        return tracks != null ? tracks.size() : 0;
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        ImageView ivTrack;
        TextView tvTrackName;
        TextView tvArtistName;
        TextView tvAlbumName;
        TextView tvDuration;

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            ivTrack = itemView.findViewById(R.id.ivTrack);
            tvTrackName = itemView.findViewById(R.id.tvTrackName);
            tvArtistName = itemView.findViewById(R.id.tvArtistName);
            tvAlbumName = itemView.findViewById(R.id.tvAlbumName);
            tvDuration = itemView.findViewById(R.id.tvDuration);
        }
    }
}