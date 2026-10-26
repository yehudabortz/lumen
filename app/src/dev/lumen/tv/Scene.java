package dev.lumen.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Calendar;
import java.util.Random;

/**
 * Gradient backgrounds drawn in code: broad shapes of colour painted small, blurred into soft blends and stretched to
 * the screen, then film grain and, where light falls, a fine sparkle. No image files; the same seed always gives the
 * same picture.
 */
final class Scene {
    /** One shape of colour, in design pixels, before the blur turns it into a soft blend. */
    private interface Op { void paint(Canvas c, Paint paint); }

    /** A rotated ellipse of solid colour. */
    private static Op blob(String color, float x, float y, float rx, float ry, float angle) {
        final int col = Color.parseColor(color);
        return (c, paint) -> {
            paint.setColor(col);
            c.save();
            c.rotate(angle, x * Ui.W, y * Ui.H);
            c.drawOval(new RectF(x * Ui.W - rx * Ui.W, y * Ui.H - ry * Ui.W, x * Ui.W + rx * Ui.W, y * Ui.H + ry * Ui.W), paint);
            c.restore();
        };
    }

    /** Concentric bands around a point, outermost colour first, like light bending across a horizon. */
    private static Op arcs(float x, float y, float r0, float step, String... colors) {
        return (c, paint) -> {
            for (int i = 0; i < colors.length; i++) {
                paint.setColor(Color.parseColor(colors[i]));
                c.drawCircle(x * Ui.W, y * Ui.H, (r0 + step * (colors.length - 1 - i)) * Ui.W, paint);
            }
        };
    }

    /** A cone of light from (x, y) along `angle`, brightest at its source and fading with distance. */
    private static Op beam(String color, float x, float y, float angle, float spread, float length) {
        final int col = Color.parseColor(color);
        return (c, paint) -> {
            float sx = x * Ui.W, sy = y * Ui.H, len = length * Ui.W;
            double a0 = Math.toRadians(angle - spread / 2), a1 = Math.toRadians(angle + spread / 2);
            Path cone = new Path();
            cone.moveTo(sx, sy);
            for (int i = 0; i <= 24; i++) {
                double t = a0 + (a1 - a0) * i / 24;
                cone.lineTo(sx + (float) Math.cos(t) * len, sy + (float) Math.sin(t) * len);
            }
            cone.close();
            paint.setShader(new RadialGradient(sx, sy, len, new int[]{Ui.mix(col, Color.WHITE, .25f), col, Ui.withAlpha(col, .35f), Ui.withAlpha(col, 0)},
                    new float[]{0, .25f, .6f, 1}, Shader.TileMode.CLAMP));
            c.drawPath(cone, paint);
            paint.setShader(null);
        };
    }

    private static final class Look {
        final String id;
        final int base;
        final boolean bright;
        final float grain, sparkle;
        final Op[] ops;

        Look(String id, String base, boolean bright, float grain, float sparkle, Op... ops) {
            this.id = id; this.base = Color.parseColor(base); this.bright = bright;
            this.grain = grain; this.sparkle = sparkle; this.ops = ops;
        }
    }

    private static final Look[] LOOKS = {
        new Look("horizon", "#16194a", false, .16f, 0,
                arcs(-.12f, 1.25f, .5f, .17f, "#16194a", "#123a5e", "#1fa58f", "#d9706e", "#e98a5a", "#f7c35f"),
                blob("#2a1f5c", .95f, .95f, .3f, .2f, -20)),
        new Look("sorbet", "#ff6f91", true, .05f, 0,
                blob("#2aa6ff", .2f, .1f, .5f, .28f, -8), blob("#d9a6ff", .14f, .55f, .22f, .2f, 0),
                blob("#ff3fb4", .3f, .95f, .34f, .26f, 10), blob("#ff8a1f", .66f, .82f, .3f, .24f, -15),
                blob("#ff4f6d", .62f, .48f, .2f, .16f, 0), blob("#ffa51f", .96f, .12f, .2f, .26f, 20)),
        new Look("beam", "#020707", false, .1f, .9f,
                beam("#0f6b62", 1.04f, -.08f, 150, 84, .8f), beam("#5fd6c8", 1.04f, -.08f, 148, 44, 1f)),
        new Look("ember", "#0b0403", false, .11f, .55f,
                beam("#8a2414", -.04f, 1.08f, -30, 84, .8f), beam("#ff8a3d", -.04f, 1.08f, -32, 44, 1f),
                blob("#3a0c0a", .9f, .2f, .3f, .2f, 0)),
        new Look("aurora", "#040b1f", false, .1f, .15f,
                blob("#0fb59a", .25f, .3f, .38f, .14f, -18), blob("#2d6cdf", .62f, .22f, .3f, .14f, 12),
                blob("#7a3fd1", .88f, .5f, .2f, .22f, 0), blob("#06302f", .3f, .85f, .4f, .2f, 0)),
        new Look("dusk", "#1d0f3f", false, .14f, 0,
                arcs(1.1f, 1.3f, .45f, .16f, "#1d0f3f", "#4c2a85", "#a8458f", "#ef6f7d", "#ffab76", "#ffd59a")),
        new Look("lagoon", "#04314d", false, .08f, 0,
                blob("#0ec6d6", .78f, .2f, .4f, .26f, 15), blob("#1b7cf0", .2f, .3f, .34f, .26f, -10),
                blob("#16d9a3", .58f, .9f, .42f, .22f, -8)),
        new Look("midnight", "#03040c", false, .1f, .7f,
                beam("#232a7a", -.05f, -.1f, 40, 84, .8f), beam("#6d6cf0", -.05f, -.1f, 38, 44, 1f)),
        new Look("citrus", "#ffb627", true, .07f, 0,
                blob("#ffe14d", .2f, .2f, .45f, .3f, 0), blob("#b5f23d", .1f, .86f, .3f, .22f, 0),
                blob("#ff7a1a", .82f, .72f, .4f, .3f, -12), blob("#ff4d4d", .98f, .2f, .18f, .2f, 0)),
        new Look("rose", "#12040c", false, .11f, .6f,
                beam("#6a1a48", .5f, -.15f, 90, 84, .8f), beam("#ff7ab8", .5f, -.15f, 90, 40, 1f)),
    };

