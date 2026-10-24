package com.nick.tvlauncher;

import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Holds an item_card view and binds an {@link AppEntry} to it. Views are looked up and
 * listeners attached once per view, so rebinding while scrolling only swaps the data.
 */
final class CardHolder extends RecyclerView.ViewHolder {
    interface Listener {
        void onLaunch(AppEntry entry);

        void onOptions(AppEntry entry);

        void onFocused(AppEntry entry);
    }

    private static final float FOCUS_SCALE = 1.1f;
    private static final long FOCUS_ANIM_MS = 160;
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);

    private final View card;
    private final ImageView banner;
    private final View generated;
    private final ImageView icon;
    private final TextView title;
    private final TextView subtitle;
    private final TextView caption;
    private final float lift;
    private final float captionDrop;
    private AppEntry entry;

    static CardHolder create(ViewGroup parent, Listener listener) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false);
        return new CardHolder(v, listener);
    }

    private CardHolder(View root, Listener listener) {
        super(root);
        card = root.findViewById(R.id.card);
        banner = root.findViewById(R.id.banner);
        generated = root.findViewById(R.id.generated);
        icon = root.findViewById(R.id.icon);
        title = root.findViewById(R.id.title);
        subtitle = root.findViewById(R.id.subtitle);
        caption = root.findViewById(R.id.caption);
        Resources res = root.getResources();
        float radius = res.getDimension(R.dimen.card_radius);
        lift = res.getDimension(R.dimen.card_lift);
        captionDrop = res.getDimension(R.dimen.caption_drop);

        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });

        root.setOnClickListener(v -> {
            if (entry != null) listener.onLaunch(entry);
        });
        root.setOnLongClickListener(v -> {
            if (entry != null) listener.onOptions(entry);
            return true;
        });
        root.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? FOCUS_SCALE : 1f;
            card.animate().scaleX(scale).scaleY(scale).translationZ(hasFocus ? lift : 0)
                    .setDuration(FOCUS_ANIM_MS).setInterpolator(EASE).start();
            caption.animate().alpha(hasFocus ? 1f : 0f).translationY(hasFocus ? captionDrop : 0)
                    .setDuration(FOCUS_ANIM_MS).start();
            if (hasFocus && entry != null) listener.onFocused(entry);
        });
    }

    void bind(@NonNull AppEntry entry) {
        this.entry = entry;
        if (entry.banner != null) {
            banner.setImageDrawable(entry.banner);
            banner.setVisibility(View.VISIBLE);
            generated.setVisibility(View.GONE);
            card.setBackgroundColor(0xFF000000);
        } else {
            // No TV banner: build one from the app's own logo and brand color.
            banner.setImageDrawable(null);
            banner.setVisibility(View.GONE);
            generated.setVisibility(View.VISIBLE);
            icon.setImageDrawable(entry.icon);
            title.setText(entry.label);
            subtitle.setText(entry.subtitle);
            subtitle.setVisibility(entry.subtitle != null ? View.VISIBLE : View.GONE);
            card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Colors.tone(entry.accent, 0.45f, 0.72f),
                            Colors.tone(entry.accent, 0.45f, 0.30f)}));
        }

        caption.setText(entry.label);
        itemView.setAlpha(entry.hidden ? 0.45f : 1f);
        itemView.setContentDescription(entry.label);
        itemView.setTag(entry);

        // Recycled views may arrive mid-animation from a previous binding.
        card.animate().cancel();
        caption.animate().cancel();
        boolean focused = itemView.hasFocus();
        card.setScaleX(focused ? FOCUS_SCALE : 1f);
        card.setScaleY(focused ? FOCUS_SCALE : 1f);
        card.setTranslationZ(focused ? lift : 0);
        caption.setAlpha(focused ? 1f : 0f);
        caption.setTranslationY(focused ? captionDrop : 0);
    }
}
