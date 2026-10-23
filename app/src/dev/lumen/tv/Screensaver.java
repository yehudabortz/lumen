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
 * Lumen's screensaver: a NASA photo (or another selected background) drifting slowly, a new one crossfading in every five
 * minutes, with a large clock, the date and the weather. The text block moves to another corner every few minutes
 * so nothing burns into an OLED panel. Any remote key exits the dream and returns to the underlying home screen.
 */
public final class Screensaver extends DreamService {
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);
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
        private boolean loading, loadAgain;
        private final Random random = new Random();
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint timeP, lineP, creditP;
        private final Matrix m = new Matrix();
        private LinearGradient shade;
        private float s = 1;
        private long textMinute = -1;
        private String cachedTime = "", cachedLine = "", textDescription;
        private int textTemp = Integer.MIN_VALUE;
        private boolean textWeather;
        private boolean attached;

        SaverView(Context c) {
            super(c);
            setFocusable(true);
            setFocusableInTouchMode(true);
            Ui.init(c);
            prefs = new Prefs(c);
            stroke.setStyle(Paint.Style.STROKE);
            timeP = Ui.text(Ui.semibold, 150, Ui.TEXT, -.045f);
            timeP.setFontFeatureSettings("tnum");
            lineP = Ui.text(Ui.medium, 32, Color.argb(210, 255, 255, 255), -.005f);
            creditP = Ui.text(Ui.medium, 18, Ui.TEXT_3, 0);
            for (Paint p : new Paint[]{timeP, lineP, creditP}) p.setShadowLayer(30, 0, 4, Color.argb(90, 0, 0, 0));
            weather = new Weather(prefs, this::postInvalidate);
            if (prefs.is(Prefs.WEATHER, "on")) weather.refresh();
        }

        @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
            ((Screensaver) getContext()).finish();
            return true;
        }

        private final Runnable weatherTick = new Runnable() {
            @Override public void run() {
                if (prefs.is(Prefs.WEATHER, "on")) weather.refresh();
                postDelayed(this, 30_000);
            }
        };

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            requestFocus();
            post(weatherTick);
        }

        @Override protected void onDetachedFromWindow() {
            attached = false;
            removeCallbacks(weatherTick);
            super.onDetachedFromWindow();
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
            final int w = Math.min(1920, getWidth()), h = Math.round(w * getHeight() / (float) getWidth());
            final String mode = prefs.get(Prefs.SAVER);
            new Thread(() -> {
                Bitmap b = null;
                String cr = null;
                if ("nasa".equals(mode)) {
                    Nasa.Photo ph = Nasa.photo(getContext(), "saver", w, h, PHOTO_MS, () -> {
                        if (attached && prefs.is(Prefs.SAVER, "nasa")) {
                            if (loading) loadAgain = true; else load();
                        }
                    });
                    if (ph != null) { b = ph.bitmap; cr = ph.credit; }
                }
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
                    if (!attached) { if (fb != null) fb.recycle(); loading = false; return; }
                    if (previous != null) previous.recycle();
                    previous = current;
                    current = fb;
                    credit = fcr;
                    shownAt = SystemClock.uptimeMillis();
                    nextAt = shownAt + PHOTO_MS;
                    loading = false;
                    invalidate();
                    if (loadAgain) { loadAgain = false; load(); }
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
            boolean reduced = prefs.is(Prefs.MOTION, "reduced");
            long cornerTime = (now - started) % CORNER_MS;
            boolean fading = fade < 1 || cornerTime < 1200 || cornerTime > CORNER_MS - 1200;
            postInvalidateDelayed(!reduced || fading ? 33 : 1000);
        }

        /** Slow zoom-and-pan, back and forth over 90 s, so the picture never sits perfectly still. */
        private void drawDrifting(Canvas c, Bitmap b, int alpha, long now) {
            double phase = ((now - started) % (2 * DRIFT_MS)) / (double) DRIFT_MS;  // 0..2
            float t = prefs.is(Prefs.MOTION, "reduced") ? 0 : (float) (phase <= 1 ? phase : 2 - phase);
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
            boolean wx = prefs.is(Prefs.WEATHER, "on") && weather.ready();
            long minute = System.currentTimeMillis() / 60_000;
            if (minute != textMinute || wx != textWeather || weather.temp != textTemp || weather.description != textDescription) {
                String mode = prefs.get(Prefs.CLOCK);
                boolean h24 = "24".equals(mode) || ("system".equals(mode) && DateFormat.is24HourFormat(getContext()));
                Calendar cal = Calendar.getInstance();
                cachedTime = DateFormat.format(h24 ? "H:mm" : "h:mm", cal).toString();
                cachedLine = DateFormat.format("EEEE, MMMM d", cal).toString();
                if (wx) cachedLine += "   ·   " + weather.temp + "°  " + weather.description;
                textMinute = minute; textTemp = weather.temp; textDescription = weather.description; textWeather = wx;
            }

            float x = right ? Ui.W - 120 : 120, timeBase = top ? 96 + 150 * .73f : Ui.H - 104 - 48;
            Paint.Align al = right ? Paint.Align.RIGHT : Paint.Align.LEFT;
            timeP.setTextAlign(al);
            lineP.setTextAlign(al);
            timeP.setAlpha(Math.round(242 * a));
            lineP.setAlpha(Math.round(210 * a));
            c.drawText(cachedTime, x, timeBase, timeP);
            c.drawText(cachedLine, x, timeBase + 56, lineP);

            if (credit != null) {
                creditP.setTextAlign(right ? Paint.Align.LEFT : Paint.Align.RIGHT);
                creditP.setAlpha(Math.round(Color.alpha(Ui.TEXT_3) * a));
                c.drawText(credit, right ? Ui.SAFE_X : Ui.W - Ui.SAFE_X, top ? Ui.H - 40 : 64, creditP);
            }
        }
    }
}
