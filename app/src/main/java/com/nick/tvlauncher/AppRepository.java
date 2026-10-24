package com.nick.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.provider.Settings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Loads installed apps and inputs, and remembers favorites, hidden apps and launch counts. */
final class AppRepository {
    /** Prefix for keys of input cards, which switch to an HDMI/composite input. */
    static final String INPUT_PREFIX = "input:";
    static final String STOCK_LAUNCHER = "com.amazon.tv.launcher";
    /** Favorites row is topped up with most-used apps to this size. */
    private static final int MAX_FAVORITES = 10;
    /** Distinct colors so inputs are easy to tell apart at a glance. */
    private static final int[] INPUT_ACCENTS = {0xFF4F7CFF, 0xFF14B8A6, 0xFFE0559B, 0xFFF59E0B};

    /** What the home screen shows, grouped by section. */
    static final class HomeData {
        final List<AppEntry> favorites = new ArrayList<>();
        final List<AppEntry> inputs = new ArrayList<>();
        final List<AppEntry> apps = new ArrayList<>();
        final boolean includesHidden;
        /** Computed on the loader thread so the UI thread only compares strings. */
        String signature;

        HomeData(boolean includesHidden) {
            this.includesHidden = includesHidden;
        }

        private String computeSignature() {
            StringBuilder sb = new StringBuilder(256).append(includesHidden).append('/');
            for (List<AppEntry> section : Arrays.asList(favorites, inputs, apps)) {
                for (AppEntry e : section) {
                    e.appendSignature(sb);
                    sb.append(';');
                }
                sb.append('/');
            }
            return sb.toString();
        }
    }

    /** Label and artwork of a launchable activity; loading these is the slow part of a load. */
    private static final class Art {
        final String className;
        final String label;
        final Drawable banner;
        final Drawable icon;
        final int accent;

        Art(String className, String label, Drawable banner, Drawable icon, int accent) {
            this.className = className;
            this.label = label;
            this.banner = banner;
            this.icon = icon;
            this.accent = accent;
        }
    }

    private final Context context;
    private final PackageManager pm;
    private final SharedPreferences prefs;
    /** Per package; dropped by {@link #invalidate} when the package is updated or removed. */
    private final Map<String, Art> artCache = new ConcurrentHashMap<>();
    /** Per input key, so inputs keep the same drawable (and signature) between loads. */
    private final Map<String, Drawable> inputIcons = new ConcurrentHashMap<>();

