package com.nick.tvlauncher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** One tile on the home screen: an installed app, or a built-in shortcut. */
final class AppEntry {
    final String packageName;
    final String label;
    final Drawable banner; // 16:9 TV banner, may be null
    final Drawable icon;
    final Intent launchIntent;
    boolean pinned;

    AppEntry(String packageName, String label, Drawable banner, Drawable icon,
             Intent launchIntent) {
        this.packageName = packageName;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
        this.launchIntent = launchIntent;
    }
}
