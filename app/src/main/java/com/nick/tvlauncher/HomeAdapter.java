package com.nick.tvlauncher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The whole home screen as one vertical list: header, section titles, horizontal rows
 * (Favorites, Inputs) and the All apps grid, so D-pad focus and scrolling stay in one place.
 */
final class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    static final int TYPE_HEADER = 0;
    static final int TYPE_TITLE = 1;
    static final int TYPE_ROW = 2;
    static final int TYPE_CARD = 3;

    interface Listener extends CardBinder.Listener {
        void onSettings();
    }

    private static final class Item {
        final int type;
        final String title;
        final List<AppEntry> row;
        final AppEntry card;
        final int rowCardWidth;
        final long id;

        Item(int type, String title, List<AppEntry> row, AppEntry card, int rowCardWidth, long id) {
            this.type = type;
            this.title = title;
            this.row = row;
            this.card = card;
            this.rowCardWidth = rowCardWidth;
            this.id = id;
        }
    }

    private final Context context;
    private final Listener listener;
    private final List<Item> items = new ArrayList<>();

    HomeAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        setHasStableIds(true);
    }

    void setData(AppRepository.HomeData data, boolean showingHidden) {
        items.clear();
        items.add(new Item(TYPE_HEADER, null, null, null, 0, -1));
        int favWidth = context.getResources().getDimensionPixelSize(R.dimen.favorite_card_width);
        int inputWidth = context.getResources().getDimensionPixelSize(R.dimen.input_card_width);
        if (!data.favorites.isEmpty()) {
            items.add(new Item(TYPE_TITLE, context.getString(R.string.section_favorites), null, null, 0, -2));
            items.add(new Item(TYPE_ROW, null, data.favorites, null, favWidth, -3));
        }
        if (!data.inputs.isEmpty()) {
            items.add(new Item(TYPE_TITLE, context.getString(R.string.section_inputs), null, null, 0, -4));
            items.add(new Item(TYPE_ROW, null, data.inputs, null, inputWidth, -5));
        }
        items.add(new Item(TYPE_TITLE, context.getString(showingHidden
                ? R.string.section_all_apps_with_hidden : R.string.section_all_apps), null, null, 0, -6));
        for (AppEntry e : data.apps) {
            items.add(new Item(TYPE_CARD, null, null, e, 0, e.key.hashCode()));
        }
        notifyDataSetChanged();
    }

    /** Re-renders the header so the greeting follows the time of day. */
    void refreshHeader() {
        if (!items.isEmpty()) notifyItemChanged(0);
    }

    boolean isFullWidth(int position) {
        return items.get(position).type != TYPE_CARD;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_HEADER:
                return new Holder(inflater.inflate(R.layout.item_header, parent, false));
            case TYPE_TITLE:
                return new Holder(inflater.inflate(R.layout.item_section_title, parent, false));
            case TYPE_ROW: {
                RecyclerView row = (RecyclerView) inflater.inflate(R.layout.item_row, parent, false);
                row.setLayoutManager(new LinearLayoutManager(parent.getContext(),
                        LinearLayoutManager.HORIZONTAL, false));
                row.setItemAnimator(null);
                return new Holder(row);
            }
            default:
                return new Holder(inflater.inflate(R.layout.item_card, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item item = items.get(position);
        View v = holder.itemView;
        switch (item.type) {
            case TYPE_HEADER:
                ((TextView) v.findViewById(R.id.greeting)).setText(greeting());
                v.findViewById(R.id.settings_button).setOnClickListener(b -> listener.onSettings());
                break;
            case TYPE_TITLE:
                ((TextView) v).setText(item.title);
                break;
            case TYPE_ROW: {
                RecyclerView row = (RecyclerView) v;
                RowAdapter adapter = (RowAdapter) row.getAdapter();
                if (adapter == null || row.getTag() == null || (int) row.getTag() != item.rowCardWidth) {
                    adapter = new RowAdapter(listener, item.rowCardWidth);
                    row.setAdapter(adapter);
                    row.setTag(item.rowCardWidth);
                }
                adapter.setItems(item.row);
                break;
            }
            default:
                CardBinder.bind(v, item.card, listener);
        }
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res = hour < 5 ? R.string.greeting_night
                : hour < 12 ? R.string.greeting_morning
                : hour < 17 ? R.string.greeting_afternoon
                : hour < 22 ? R.string.greeting_evening
                : R.string.greeting_night;
        return context.getString(res);
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(View v) {
            super(v);
        }
    }
}
