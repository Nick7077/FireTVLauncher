package com.nick.tvlauncher;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Cards in one horizontal row (Favorites, Inputs). */
final class RowAdapter extends RecyclerView.Adapter<CardHolder> {
    private final CardHolder.Listener listener;
    private final int cardWidthPx;
    private List<AppEntry> items = new ArrayList<>();

    RowAdapter(CardHolder.Listener listener, int cardWidthPx) {
        this.listener = listener;
        this.cardWidthPx = cardWidthPx;
        setHasStableIds(true);
    }

    int cardWidthPx() {
        return cardWidthPx;
    }

    void setItems(List<AppEntry> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).stableId();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CardHolder holder = CardHolder.create(parent, listener);
        holder.itemView.getLayoutParams().width = cardWidthPx;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull CardHolder holder, int position) {
        holder.bind(items.get(position));
    }
}
