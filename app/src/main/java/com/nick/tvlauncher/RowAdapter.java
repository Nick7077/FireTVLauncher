package com.nick.tvlauncher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Cards in one horizontal row (Favorites, Inputs). */
final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
    private final CardBinder.Listener listener;
    private final int cardWidthPx;
    private List<AppEntry> items = new ArrayList<>();

    RowAdapter(CardBinder.Listener listener, int cardWidthPx) {
        this.listener = listener;
        this.cardWidthPx = cardWidthPx;
        setHasStableIds(true);
    }

    void setItems(List<AppEntry> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).key.hashCode();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false);
        v.getLayoutParams().width = cardWidthPx;
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CardBinder.bind(holder.itemView, items.get(position), listener);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View v) {
            super(v);
        }
    }
}
