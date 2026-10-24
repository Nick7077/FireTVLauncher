package com.nick.tvlauncher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LauncherActivity extends Activity implements HomeAdapter.Listener {
    private static final int GRID_COLUMNS = 5;
    private static final int BASE_BG = 0xFF07090D;

    private static final DecelerateInterpolator PANEL_EASE = new DecelerateInterpolator(2f);

    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    /** Bumped per reload so a burst of requests (e.g. package broadcasts) runs one load. */
    private final AtomicInteger reloadGeneration = new AtomicInteger();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private AppRepository repo;
    private HomeAdapter adapter;
    private RecyclerView list;
    private GradientDrawable ambient;
    private ValueAnimator ambientAnim;
    /** Accent the background is heading toward, and the tone currently drawn. */
    private int ambientColor = Colors.NEUTRAL;
    private int ambientShown = ambientTone(Colors.NEUTRAL);
    private boolean showHidden;
    private String lastSignature;
    private View optionsScrim;
    private View optionsPanel;
    /** Card that had focus when the options panel opened, so focus can return to it. */
    private View focusBeforeOptions;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Uri data = intent.getData();
            if (data != null) repo.invalidate(data.getSchemeSpecificPart());
            reload();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        repo = new AppRepository(this);

        // Background glow that drifts toward the focused app's brand color.
        ambient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{ambientShown, BASE_BG, BASE_BG});
        findViewById(R.id.root).setBackground(ambient);
        // The root paints the whole screen, so the window background would only be overdraw.
        getWindow().setBackgroundDrawable(null);

        adapter = new HomeAdapter(this, this);
        list = findViewById(R.id.list);
        GridLayoutManager glm = new GridLayoutManager(this, GRID_COLUMNS) {
            /** Keeps the focused row centered vertically instead of hugging the screen edge. */
            @Override
            public boolean requestChildRectangleOnScreen(RecyclerView parent, View child,
                    Rect rect, boolean immediate, boolean focusedChildVisible) {
                int childCenter = child.getTop() + rect.top + rect.height() / 2;
                int dy = childCenter - parent.getHeight() / 2;
                if (dy == 0) return false;
                if (immediate) parent.scrollBy(0, dy);
                else parent.smoothScrollBy(0, dy);
                return true;
            }
        };
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.isFullWidth(position) ? GRID_COLUMNS : 1;
            }
        });
        list.setLayoutManager(glm);
        list.setItemAnimator(null);
        list.setAdapter(adapter);

        optionsScrim = findViewById(R.id.options_scrim);
        optionsPanel = findViewById(R.id.options_panel);
        optionsScrim.setOnClickListener(v -> closeOptions());
        ImageView optionsIcon = findViewById(R.id.options_icon);
        optionsIcon.setClipToOutline(true);
        optionsIcon.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        view.getWidth() * 0.22f);
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.refreshHeader();
        reload();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        if (ambientAnim != null) ambientAnim.cancel();
        loader.shutdownNow();
        super.onDestroy();
    }

    /** Pressing Home while already home jumps back to the top. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (showHidden) {
            showHidden = false;
            reload();
        }
        list.scrollToPosition(0);
        list.post(this::focusFirstCard);
    }

    @Override
    public void onBackPressed() {
        // This is the home screen; Back only closes the panel or leaves the hidden-apps view.
        if (optionsOpen()) {
            closeOptions();
        } else if (showHidden) {
            showHidden = false;
            reload();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Menu (≡) opens the options for the focused card.
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (optionsOpen()) {
                closeOptions();
                return true;
            }
            View focused = getCurrentFocus();
            if (focused != null && focused.getTag() instanceof AppEntry) {
                onOptions((AppEntry) focused.getTag());
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void reload() {
        boolean hidden = showHidden;
        int generation = reloadGeneration.incrementAndGet();
        loader.execute(() -> {
            // A newer reload is queued behind this one; let it do the work.
            if (generation != reloadGeneration.get()) return;
            AppRepository.HomeData data = repo.load(hidden);
            runOnUiThread(() -> apply(data, hidden));
        });
    }

    private void apply(AppRepository.HomeData data, boolean hidden) {
        if (isDestroyed() || hidden != showHidden) return;
        String signature = data.signature;
        // Unchanged contents: keep the current views so focus stays where the user left it.
        if (signature.equals(lastSignature)) return;
        boolean firstLoad = lastSignature == null;
        lastSignature = signature;
        adapter.setData(data, hidden);
        if (firstLoad || getCurrentFocus() == null) list.post(this::focusFirstCard);
    }

    /** Focuses the first card in the list, skipping the header and titles. */
    private void focusFirstCard() {
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            int type = list.getChildViewHolder(child).getItemViewType();
            if (type == HomeAdapter.TYPE_ROW || type == HomeAdapter.TYPE_CARD) {
                if (child instanceof RecyclerView && ((RecyclerView) child).getChildCount() > 0) {
                    ((RecyclerView) child).getChildAt(0).requestFocus();
                } else {
                    child.requestFocus();
                }
                return;
            }
        }
    }

    @Override
    public void onLaunch(AppEntry entry) {
        if (tryStart(entry.launchIntent, entry.label)) repo.recordLaunch(entry.key);
    }

    /** Starts an activity, showing a toast instead of crashing if it is gone or refused. */
    private boolean tryStart(Intent intent, CharSequence label) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.launch_failed, label),
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override
    public void onFocused(AppEntry entry) {
        animateAmbient(entry.accent);
    }

    @Override
    public void onSettings() {
        tryStart(AppRepository.settingsIntent(getPackageManager()), getString(R.string.settings));
    }

    @Override
    public void onOptions(AppEntry entry) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(getString(entry.favorite ? R.string.remove_favorite : R.string.add_favorite));
        actions.add(() -> repo.toggleFavorite(entry.key));
        if (repo.canMoveFavorite(entry.key, -1)) {
            labels.add(getString(R.string.move_left));
            actions.add(() -> repo.moveFavorite(entry.key, -1));
        }
        if (repo.canMoveFavorite(entry.key, 1)) {
            labels.add(getString(R.string.move_right));
            actions.add(() -> repo.moveFavorite(entry.key, 1));
        }
        labels.add(getString(entry.hidden ? R.string.unhide : R.string.hide));
        actions.add(() -> repo.toggleHidden(entry.key));
        if (entry.kind == AppEntry.Kind.APP) {
            labels.add(getString(R.string.app_info));
            actions.add(() -> tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", entry.key, null)), getString(R.string.app_info)));
        }
        labels.add(getString(showHidden ? R.string.hide_hidden_apps : R.string.show_hidden_apps));
        actions.add(() -> showHidden = !showHidden);

        showOptions(entry, labels, actions);
    }

    private boolean optionsOpen() {
        return optionsScrim.getVisibility() == View.VISIBLE;
    }

    private void showOptions(AppEntry entry, List<String> labels, List<Runnable> actions) {
        focusBeforeOptions = getCurrentFocus();
        ((ImageView) findViewById(R.id.options_icon)).setImageDrawable(entry.icon);
        ((TextView) findViewById(R.id.options_title)).setText(entry.label);
        ((TextView) findViewById(R.id.options_subtitle)).setText(
                entry.kind == AppEntry.Kind.INPUT ? R.string.options_kind_input : R.string.options_kind_app);

        LinearLayout container = findViewById(R.id.options_list);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < labels.size(); i++) {
            TextView item = (TextView) inflater.inflate(R.layout.item_option, container, false);
            item.setText(labels.get(i));
            Runnable action = actions.get(i);
            item.setOnClickListener(v -> {
                closeOptions();
                action.run();
                reload();
            });
            container.addView(item);
        }

        // Keep D-pad focus inside the panel while it is open.
        list.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        optionsScrim.setVisibility(View.VISIBLE);
        optionsScrim.setAlpha(0f);
        optionsScrim.animate().alpha(1f).setDuration(180).start();
        optionsPanel.setTranslationX(getResources().getDimension(R.dimen.options_width));
        optionsPanel.animate().translationX(0).setDuration(220)
                .setInterpolator(PANEL_EASE).start();
        container.getChildAt(0).requestFocus();
    }

    private void closeOptions() {
        if (!optionsOpen()) return;
        optionsScrim.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> optionsScrim.setVisibility(View.GONE)).start();
        optionsPanel.animate().translationX(getResources().getDimension(R.dimen.options_width))
                .setDuration(150).start();
        list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (focusBeforeOptions != null && focusBeforeOptions.isAttachedToWindow()) {
            focusBeforeOptions.requestFocus();
        } else {
            focusFirstCard();
        }
    }

    private void animateAmbient(int accent) {
        if (accent == ambientColor) return;
        if (ambientAnim != null) ambientAnim.cancel();
        ambientColor = accent;
        // Start from what is on screen so fast D-pad moves blend instead of jumping.
        int start = ambientShown;
        int end = ambientTone(accent);
        ambientAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(450);
        ambientAnim.addUpdateListener(a -> {
            ambientShown = (int) argb.evaluate(a.getAnimatedFraction(), start, end);
            ambient.setColors(new int[]{ambientShown, BASE_BG, BASE_BG});
        });
        ambientAnim.start();
    }

    private static int ambientTone(int accent) {
        return Colors.tone(accent, 0.5f, 0.32f);
    }
}
