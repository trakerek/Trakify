package com.example.trakify;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.trakify.SearchFragment.SearchResult;

import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SearchViewHolder> {

    private List<SearchResult> results;
    private final OnResultClickListener listener;

    public interface OnResultClickListener {
        void onResultClick(SearchResult result);
        void onDownloadClick(SearchResult result);
    }

    public SearchResultAdapter(List<SearchResult> results, OnResultClickListener listener) {
        this.results = results;
        this.listener = listener;
    }

    public void setItems(List<SearchResult> newResults) {
        this.results = newResults;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SearchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new SearchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchViewHolder holder, int position) {
        SearchResult result = results.get(position);

        holder.tvName.setText(result.name);
        holder.tvArtist.setText(result.artist);

        // Show album info if available
        if (result.album != null && !result.album.isEmpty()) {
            holder.tvAlbum.setText(result.album);
            holder.tvAlbum.setVisibility(View.VISIBLE);
        } else {
            holder.tvAlbum.setVisibility(View.GONE);
        }

        // Format duration
        if (result.durationMs > 0) {
            int minutes = (result.durationMs / 1000) / 60;
            int seconds = (result.durationMs / 1000) % 60;
            holder.tvDuration.setText(String.format("%d:%02d", minutes, seconds));
            holder.tvDuration.setVisibility(View.VISIBLE);
        } else {
            holder.tvDuration.setVisibility(View.GONE);
        }

        // Show type badge
        switch (result.type) {
            case "track":
                holder.tvType.setText("SONG");
                holder.tvType.setBackgroundResource(R.drawable.badge_song);
                break;
            case "album":
                holder.tvType.setText("ALBUM");
                holder.tvType.setBackgroundResource(R.drawable.badge_album);
                break;
            case "artist":
                holder.tvType.setText("ARTIST");
                holder.tvType.setBackgroundResource(R.drawable.badge_artist);
                break;
            default:
                holder.tvType.setVisibility(View.GONE);
                break;
        }

        // TODO: Load image with Glide if you add the library
//        holder.ivImage.setImageResource(R.drawable.ic_music_placeholder);
        if (!result.imageUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(result.imageUrl)
                    .placeholder(R.drawable.ic_album_placeholder)
                    .into(holder.ivImage);
        } else {
            holder.ivImage.setImageResource(R.drawable.ic_album_placeholder);
        }
        // Click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onResultClick(result);
        });

        // Download button
        holder.btnDownload.setOnClickListener(v -> {
            if (listener != null) listener.onResultClick(result);
        });
    }

    @Override
    public int getItemCount() {
        return results != null ? results.size() : 0;
    }

    static class SearchViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvName;
        TextView tvArtist;
        TextView tvAlbum;
        TextView tvDuration;
        TextView tvType;
        ImageButton btnDownload;

        public SearchViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivSearchImage);
            tvName = itemView.findViewById(R.id.tvSearchName);
            tvArtist = itemView.findViewById(R.id.tvSearchArtist);
            tvAlbum = itemView.findViewById(R.id.tvSearchAlbum);
            tvDuration = itemView.findViewById(R.id.tvSearchDuration);
            tvType = itemView.findViewById(R.id.tvSearchType);
            btnDownload = itemView.findViewById(R.id.btnSearchDownload);
        }
    }
}