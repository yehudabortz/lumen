package dev.lumen.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.HashMap;
import java.util.Map;

/**
 * Soft shadows for rounded rects, drawn as a nine-slice: one blurred rounded square per (radius, blur), whose
 * corners are drawn as-is and whose edges stretch. Any size or animation reuses the same small bitmap, and the
 * edges stay soft because nothing is resampled at a different blur.
 */
final class Shadow {
    private static final Map<Long, Bitmap> CACHE = new HashMap<>();
    private static final float RES = .5f;  // bitmap pixels per design pixel; shadows are soft, half is plenty
    private static final Rect SRC = new Rect();
    private static final RectF DST = new RectF();

    private Shadow() {}

    /** Draws the shadow of `rr` (design px), blurred by `blur`, pushed down by `dy`, at `opacity` (0..1). */
    static void draw(Canvas c, RectF rr, float radius, float blur, float dy, float opacity, Paint paint) {
        if (opacity <= 0.005f) return;
        Bitmap b = bitmap(radius, blur);
        int n = b.getWidth(), cs = n / 2;           // corner slice in bitmap px (center row/column stretches)
        float cd = cs / RES;                         // the same corner in design px
        float l = rr.left - 2 * blur, t = rr.top - 2 * blur + dy, r = rr.right + 2 * blur, bt = rr.bottom + 2 * blur + dy;
        float cw = Math.min(cd, (r - l) / 2), ch = Math.min(cd, (bt - t) / 2);
        int sw = Math.round(cw * RES), sh = Math.round(ch * RES);
        paint.setAlpha(Math.round(255 * Math.min(1, opacity)));
        // corners
        slice(c, b, 0, 0, sw, sh, l, t, l + cw, t + ch, paint);
        slice(c, b, n - sw, 0, n, sh, r - cw, t, r, t + ch, paint);
        slice(c, b, 0, n - sh, sw, n, l, bt - ch, l + cw, bt, paint);
        slice(c, b, n - sw, n - sh, n, n, r - cw, bt - ch, r, bt, paint);
        // edges (stretch the middle column/row)
        if (r - l > 2 * cw) {
            slice(c, b, cs, 0, cs + 1, sh, l + cw, t, r - cw, t + ch, paint);
            slice(c, b, cs, n - sh, cs + 1, n, l + cw, bt - ch, r - cw, bt, paint);
        }
        if (bt - t > 2 * ch) {
            slice(c, b, 0, cs, sw, cs + 1, l, t + ch, l + cw, bt - ch, paint);
            slice(c, b, n - sw, cs, n, cs + 1, r - cw, t + ch, r, bt - ch, paint);
        }
        if (r - l > 2 * cw && bt - t > 2 * ch) slice(c, b, cs, cs, cs + 1, cs + 1, l + cw, t + ch, r - cw, bt - ch, paint);
        paint.setAlpha(255);
    }

    private static void slice(Canvas c, Bitmap b, int sl, int st, int sr, int sb, float l, float t, float r, float bt, Paint p) {
        if (sr <= sl || sb <= st || r <= l || bt <= t) return;
        SRC.set(sl, st, sr, sb);
        DST.set(l, t, r, bt);
        c.drawBitmap(b, SRC, DST, p);
    }

    /** A blurred rounded square: margin 2*blur, then radius + blur of solid on each side of an odd center. */
    private static Bitmap bitmap(float radius, float blur) {
        long key = ((long) Math.round(radius) << 32) | Math.round(blur);
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        int margin = Math.round(2 * blur * RES), inner = Math.round((radius + blur) * RES);
        int n = 2 * (margin + inner) + 1;
        b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        float rr = radius * RES;
        new Canvas(b).drawRoundRect(margin, margin, n - margin, n - margin, rr, rr, p);
        // three box passes ≈ a Gaussian with sigma ≈ blur/2
        Blur.boxBlur(b, Math.max(1, Math.round(blur * RES * .58f)), 3);
        CACHE.put(key, b);
        return b;
    }
}