    AppRepository(Context context) {
        this.context = context.getApplicationContext();
        this.pm = context.getPackageManager();
        this.prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    /** Forgets cached artwork for a package that was installed, updated or removed. */
    void invalidate(String pkg) {
        artCache.remove(pkg);
    }

    /** Builds the home screen contents. Safe to call off the main thread. */
    HomeData load(boolean includeHidden) {
        Set<String> hidden = getHidden();
        List<String> favOrder = getFavoriteOrder();
        Set<String> favSet = new HashSet<>(favOrder);
        HomeData data = new HomeData(includeHidden);

        List<ResolveInfo> launchables = queryLaunchables();
        Set<String> installed = new HashSet<>();
        for (ResolveInfo ri : launchables) {
            String pkg = ri.activityInfo.packageName;
            installed.add(pkg);
            if (pkg.equals(STOCK_LAUNCHER) || pkg.equals(context.getPackageName())) continue;
            AppEntry e = toEntry(ri);
            e.hidden = hidden.contains(pkg);
            e.favorite = favSet.contains(pkg);
            if (!e.hidden || includeHidden) data.apps.add(e);
        }
        // Drop artwork of apps that went away without a broadcast reaching us.
        artCache.keySet().retainAll(installed);
        Collator collator = Collator.getInstance();
        Collections.sort(data.apps, (a, b) -> collator.compare(a.label, b.label));

        for (AppEntry e : loadInputs()) {
            e.hidden = hidden.contains(e.key);
            e.favorite = favSet.contains(e.key);
            if (!e.hidden || includeHidden) data.inputs.add(e);
        }

        // Favorites: everything the user starred, in their order, then most-used apps.
        Map<String, AppEntry> byKey = new HashMap<>();
        for (AppEntry e : data.apps) byKey.put(e.key, e);
        for (AppEntry e : data.inputs) byKey.put(e.key, e);
        for (String key : favOrder) {
            AppEntry e = byKey.get(key);
            if (e != null && !e.hidden) data.favorites.add(e);
        }
        Map<AppEntry, Integer> counts = new HashMap<>();
        for (AppEntry e : data.apps) {
            if (e.favorite || e.hidden) continue;
            int count = launchCount(e.key);
            if (count > 0) counts.put(e, count);
        }
        List<AppEntry> used = new ArrayList<>(counts.keySet());
        Collections.sort(used, (a, b) -> Integer.compare(counts.get(b), counts.get(a)));
        for (AppEntry e : used) {
            if (data.favorites.size() >= MAX_FAVORITES) break;
            data.favorites.add(e);
        }
        data.signature = data.computeSignature();
        return data;
    }

    private List<ResolveInfo> queryLaunchables() {
        Map<String, ResolveInfo> byPackage = new LinkedHashMap<>();
        // Leanback entries first so their TV-specific activity wins over the phone one.
        for (String category : new String[]{
                Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                byPackage.putIfAbsent(ri.activityInfo.packageName, ri);
            }
        }
        return new ArrayList<>(byPackage.values());
    }

    private AppEntry toEntry(ResolveInfo ri) {
        ActivityInfo ai = ri.activityInfo;
        String pkg = ai.packageName;
        Art art = artCache.get(pkg);
        if (art == null || !art.className.equals(ai.name)) {
            Drawable banner = ai.loadBanner(pm);
            Drawable icon = ai.loadIcon(pm);
            art = new Art(ai.name, String.valueOf(ai.loadLabel(pm)), banner, icon,
                    Colors.dominant(banner != null ? banner : icon));
            artCache.put(pkg, art);
        }
        Intent launch = new Intent(Intent.ACTION_MAIN)
                .setClassName(pkg, ai.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return new AppEntry(AppEntry.Kind.APP, pkg, art.label, art.banner, art.icon,
                launch, art.accent, null);
    }

    /** One card per connected passthrough input (HDMI, composite). */
    private List<AppEntry> loadInputs() {
        List<AppEntry> inputs = new ArrayList<>();
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return inputs;
        // Order by the built-in port name (HDMI 1, HDMI 2, ...) so colors stay tied to the
        // physical port even if the user renames an input.
        Map<TvInputInfo, String> portNames = new HashMap<>();
        for (TvInputInfo info : tim.getTvInputList()) {
            if (!info.isPassthroughInput() || info.isHidden(context)) continue;
            portNames.put(info, String.valueOf(info.loadLabel(context)));
        }
        List<TvInputInfo> infos = new ArrayList<>(portNames.keySet());
        Collator collator = Collator.getInstance();
        Collections.sort(infos, (a, b) -> collator.compare(portNames.get(a), portNames.get(b)));
        for (TvInputInfo info : infos) {
            int state = tim.getInputState(info.getId());
            if (state == TvInputManager.INPUT_STATE_DISCONNECTED) continue;
            CharSequence label = info.loadCustomLabel(context);
            if (label == null || label.length() == 0) label = portNames.get(info);
            Intent play = new Intent(context, InputPlayerActivity.class)
                    .putExtra(InputPlayerActivity.EXTRA_INPUT_ID, info.getId())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            String status = context.getString(state == TvInputManager.INPUT_STATE_CONNECTED
                    ? R.string.input_active : R.string.input_standby);
            int accent = INPUT_ACCENTS[inputs.size() % INPUT_ACCENTS.length];
            String key = INPUT_PREFIX + info.getId();
            Drawable icon = inputIcons.computeIfAbsent(key,
                    k -> context.getDrawable(R.drawable.ic_hdmi));
            inputs.add(new AppEntry(AppEntry.Kind.INPUT, key, String.valueOf(label), null, icon,
                    play, accent, status));
        }
        return inputs;
    }

    /**
     * Fire OS routes ACTION_SETTINGS through the stock home screen first, so prefer the
     * Android TV settings entry point, which opens Amazon's settings screen directly.
     */
    static Intent settingsIntent(PackageManager pm) {
        Intent tvSettings = new Intent(Intent.ACTION_MAIN)
                .addCategory("android.intent.category.LEANBACK_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tvSettings.resolveActivity(pm) != null) return tvSettings;
        return new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    void recordLaunch(String key) {
        prefs.edit().putInt("count_" + key, launchCount(key) + 1).apply();
    }

    private int launchCount(String key) {
        return prefs.getInt("count_" + key, 0);
    }

    void toggleFavorite(String key) {
        List<String> order = getFavoriteOrder();
        if (!order.remove(key)) order.add(key);
        saveFavoriteOrder(order);
    }

    /** Moves a favorite one place left (-1) or right (+1). */
    void moveFavorite(String key, int delta) {
        List<String> order = getFavoriteOrder();
        int i = order.indexOf(key);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= order.size()) return;
        Collections.swap(order, i, j);
        saveFavoriteOrder(order);
    }

    boolean canMoveFavorite(String key, int delta) {
        List<String> order = getFavoriteOrder();
        int i = order.indexOf(key);
        int j = i + delta;
        return i >= 0 && j >= 0 && j < order.size();
    }

    void toggleHidden(String key) {
        Set<String> hidden = new HashSet<>(getHidden());
        if (!hidden.remove(key)) hidden.add(key);
        prefs.edit().putStringSet("hidden", hidden).apply();
    }

    /** Owned by SharedPreferences: copy before modifying. */
    private Set<String> getHidden() {
        return prefs.getStringSet("hidden", Collections.emptySet());
    }

    /** Favorites were called "pins" in v1, so the old key is kept for continuity. */
    private List<String> getFavoriteOrder() {
        String raw = prefs.getString("pin_order", "");
        List<String> out = new ArrayList<>();
        for (String s : raw.split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private void saveFavoriteOrder(List<String> order) {
        prefs.edit().putString("pin_order", String.join(",", order)).apply();
    }
}
