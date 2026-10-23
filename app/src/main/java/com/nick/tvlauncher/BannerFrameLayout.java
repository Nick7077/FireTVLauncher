package com.nick.tvlauncher;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** A tile that keeps the 16:9 shape of Android TV app banners at any column width. */
public class BannerFrameLayout extends FrameLayout {
    public BannerFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
    }
}
