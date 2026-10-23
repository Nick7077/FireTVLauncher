package com.nick.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

/** Small color helpers: brand-color extraction and the gradients built from it. */
final class Colors {
    static final int NEUTRAL = 0xFF3A4556;
    private static final int SAMPLE = 24;
    private static final int BUCKETS = 12;

    private Colors() {
    }

    /**
     * Returns the most prominent vivid color in a logo. Pixels are grouped into hue
     * buckets weighted by saturation x brightness, so a red logo on a white card
     * yields red rather than a washed-out average.
     */
    static int dominant(Drawable d) {
        if (d == null) return NEUTRAL;
        Bitmap bmp = Bitmap.createBitmap(SAMPLE, SAMPLE, Bitmap.Config.ARGB_8888);
        Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
        copy.setBounds(0, 0, SAMPLE, SAMPLE);
        copy.draw(new Canvas(bmp));

        float[] weight = new float[BUCKETS];
        float[][] rgb = new float[BUCKETS][3];
        float[] hsv = new float[3];
        float total = 0;
        for (int y = 0; y < SAMPLE; y++) {
            for (int x = 0; x < SAMPLE; x++) {
                int c = bmp.getPixel(x, y);
                if (Color.alpha(c) < 200) continue;
                Color.colorToHSV(c, hsv);
                float w = hsv[1] * hsv[2];
                if (w < 0.12f) continue; // white, black and grey pixels say nothing about brand
                int b = Math.min(BUCKETS - 1, (int) (hsv[0] / 360f * BUCKETS));
                weight[b] += w;
                rgb[b][0] += Color.red(c) * w;
                rgb[b][1] += Color.green(c) * w;
                rgb[b][2] += Color.blue(c) * w;
                total += w;
            }
        }
        bmp.recycle();
        if (total < 4f) return NEUTRAL;

        int best = 0;
        for (int i = 1; i < BUCKETS; i++) if (weight[i] > weight[best]) best = i;
        float w = weight[best];
        return Color.rgb((int) (rgb[best][0] / w), (int) (rgb[best][1] / w), (int) (rgb[best][2] / w));
    }

    /** Same hue as {@code color} with the given saturation floor and brightness. */
    static int tone(int color, float minSat, float value) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[1] < 0.15f) hsv[1] = 0.15f; // keep greys slightly cool instead of flat
        else hsv[1] = Math.max(hsv[1], minSat);
        hsv[2] = value;
        return Color.HSVToColor(hsv);
    }
}
