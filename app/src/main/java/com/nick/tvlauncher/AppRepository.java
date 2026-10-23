package com.nick.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.provider.Settings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads installed apps and remembers pins, hidden apps and launch counts. */
final class AppRepository {
    static final String SETTINGS_PKG = "__settings__";
    /** Prefix for tiles that switch to an HDMI/composite input rather than open an app. */
    static final String INPUT_PREFIX = "input:";

    /** Stock launcher UI; never shown as a tile (we hide ourselves too). */
    private static final Set<String> ALWAYS_HIDDEN = new HashSet<>(Arrays.asList(
            "com.amazon.tv.launcher"));

    private final Context context;
    private final PackageManager pm;
    private final SharedPreferences prefs;

    AppRepository(Context context) {
        this.context = context.getApplicationContext();
        this.pm = context.getPackageManager();
        this.prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    List<AppEntry> loadApps(boolean includeHidden) {
        Map<String, ResolveInfo> byPackage = new LinkedHashMap<>();
        // Leanback entries first so their TV-specific activity wins over the phone one.
        for (String category : new String[]{
                Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!byPackage.containsKey(pkg)) byPackage.put(pkg, ri);
            }
        }

        Set<String> hidden = getHidden();
        List<String> pinOrder = getPinOrder();
        List<AppEntry> apps = new ArrayList<>();
        for (Map.Entry<String, ResolveInfo> e : byPackage.entrySet()) {
            String pkg = e.getKey();
            if (ALWAYS_HIDDEN.contains(pkg) || pkg.equals(context.getPackageName())) continue;
            if (!includeHidden && hidden.contains(pkg)) continue;
            ActivityInfo ai = e.getValue().activityInfo;
            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .setClassName(pkg, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            AppEntry entry = new AppEntry(pkg, String.valueOf(ai.loadLabel(pm)),
                    ai.loadBanner(pm), ai.loadIcon(pm), launch);
            entry.pinned = pinOrder.contains(pkg);
            apps.add(entry);
        }
        for (AppEntry input : loadInputs()) {
            if (!includeHidden && hidden.contains(input.packageName)) continue;
            input.pinned = pinOrder.contains(input.packageName);
            apps.add(input);
        }

        Collator collator = Collator.getInstance();
        Collections.sort(apps, (a, b) -> {
            if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
            if (a.pinned) {
                return Integer.compare(pinOrder.indexOf(a.packageName),
                        pinOrder.indexOf(b.packageName));
            }
            int byUse = Integer.compare(launchCount(b.packageName), launchCount(a.packageName));
            return byUse != 0 ? byUse : collator.compare(a.label, b.label);
        });

        apps.add(new AppEntry(SETTINGS_PKG, context.getString(R.string.settings), null,
                context.getDrawable(R.drawable.ic_settings),
                settingsIntent()));
        return apps;
    }

    /** One tile per connected passthrough input (HDMI, composite); antenna is the Live TV app. */
    private List<AppEntry> loadInputs() {
        List<AppEntry> inputs = new ArrayList<>();
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return inputs;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (!info.isPassthroughInput() || info.isHidden(context)) continue;
            if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_DISCONNECTED) continue;
            CharSequence label = info.loadCustomLabel(context);
            if (label == null || label.length() == 0) label = info.loadLabel(context);
            Intent play = new Intent(context, InputPlayerActivity.class)
                    .putExtra(InputPlayerActivity.EXTRA_INPUT_ID, info.getId())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            inputs.add(new AppEntry(INPUT_PREFIX + info.getId(), String.valueOf(label), null,
                    context.getDrawable(R.drawable.ic_input), play));
        }
        return inputs;
    }

    /**
     * Fire OS routes ACTION_SETTINGS through the stock home screen first, so prefer the
     * Android TV settings entry point, which opens Amazon's settings screen directly.
     */
    private Intent settingsIntent() {
        Intent tvSettings = new Intent(Intent.ACTION_MAIN)
                .addCategory("android.intent.category.LEANBACK_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tvSettings.resolveActivity(pm) != null) return tvSettings;
        return new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    void recordLaunch(String pkg) {
        prefs.edit().putInt("count_" + pkg, launchCount(pkg) + 1).apply();
    }

    private int launchCount(String pkg) {
        return prefs.getInt("count_" + pkg, 0);
    }

    void togglePinned(String pkg) {
        List<String> order = getPinOrder();
        if (!order.remove(pkg)) order.add(pkg);
        prefs.edit().putString("pin_order", String.join(",", order)).apply();
    }

    void toggleHidden(String pkg) {
        Set<String> hidden = new HashSet<>(getHidden());
        if (!hidden.remove(pkg)) hidden.add(pkg);
        prefs.edit().putStringSet("hidden", hidden).apply();
    }

    boolean isHidden(String pkg) {
        return getHidden().contains(pkg);
    }

    private Set<String> getHidden() {
        return prefs.getStringSet("hidden", Collections.emptySet());
    }

    private List<String> getPinOrder() {
        String raw = prefs.getString("pin_order", "");
        List<String> out = new ArrayList<>();
        for (String s : raw.split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }
}
