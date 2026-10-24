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

    /** Non-negative, so it never collides with the adapters' fixed negative ids. */
    long stableId() {
        return key.hashCode() & 0xFFFFFFFFL;
    }

    /**
     * Identity used to skip redundant redraws when nothing changed. Artwork is cached per
     * package and replaced when the app updates, so its identity tracks new banners too.
     */
    void appendSignature(StringBuilder sb) {
        sb.append(key).append('|').append(label).append('|').append(favorite).append('|')
                .append(hidden).append('|').append(subtitle).append('|')
                .append(System.identityHashCode(banner)).append('|')
                .append(System.identityHashCode(icon));
    }
}
