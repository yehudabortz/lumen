package dev.lumen.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;

import java.util.HashMap;
import java.util.Map;

/** Typography, colors and small drawing helpers. Everything is laid out in design pixels (1920x1080). */
final class Ui {
    static final float W = 1920f, H = 1080f;

    static final int TEXT = Color.argb(242, 255, 255, 255);
    static final int TEXT_2 = Color.argb(158, 235, 235, 245);
    static final int TEXT_3 = Color.argb(102, 235, 235, 245);

    static Typeface medium, semibold, bold;

    private Ui() {}

    /** Inter if it's bundled in assets/fonts, otherwise the TV's own sans-serif at the closest weight. */
    static void init(Context c) {
        if (medium != null) return;
        medium = font(c, "Inter-Medium.ttf", 500);
        semibold = font(c, "Inter-SemiBold.ttf", 600);
        bold = font(c, "Inter-Bold.ttf", 700);
    }

    private static Typeface font(Context c, String file, int weight) {
        try {
            return Typeface.createFromAsset(c.getAssets(), "fonts/" + file);
        } catch (RuntimeException e) {
            if (Build.VERSION.SDK_INT >= 28) return Typeface.create(Typeface.SANS_SERIF, weight, false);
            return Typeface.create(weight >= 600 ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL);
        }
    }

    static Paint text(Typeface tf, float size, int color, float trackingEm) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        p.setLetterSpacing(trackingEm);
        return p;
    }

    static int withAlpha(int color, float a) {
        return (Math.round(Color.alpha(color) * clamp(a, 0, 1)) << 24) | (color & 0xFFFFFF);
    }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : v > hi ? hi : v; }

    static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static int mix(int a, int b, float t) {
        return Color.argb(
                Math.round(lerp(Color.alpha(a), Color.alpha(b), t)),
                Math.round(lerp(Color.red(a), Color.red(b), t)),
                Math.round(lerp(Color.green(a), Color.green(b), t)),
                Math.round(lerp(Color.blue(a), Color.blue(b), t)));
    }

    /** Ease toward a target, independent of frame rate. tau is the time constant in ms. */
    static float approach(float v, float target, long dtMs, float tau) {
        float k = 1f - (float) Math.exp(-dtMs / tau);
        float n = v + (target - v) * k;
        return Math.abs(target - n) < 0.002f ? target : n;
    }

    /** Ease-out cubic, for one-shot timelines like the glint. */
    static float easeOut(float t) {
        t = clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    // ---- Soft shadows: a blurred rounded rectangle, rendered once per size and reused ----

    private static final Map<String, Bitmap> SHADOWS = new HashMap<>();

    /**
     * A shadow bitmap for a w x h rounded rect, with `blur` design px of softness on every side.
     * Drawn at quarter resolution and stretched, which is plenty for something this soft.
     */
    static Bitmap shadow(float w, float h, float radius, float blur) {
        String key = (int) w + "x" + (int) h + "r" + (int) radius + "b" + (int) blur;
        Bitmap b = SHADOWS.get(key);
        if (b != null) return b;
        float k = 0.25f;
        int bw = Math.max(4, Math.round((w + blur * 2) * k)), bh = Math.max(4, Math.round((h + blur * 2) * k));
        b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        c.drawRoundRect(blur * k, blur * k, (blur + w) * k, (blur + h) * k, radius * k, radius * k, p);
        Blur.boxBlur(b, Math.max(1, Math.round(blur * k / 2)), 3);
        SHADOWS.put(key, b);
        return b;
    }
}
