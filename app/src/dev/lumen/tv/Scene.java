package dev.lumen.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Calendar;
import java.util.Random;

/**
 * Landscapes drawn in code: layered ridges, haze, water, sun or moon, lit by a time-of-day palette.
 * No image files; the same seed always gives the same scene. Mirrors the generator in docs/design/mockup.html.
 */
final class Scene {
    static final String[] KINDS = {"alpine", "forest", "dunes", "coast", "graphite", "black"};

    static final class Palette {
        final int top, mid, low, sun, far, near, mist, water0, water1;
        final float sx, sy;
        final boolean night;

        Palette(String top, String mid, String low, String sun, float sx, float sy, String far, String near,
                int mist, String water0, String water1, boolean night) {
            this.top = Color.parseColor(top); this.mid = Color.parseColor(mid); this.low = Color.parseColor(low);
            this.sun = Color.parseColor(sun); this.sx = sx; this.sy = sy;
            this.far = Color.parseColor(far); this.near = Color.parseColor(near); this.mist = mist;
            this.water0 = Color.parseColor(water0); this.water1 = Color.parseColor(water1); this.night = night;
        }
    }

    static final Palette DAWN = new Palette("#26305f", "#8b6f9f", "#f6b487", "#ffd6a8", .70f, .60f, "#a88fa8", "#2b2238",
            Color.argb(87, 255, 214, 190), "#eaa989", "#3b2c4a", false);
    static final Palette DAY = new Palette("#3a7dd0", "#79b0e6", "#d6ebf8", "#fffbea", .74f, .20f, "#a7bfd6", "#22362d",
            Color.argb(112, 236, 246, 252), "#9cc6e6", "#24465a", false);
    static final Palette DUSK = new Palette("#131a47", "#5b3b79", "#f19c5e", "#ffb373", .66f, .64f, "#8b6088", "#1a1428",
            Color.argb(66, 255, 172, 132), "#da8b63", "#221932", false);
    static final Palette NIGHT = new Palette("#03060f", "#0a1330", "#1c2a54", "#e3ebff", .76f, .19f, "#2c3b60", "#050915",
            Color.argb(41, 120, 140, 205), "#1d2b55", "#03060e", true);

