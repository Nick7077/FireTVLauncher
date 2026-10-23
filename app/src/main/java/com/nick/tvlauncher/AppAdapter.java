package com.nick.tvlauncher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class AppAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private List<AppEntry> apps = new ArrayList<>();

    AppAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    void setApps(List<AppEntry> apps) {
        this.apps = apps;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public AppEntry getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return apps.get(position).packageName.hashCode();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView != null ? convertView
                : inflater.inflate(R.layout.item_app, parent, false);
        AppEntry app = apps.get(position);
        ImageView banner = v.findViewById(R.id.banner);
        View iconGroup = v.findViewById(R.id.icon_group);
        if (app.banner != null) {
            banner.setImageDrawable(app.banner);
            banner.setVisibility(View.VISIBLE);
            iconGroup.setVisibility(View.GONE);
        } else {
            ((ImageView) v.findViewById(R.id.icon)).setImageDrawable(app.icon);
            ((TextView) v.findViewById(R.id.label)).setText(app.label);
            banner.setVisibility(View.GONE);
            iconGroup.setVisibility(View.VISIBLE);
        }
        v.findViewById(R.id.pin_marker).setVisibility(app.pinned ? View.VISIBLE : View.GONE);
        v.setContentDescription(app.label);
        return v;
    }
}
