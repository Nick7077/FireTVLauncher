package com.nick.tvlauncher;

import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

/** Binds an {@link AppEntry} to an item_card view and animates it on focus. */
final class CardBinder {
    interface Listener {
        void onLaunch(AppEntry entry);

        void onOptions(AppEntry entry);

        void onFocused(AppEntry entry);
    }

    private static final float FOCUS_SCALE = 1.1f;
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);

    private CardBinder() {
    }

    static void bind(View root, AppEntry entry, Listener listener) {
        View card = root.findViewById(R.id.card);
        ImageView banner = root.findViewById(R.id.banner);
        View generated = root.findViewById(R.id.generated);
        TextView caption = root.findViewById(R.id.caption);
        float radius = root.getResources().getDimension(R.dimen.card_radius);

        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });

        if (entry.banner != null) {
            banner.setImageDrawable(entry.banner);
            banner.setVisibility(View.VISIBLE);
            generated.setVisibility(View.GONE);
            card.setBackgroundColor(0xFF000000);
        } else {
            // No TV banner: build one from the app's own logo and brand color.
            banner.setVisibility(View.GONE);
            generated.setVisibility(View.VISIBLE);
            ((ImageView) root.findViewById(R.id.icon)).setImageDrawable(entry.icon);
            ((TextView) root.findViewById(R.id.title)).setText(entry.label);
            TextView subtitle = root.findViewById(R.id.subtitle);
            subtitle.setText(entry.subtitle);
            subtitle.setVisibility(entry.subtitle != null ? View.VISIBLE : View.GONE);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Colors.tone(entry.accent, 0.45f, 0.72f),
                            Colors.tone(entry.accent, 0.45f, 0.30f)});
            card.setBackground(bg);
        }

        caption.setText(entry.label);
        root.setAlpha(entry.hidden ? 0.45f : 1f);
        root.setContentDescription(entry.label);
        root.setTag(entry);

        root.setOnClickListener(v -> listener.onLaunch(entry));
        root.setOnLongClickListener(v -> {
            listener.onOptions(entry);
            return true;
        });
        root.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? FOCUS_SCALE : 1f;
            card.animate().scaleX(scale).scaleY(scale)
                    .translationZ(hasFocus ? v.getResources().getDimension(R.dimen.card_lift) : 0)
                    .setDuration(160).setInterpolator(EASE).start();
            caption.animate().alpha(hasFocus ? 1f : 0f)
                    .translationY(hasFocus ? v.getResources().getDimension(R.dimen.caption_drop) : 0)
                    .setDuration(160).start();
            if (hasFocus) listener.onFocused(entry);
        });
        // Recycled views may arrive mid-animation from a previous binding.
        boolean focused = root.hasFocus();
        card.setScaleX(focused ? FOCUS_SCALE : 1f);
        card.setScaleY(focused ? FOCUS_SCALE : 1f);
        caption.setAlpha(focused ? 1f : 0f);
    }
}