    /** The drawn looks, in menu order; Graphite and Black are plain and listed separately in Prefs. */
    static final String[] KINDS = new String[LOOKS.length];
    static { for (int i = 0; i < LOOKS.length; i++) KINDS[i] = LOOKS[i].id; }

    /** Time-of-day toning laid over a look: a colour wash and a dimming. Day leaves the look as designed. */
    static final class Palette {
        final int tint;
        final float tintAlpha, dim;

        Palette(String tint, float tintAlpha, float dim) {
            this.tint = Color.parseColor(tint); this.tintAlpha = tintAlpha; this.dim = dim;
        }
    }

    static final Palette DAWN = new Palette("#ff9a8b", .10f, .06f);
    static final Palette DAY = new Palette("#ffffff", 0, 0);
    static final Palette DUSK = new Palette("#ff8a3d", .08f, .18f);
    static final Palette NIGHT = new Palette("#1a2a6c", .16f, .42f);

    /** Which palette the clock calls for: dawn 5–8, day 8–17, dusk 17–20, night otherwise. */
    static Palette forTime(Calendar now) {
        int h = now.get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 8) return DAWN;
        if (h >= 8 && h < 17) return DAY;
        if (h >= 17 && h < 20) return DUSK;
        return NIGHT;
    }

    static boolean isBright(Palette p, String kind) {
        Look look = find(kind);
        return look != null && look.bright && p.dim < .1f;
    }

    private static final float W = Ui.W, H = Ui.H;
    private static final int SOFT_W = 384, SOFT_H = 216;  // the blend is painted and blurred at a fifth of 1080p

    private Scene() {}

    private static Look find(String kind) {
        for (Look l : LOOKS) if (l.id.equals(kind)) return l;
        return null;
    }

    /** Renders the background into a new bitmap of the given pixel size. */
    static Bitmap render(String kind, Palette p, long seed, int width, int height) {
        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(width / W, height / H);
        Random r = new Random(seed);
        Paint paint = new Paint();
        plain(paint);
        if ("black".equals(kind)) { c.drawColor(Color.BLACK); return b; }
        if ("graphite".equals(kind)) {
            c.drawColor(Color.parseColor("#16171b"));
            paint.setShader(new RadialGradient(W * .3f, H * .1f, W * .9f, Color.parseColor("#3a3d45"), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, paint);
            grain(c, r, paint, .06f);
            return b;
        }
        Look look = find(kind);
        if (look == null) look = LOOKS[0];

        // Paint the shapes small and blur them hard: the colours run together like light through frosted glass
        Bitmap soft = Bitmap.createBitmap(SOFT_W, SOFT_H, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(soft);
        sc.scale(SOFT_W / W, SOFT_H / H);
        sc.drawColor(look.base);
        for (Op op : look.ops) { plain(paint); op.paint(sc, paint); }
        Blur.boxBlur(soft, 26, 3);
        plain(paint);
        paint.setFilterBitmap(true);
        c.drawBitmap(soft, null, new RectF(0, 0, W, H), paint);

        if (look.sparkle > 0) sparkle(c, r, paint, soft, look.sparkle);
        soft.recycle();
        grain(c, r, paint, look.grain);
        if (p.tintAlpha > 0) c.drawColor(Ui.withAlpha(p.tint, p.tintAlpha));
        if (p.dim > 0) c.drawColor(Color.argb(Math.round(255 * p.dim), 0, 0, 0));
        return b;
    }

    /** Fine bright specks, thickest where the blend is brightest, like dust caught in a beam. */
    private static void sparkle(Canvas c, Random r, Paint paint, Bitmap soft, float amount) {
        plain(paint);
        int tries = Math.round(140000 * amount);
        for (int i = 0; i < tries; i++) {
            float u = r.nextFloat(), v = r.nextFloat();
            int px = soft.getPixel(Math.min(SOFT_W - 1, (int) (u * SOFT_W)), Math.min(SOFT_H - 1, (int) (v * SOFT_H)));
            float lum = (.2126f * Color.red(px) + .7152f * Color.green(px) + .0722f * Color.blue(px)) / 255f;
            if (r.nextFloat() > lum * lum * 1.4f) continue;
            paint.setColor(Color.argb(Math.round((50 + r.nextFloat() * 180) * Math.min(1, lum * 1.5f)), 255, 255, 255));
            c.drawCircle(u * W, v * H, .4f + r.nextFloat() * .6f, paint);
        }
        plain(paint);
    }

    private static void plain(Paint paint) {
        paint.reset();
        paint.setFlags(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }

    /** Monochrome film grain from a repeating noise tile: the texture of the look, and it hides banding. */
    private static void grain(Canvas c, Random r, Paint paint, float strength) {
        int n = 512;
        int[] px = new int[n * n];
        for (int i = 0; i < px.length; i++) {
            float g = (float) r.nextGaussian() * .5f;
            int a = Math.min(255, Math.round(Math.abs(g) * 255 * strength * 2));
            px[i] = g > 0 ? Color.argb(a, 255, 255, 255) : Color.argb(a, 0, 0, 0);
        }
        Bitmap tile = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        plain(paint);
        paint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRect(0, 0, W, H, paint);
        plain(paint);
        tile.recycle();
    }
}