    /** Which palette the clock calls for: dawn 5–8, day 8–17, dusk 17–20, night otherwise. */
    static Palette forTime(Calendar now) {
        int h = now.get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 8) return DAWN;
        if (h >= 8 && h < 17) return DAY;
        if (h >= 17 && h < 20) return DUSK;
        return NIGHT;
    }

    static boolean isBright(Palette p, String kind) {
        return p == DAY && !"black".equals(kind) && !"graphite".equals(kind);
    }

    private static final float W = Ui.W, H = Ui.H;

    private Scene() {}

    /** Renders the scene into a new bitmap of the given pixel size. */
    static Bitmap render(String kind, Palette p, long seed, int width, int height) {
        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(width / W, height / H);
        draw(c, kind, p, new Random(seed));
        return b;
    }

    private static void draw(Canvas c, String kind, Palette p, Random r) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if ("black".equals(kind)) { c.drawColor(Color.BLACK); return; }
        if ("graphite".equals(kind)) {
            c.drawColor(Color.parseColor("#16171b"));
            paint.setShader(new RadialGradient(W * .3f, H * .1f, W * .9f, Color.parseColor("#3a3d45"), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, paint);
            return;
        }

        // Sky, stars, sun glow, sun or moon
        paint.setShader(new LinearGradient(0, 0, 0, H, new int[]{p.top, p.mid, p.low}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);
        float sx = p.sx * W, sy = p.sy * H;
        if (p.night) {
            paint.setColor(Color.WHITE);
            for (int i = 0; i < 260; i++) {
                float x = r.nextFloat() * W, y = r.nextFloat() * H * .58f, rad = r.nextFloat() * 1.4f + .3f;
                paint.setAlpha(Math.round((r.nextFloat() * .7f + .15f) * 255));
                c.drawCircle(x, y, rad, paint);
            }
            paint.setAlpha(255);
        }
        float glowR = p.night ? 260 : 620;
        paint.setShader(new RadialGradient(sx, sy, glowR,
                new int[]{Ui.withAlpha(p.sun, p.night ? .5f : .85f), Ui.withAlpha(p.sun, p.night ? .12f : .35f), Ui.withAlpha(p.sun, 0)},
                new float[]{0, .25f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);
        if (p.night) {
            Path moon = new Path(), bite = new Path();
            moon.addCircle(sx, sy, 32, Path.Direction.CW);
            bite.addCircle(sx + 14, sy - 9, 30, Path.Direction.CW);
            moon.op(bite, Path.Op.DIFFERENCE);
            paint.setColor(Color.parseColor("#f2f5ff"));
            c.drawPath(moon, paint);
        } else if (p != DAY) {
            paint.setShader(new RadialGradient(sx, sy, 56, new int[]{p.sun, p.sun, Ui.withAlpha(p.sun, 0)}, new float[]{0, .75f, 1}, Shader.TileMode.CLAMP));
            c.drawCircle(sx, sy, 56, paint);
            paint.setShader(null);
        }

        int far = Ui.mix(p.far, p.low, .28f);
        switch (kind) {
            case "forest": forest(c, p, r, paint, far); break;
            case "dunes": dunes(c, p, r, paint); break;
            case "coast": coast(c, p, r, paint, far, sx); break;
            default: alpine(c, p, r, paint, far, sx); break;
        }
    }

    // ---- Shapes ----

    private static final float STEP = 12, X0 = -20;
    private static final int N = (int) ((W + 40) / STEP) + 1;

    /** A ridge line: three octaves of sine; `sharp` folds them into peaks for mountains. */
    private static float[] ridge(Random r, float y0, float amp, boolean sharp, int octaves) {
        float[] f = new float[octaves], ph = new float[octaves], ys = new float[N];
        for (int k = 0; k < octaves; k++) {
            f[k] = (float) ((.8 + r.nextFloat() * 1.1) * Math.pow(2.15, k) / W * Math.PI * 2);
            ph[k] = r.nextFloat() * 6.28f;
        }
        for (int i = 0; i < N; i++) {
            float x = X0 + i * STEP, v = 0, a = 1, tot = 0;
            for (int k = 0; k < octaves; k++) {
                float s = (float) Math.sin(x * f[k] + ph[k]);
                v += a * (sharp ? 1 - Math.abs(s) : (s + 1) / 2);
                tot += a;
                a *= .4f;
            }
            ys[i] = y0 - amp * (float) Math.pow(v / tot, sharp ? 1.6 : 1);
        }
        return ys;
    }

    private static Path fill(float[] ys, float bottom) {
        Path path = new Path();
        path.moveTo(X0, ys[0]);
        for (int i = 1; i < ys.length; i++) path.lineTo(X0 + i * STEP, ys[i]);
        path.lineTo(W + 20, bottom);
        path.lineTo(X0, bottom);
        path.close();
        return path;
    }

    /** Pine silhouettes along a ridge. With edgesOnly they thin out toward the middle, framing the scene. */
    private static Path trees(Random r, float[] ys, float hMin, float hMax, float every, boolean edgesOnly) {
        Path path = new Path();
        for (int i = 0; i < ys.length - 1; i++) {
            if (r.nextFloat() > 12 / every) continue;
            float x = X0 + i * STEP;
            if (edgesOnly) {
                float e = Math.min(x, W - x);
                if (e > 420 || r.nextFloat() > 1 - e / 520) continue;
            }
            float y = ys[i], h = hMin + r.nextFloat() * (hMax - hMin), w = h * (.28f + r.nextFloat() * .1f);
            path.moveTo(x - w / 2, y + 4);
            path.lineTo(x, y - h);
            path.lineTo(x + w / 2, y + 4);
            path.close();
        }
        return path;
    }

    private static void mist(Canvas c, Palette p, Paint paint, float y, float h, float opacity) {
        int m = Ui.withAlpha(p.mist, Color.alpha(p.mist) / 255f * opacity);
        paint.setShader(new LinearGradient(0, y, 0, y + h, new int[]{Ui.withAlpha(m, 0), m, Ui.withAlpha(m, 0)}, null, Shader.TileMode.CLAMP));
        c.drawRect(-20, y, W + 20, y + h, paint);
        paint.setShader(null);
    }

    private static void solid(Canvas c, Paint paint, Path path, int color) {
        paint.setShader(null);
        paint.setColor(color);
        c.drawPath(path, paint);
    }

    private static void alpine(Canvas c, Palette p, Random r, Paint paint, int far, float sx) {
        float water = .73f * H;
        float[][] specs = {{.5f, .22f}, {.57f, .17f}, {.635f, .12f}, {.695f, .07f}};
        Path[] ranges = new Path[specs.length];
        int[] colors = new int[specs.length];
        for (int i = 0; i < specs.length; i++) {
            ranges[i] = fill(ridge(r, specs[i][0] * H, specs[i][1] * H, true, 3), H + 10);
            colors[i] = Ui.mix(far, p.near, i / 4.2f);
            solid(c, paint, ranges[i], colors[i]);
            if (i < 3) mist(c, p, paint, specs[i][0] * H - 40, 120, .55f);
        }
        float[] shore = ridge(r, .735f * H, .02f * H, false, 3);
        int shoreC = Ui.mix(far, p.near, .85f);
        Path shorePath = fill(shore, H + 10), shoreTrees = trees(r, shore, 16, 44, 18, false);
        solid(c, paint, shorePath, shoreC);
        solid(c, paint, shoreTrees, shoreC);

        // Lake, with the mountains reflected and faded
        paint.setShader(new LinearGradient(0, water, 0, H, p.water0, p.water1, Shader.TileMode.CLAMP));
        c.drawRect(0, water, W, H, paint);
        paint.setShader(null);
        c.save();
        c.clipRect(0, water, W, H);
        c.translate(0, 2 * water);
        c.scale(1, -1);
        for (int i = 0; i < ranges.length; i++) solid(c, paint, ranges[i], Ui.withAlpha(colors[i], .42f));
        solid(c, paint, shorePath, Ui.withAlpha(shoreC, .42f));
        c.restore();
        paint.setShader(new RadialGradient(0, 0, 1, Ui.withAlpha(p.sun, p.night ? .12f : .22f), Ui.withAlpha(p.sun, 0), Shader.TileMode.CLAMP));
        c.save();
        c.translate(sx, water + 120);
        c.scale(70, 170);
        c.drawCircle(0, 0, 1, paint);
        c.restore();
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        for (int i = 0; i < 18; i++) {
            float y = water + 12 + r.nextFloat() * (H - water - 20), x = r.nextFloat() * W, w = 80 + r.nextFloat() * 260;
            paint.setAlpha(Math.round((.04f + r.nextFloat() * .06f) * 255));
            c.drawRect(x, y, x + w, y + 1.5f, paint);
        }
        paint.setAlpha(255);
        float[] fg = ridge(r, 1.01f * H, .05f * H, false, 3);
        int fgC = Ui.mix(p.near, Color.BLACK, .45f);
        solid(c, paint, fill(fg, H + 10), fgC);
        solid(c, paint, trees(r, fg, 70, 230, 5, true), fgC);
    }

    private static void forest(Canvas c, Palette p, Random r, Paint paint, int far) {
        for (int i = 0; i < 6; i++) {
            float y = (.5f + i * .085f) * H;
            float[] ys = ridge(r, y, .07f * H, false, 3);
            int col = Ui.mix(far, p.near, i / 5.2f);
            solid(c, paint, fill(ys, H + 10), col);
            solid(c, paint, trees(r, ys, 18 + i * 12, 40 + i * 26, 26 - i * 2, false), col);
            if (i < 5) mist(c, p, paint, y - 30, 110, .7f - i * .08f);
        }
    }

    private static void dunes(Canvas c, Palette p, Random r, Paint paint) {
        int far = Ui.mix(p.far, Color.parseColor("#e3ab7c"), .5f), near = Ui.mix(p.near, Color.parseColor("#3a1c14"), .4f);
        for (int i = 0; i < 5; i++) {
            float[] ys = ridge(r, (.6f + i * .09f) * H, (.1f - i * .01f) * H, false, 2);
            solid(c, paint, fill(ys, H + 10), Ui.mix(far, near, i / 4f));
            float[] lip = ys.clone();
            for (int k = 0; k < lip.length; k++) lip[k] += 3;
            solid(c, paint, fill(lip, H + 10), Ui.withAlpha(Ui.mix(far, near, Math.min(1, i / 4f + .12f)), .6f));
            if (i < 2) mist(c, p, paint, (.6f + i * .09f) * H - 30, 90, .35f);
        }
    }

    private static void coast(Canvas c, Palette p, Random r, Paint paint, int far, float sx) {
        float sea = .62f * H;
        paint.setShader(new LinearGradient(0, sea, 0, H, new int[]{Ui.mix(p.low, p.water0, .35f), p.water0, p.water1},
                new float[]{0, .35f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, sea, W, H, paint);
        paint.setShader(new RadialGradient(0, 0, 1, Ui.withAlpha(p.sun, p.night ? .12f : .28f), Ui.withAlpha(p.sun, 0), Shader.TileMode.CLAMP));
        c.save();
        c.translate(sx, sea + 160);
        c.scale(90, 230);
        c.drawCircle(0, 0, 1, paint);
        c.restore();
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        for (int i = 0; i < 26; i++) {
            float y = sea + 8 + (float) Math.pow(r.nextFloat(), 1.6) * (H - sea), x = r.nextFloat() * W, w = 60 + r.nextFloat() * 300;
            paint.setAlpha(Math.round((.05f + r.nextFloat() * .07f) * 255));
            c.drawRect(x, y, x + w, y + 1.5f, paint);
        }
        paint.setAlpha(255);
        c.save();
        c.translate(W, 0);
        c.scale(-1, 1);
        headland(c, p, r, paint, far, -200, 700, .5f * H, .3f, sea);
        c.restore();
        headland(c, p, r, paint, far, -100, 860, .38f * H, .62f, sea);
        headland(c, p, r, paint, far, -100, 640, .28f * H, .94f, sea);
    }

    /** A ridge that falls into the sea toward x1; past x1 it drops below the waterline. */
    private static void headland(Canvas c, Palette p, Random r, Paint paint, int far, float x0, float x1, float top, float t, float sea) {
        float[] ys = ridge(r, top, .05f * H, true, 3);
        for (int i = 0; i < ys.length; i++) {
            float x = X0 + i * STEP, u = Ui.clamp((x1 - x) / (x1 - x0), 0, 1);
            ys[i] = u > 0 ? sea + 6 - (sea + 6 - ys[i]) * (float) Math.pow(u, .55) : H + 20;
        }
        solid(c, paint, fill(ys, H + 10), Ui.mix(far, p.near, t));
    }
}
