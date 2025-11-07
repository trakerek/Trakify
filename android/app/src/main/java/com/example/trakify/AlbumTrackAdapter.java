package com.example.trakify;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.trakify.AlbumDetailFragment.AlbumTrack;

import java.util.List;

public class AlbumTrackAdapter extends RecyclerView.Adapter<AlbumTrackAdapter.TrackViewHolder> {

    private List<AlbumTrack> tracks;
    private final OnTrackClickListener listener;

    public interface OnTrackClickListener {
        void onTrackClick(AlbumTrack track);
    }

    public AlbumTrackAdapter(List<AlbumTrack> tracks, OnTrackClickListener listener) {
        this.tracks = tracks;
        this.listener = listener;
    }

    public void setItems(List<AlbumTrack> newTracks) {
        this.tracks = newTracks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_album_track, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        AlbumTrack track = tracks.get(position);

        holder.tvTrackNumber.setText(String.valueOf(track.trackNumber));
        holder.tvTrackName.setText(track.name);
        holder.tvArtist.setText(track.artist);

        // Format duration
        if (track.durationMs > 0) {
            int minutes = (track.durationMs / 1000) / 60;
            int seconds = (track.durationMs / 1000) % 60;
            holder.tvDuration.setText(String.format("%d:%02d", minutes, seconds));
        } else {
            holder.tvDuration.setText("");
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTrackClick(track);
        });

        holder.btnDownload.setOnClickListener(v -> {
            if (listener != null) listener.onTrackClick(track);
        });
    }

    @Override
    public int getItemCount() {
        return tracks != null ? tracks.size() : 0;
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        TextView tvTrackNumber;
        TextView tvTrackName;
        TextView tvArtist;
        TextView tvDuration;
        ImageButton btnDownload;

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTrackNumber = itemView.findViewById(R.id.tvTrackNumber);
            tvTrackName = itemView.findViewById(R.id.tvAlbumTrackName);
            tvArtist = itemView.findViewById(R.id.tvAlbumTrackArtist);
            tvDuration = itemView.findViewById(R.id.tvAlbumTrackDuration);
            btnDownload = itemView.findViewById(R.id.btnAlbumTrackDownload);
        }
    }
}