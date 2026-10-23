package dev.lumen.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;

/** Typography, colors and small drawing helpers. Everything is laid out in design pixels (1920x1080). */
final class Ui {
    static final float W = 1920f, H = 1080f;

    // Shared layout tokens (design px, on an 8-pt grid). Inner radius = outer radius - padding, always.
    static final float SAFE_X = 96;                                // TV overscan margin, left and right
    static final float BAR_RIGHT = W - SAFE_X, BAR_TOP = 52, BAR_H = 64, BAR_R = BAR_H / 2;
    static final float CHIP_INSET = 6, CHIP_D = BAR_H - 2 * CHIP_INSET;   // gear chip: concentric with the bar
    static final float TEXT_INSET = 24;                            // text inside a bar or a selection pill
    static final float MENU_R = 32, MENU_PAD = 16, ROW_R = MENU_R - MENU_PAD, ROW_H = 60;
    static final float DROPDOWN_GAP = 12;

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

    // ---- Continuous corners ----

    /**
     * Continuous ("squircle") corner, in units of the corner radius, for the top-right corner going from
     * the top edge to the right edge: (u, v) means (right - u*r, top + v*r). The curvature eases in over 1.53 r
     * instead of jumping from straight to circular, which keeps the rounded rectangle visually soft.
     */
    private static final float[] CORNER = {
            1.52866483f, 0f,
            1.08849323f, 0f, 0.86840689f, 0f, 0.66993427f, 0.06549600f,
            0.63149399f, 0.07491100f,
            0.37282392f, 0.16905899f, 0.16905899f, 0.37282392f, 0.07491100f, 0.63149399f,
            0.06549600f, 0.66993427f,
            0f, 0.86840689f, 0f, 1.08849323f, 0f, 1.52866483f,
    };

    /** Replaces `p` with a continuous rounded rect. Falls back to a capsule when the radius fills the short side. */
    static Path squircle(Path p, float l, float t, float r, float b, float radius) {
        p.reset();
        float half = Math.min(r - l, b - t) / 2;
        if (radius >= half * .98f) {  // capsules and circles keep true round ends, for smooth rounded ends
            p.addRoundRect(l, t, r, b, half, half, Path.Direction.CW);
            return p;
        }
        float k = Math.min(radius, half / 1.52866483f);
        corner(p, 0, l, t, r, b, k, true);
        corner(p, 1, l, t, r, b, k, false);
        corner(p, 2, l, t, r, b, k, false);
        corner(p, 3, l, t, r, b, k, false);
        p.close();
        return p;
    }

    static Path squircle(Path p, RectF rr, float radius) { return squircle(p, rr.left, rr.top, rr.right, rr.bottom, radius); }

    private static final float[] P0 = new float[2], P1 = new float[2], P2 = new float[2], P3 = new float[2];  // UI thread only

    private static void corner(Path p, int which, float l, float t, float r, float b, float k, boolean first) {
        float[] c = CORNER, pt = P0;
        map(which, c[0], c[1], l, t, r, b, k, pt);
        if (first) p.moveTo(pt[0], pt[1]); else p.lineTo(pt[0], pt[1]);
        float[] a = P1, bb = P2, e = P3;
        cubic(p, which, c, 2, l, t, r, b, k, a, bb, e);
        map(which, c[8], c[9], l, t, r, b, k, pt);
        p.lineTo(pt[0], pt[1]);
        cubic(p, which, c, 10, l, t, r, b, k, a, bb, e);
        map(which, c[16], c[17], l, t, r, b, k, pt);
        p.lineTo(pt[0], pt[1]);
        cubic(p, which, c, 18, l, t, r, b, k, a, bb, e);
    }

    private static void cubic(Path p, int which, float[] c, int i, float l, float t, float r, float b, float k, float[] a, float[] bb, float[] e) {
        map(which, c[i], c[i + 1], l, t, r, b, k, a);
        map(which, c[i + 2], c[i + 3], l, t, r, b, k, bb);
        map(which, c[i + 4], c[i + 5], l, t, r, b, k, e);
        p.cubicTo(a[0], a[1], bb[0], bb[1], e[0], e[1]);
    }

    /** Corners in clockwise order: 0 top-right, 1 bottom-right, 2 bottom-left, 3 top-left. */
    private static void map(int which, float u, float v, float l, float t, float r, float b, float k, float[] out) {
        switch (which) {
            case 0: out[0] = r - u * k; out[1] = t + v * k; break;
            case 1: out[0] = r - v * k; out[1] = b - u * k; break;
            case 2: out[0] = l + u * k; out[1] = b - v * k; break;
            default: out[0] = l + v * k; out[1] = t + u * k; break;
        }
    }
}
