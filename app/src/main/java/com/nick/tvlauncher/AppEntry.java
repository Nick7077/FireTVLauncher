package com.nick.tvlauncher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** One card on the home screen: an installed app or a TV input. */
final class AppEntry {
    enum Kind { APP, INPUT }

    final Kind kind;
    /** Package name for apps, {@link AppRepository#INPUT_PREFIX} + input id for inputs. */
    final String key;
    final String label;
    final Drawable banner; // 16:9 TV banner, may be null
    final Drawable icon;
    final Intent launchIntent;
    /** Brand color pulled from the banner or icon; tints the card and the background. */
    final int accent;
    /** Secondary line on generated cards (input status); may be null. */
    final String subtitle;
    boolean favorite;
    boolean hidden;

    AppEntry(Kind kind, String key, String label, Drawable banner, Drawable icon,
             Intent launchIntent, int accent, String subtitle) {
        this.kind = kind;
        this.key = key;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
        this.launchIntent = launchIntent;
        this.accent = accent;
        this.subtitle = subtitle;
    }

    /** Identity used to skip redundant redraws when nothing changed. */
    String signature() {
        return key + '|' + label + '|' + favorite + '|' + hidden + '|' + subtitle;
    }
}
