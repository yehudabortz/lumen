package dev.lumen.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/**
 * Lumen's screensaver: an Unsplash photo (or the landscape) drifting slowly, a new one crossfading in every five
 * minutes, with a large clock, the date and the weather. The text block moves to another corner every few minutes
 * so nothing burns into an OLED panel. Any key wakes the TV (the system handles that for non-interactive dreams).
 */
public final class Screensaver extends DreamService {
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        setContentView(new SaverView(this));
    }

    static final class SaverView extends View {
        private static final long PHOTO_MS = 5 * 60_000L, CORNER_MS = 4 * 60_000L, FADE_MS = 2500, DRIFT_MS = 90_000L;

        private final Prefs prefs;
        private final Weather weather;
        private Bitmap current, previous;
        private String credit;
        private long shownAt, nextAt, started = SystemClock.uptimeMillis();
        private boolean loading;
        private final Random random = new Random();
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint timeP, lineP, creditP;
        private final Matrix m = new Matrix();
        private LinearGradient shade;
        private float s = 1;

        SaverView(Context c) {
            super(c);
            Ui.init(c);
            prefs = new Prefs(c);
            stroke.setStyle(Paint.Style.STROKE);
            timeP = Ui.text(Ui.semibold, 150, Ui.TEXT, -.045f);
            timeP.setFontFeatureSettings("tnum");
            lineP = Ui.text(Ui.medium, 32, Color.argb(210, 255, 255, 255), -.005f);
            creditP = Ui.text(Ui.medium, 18, Ui.TEXT_3, 0);
            for (Paint p : new Paint[]{timeP, lineP, creditP}) p.setShadowLayer(30, 0, 4, Color.argb(90, 0, 0, 0));
            String u = prefs.get(Prefs.UNITS);
            weather = new Weather("f".equals(u) || ("auto".equals(u) && AppStore.usesFahrenheit()), this::postInvalidate);
            if (prefs.is(Prefs.WEATHER, "on")) weather.refresh();
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            s = w / Ui.W;
            shade = new LinearGradient(0, Ui.H, 0, Ui.H * .45f, Color.argb(150, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP);
            load();
        }

        /** The next image, off the main thread: a fresh Unsplash photo, the cached one, or a landscape. */
        private void load() {
            if (loading || getWidth() == 0) return;
            loading = true;
            final int w = Math.round(getWidth() * 1.12f), h = Math.round(getHeight() * 1.12f);  // room to drift
            final String mode = prefs.get(Prefs.SAVER);
            new Thread(() -> {
                Bitmap b = null;
                String cr = null;
                if ("unsplash".equals(mode)) {
                    Unsplash.Photo ph = null;
                    try { ph = Unsplash.next(getContext(), "saver", w, h); } catch (Exception ignored) { }
                    if (ph == null) ph = Unsplash.cached(getContext(), "saver", w, h);
                    if (ph != null) { b = ph.bitmap; cr = ph.credit; }
                }
                if (b == null && !"black".equals(mode)) {
                    String[] kinds = {"alpine", "forest", "dunes", "coast"};
                    b = Scene.render(kinds[random.nextInt(kinds.length)], Scene.forTime(Calendar.getInstance()), random.nextInt(1000), w, h);
                }
                final Bitmap fb = b;
                final String fcr = cr;
                post(() -> {
                    previous = current;
                    current = fb;
                    credit = fcr;
                    shownAt = SystemClock.uptimeMillis();
                    nextAt = shownAt + PHOTO_MS;
                    loading = false;
                    invalidate();
                });
            }, "saver").start();
        }

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            if (now > nextAt && nextAt > 0) { nextAt = Long.MAX_VALUE; load(); }
            c.drawColor(Color.BLACK);
            float fade = Ui.clamp((now - shownAt) / (float) FADE_MS, 0, 1);
            if (previous != null && fade < 1) drawDrifting(c, previous, 255, now);
            if (current != null) drawDrifting(c, current, Math.round(255 * fade), now);
            if (fade >= 1 && previous != null) { previous.recycle(); previous = null; }

            c.save();
            c.scale(s, s);
            fill.setShader(shade);
            c.drawRect(0, 0, Ui.W, Ui.H, fill);
            fill.setShader(null);
            drawText(c, now);
            c.restore();
            postInvalidateOnAnimation();
        }

        /** Slow zoom-and-pan, back and forth over 90 s, so the picture never sits perfectly still. */
        private void drawDrifting(Canvas c, Bitmap b, int alpha, long now) {
            double phase = ((now - started) % (2 * DRIFT_MS)) / (double) DRIFT_MS;  // 0..2
            float t = (float) (phase <= 1 ? phase : 2 - phase);
            t = t * t * (3 - 2 * t);  // smoothstep: ease at both ends
            float scale = Math.max(getWidth() / (float) b.getWidth(), getHeight() / (float) b.getHeight()) * (1 + .04f * t);
            float dx = (getWidth() - b.getWidth() * scale) * (.3f + .4f * t), dy = (getHeight() - b.getHeight() * scale) * (.6f - .2f * t);
            m.setScale(scale, scale);
            m.postTranslate(dx, dy);
            bmp.setAlpha(alpha);
            c.drawBitmap(b, m, bmp);
        }

        /** Clock, date and weather in one corner, moving every few minutes; the credit sits in the opposite corner. */
        private void drawText(Canvas c, long now) {
            int corner = (int) (((now - started) / CORNER_MS) % 4);  // 0 bottom-left, 1 bottom-right, 2 top-right, 3 top-left
            long into = (now - started) % CORNER_MS;
            float a = Ui.clamp(Math.min(into, CORNER_MS - into) / 1200f, 0, 1);  // fade out and in when moving
            boolean right = corner == 1 || corner == 2, top = corner >= 2;
            Calendar cal = Calendar.getInstance();
            String mode = prefs.get(Prefs.CLOCK);
            boolean h24 = "24".equals(mode) || ("system".equals(mode) && DateFormat.is24HourFormat(getContext()));
            String time = DateFormat.format(h24 ? "H:mm" : "h:mm", cal).toString();
            String line = DateFormat.format("EEEE, MMMM d", cal).toString();
            boolean wx = prefs.is(Prefs.WEATHER, "on") && weather.ready();
            if (wx) line += "   ·   " + weather.temp + "°  " + weather.description;

            float x = right ? Ui.W - 120 : 120, timeBase = top ? 96 + 150 * .73f : Ui.H - 104 - 48;
            Paint.Align al = right ? Paint.Align.RIGHT : Paint.Align.LEFT;
            timeP.setTextAlign(al);
            lineP.setTextAlign(al);
            timeP.setAlpha(Math.round(242 * a));
            lineP.setAlpha(Math.round(210 * a));
            c.drawText(time, x, timeBase, timeP);
            c.drawText(line, x, timeBase + 56, lineP);

            if (credit != null) {
                creditP.setTextAlign(right ? Paint.Align.LEFT : Paint.Align.RIGHT);
                creditP.setAlpha(Math.round(Color.alpha(Ui.TEXT_3) * a));
                c.drawText(credit, right ? Ui.SAFE_X : Ui.W - Ui.SAFE_X, top ? Ui.H - 40 : 64, creditP);
            }
        }
    }
}
