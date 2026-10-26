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
import android.view.KeyEvent;

import java.util.Calendar;
import java.util.Random;

/**
 * Lumen's screensaver: a NASA photo (or another selected background) drifting slowly, a new one crossfading in every five
 * minutes, with a large clock, the date and the weather. The text block moves to another corner every few minutes
 * so nothing burns into an OLED panel. Any remote key exits the dream and returns to the underlying home screen.
 */
public final class Screensaver extends DreamService {
    private static volatile long suppressHomeBackUntil;
    private static Bitmap wakeFrame;  // the last frame, handed to Home so it can dissolve from it
    private static long wakeFrameAt;

    /**
     * The frame the screensaver last showed when a key woke it, once; Home fades it out over itself. A frame
     * Home didn't pick up straight away (the screensaver started over another app) is stale and dropped.
     */
    static Bitmap takeWakeFrame() {
        Bitmap b = wakeFrame;
        wakeFrame = null;
        if (b != null && SystemClock.uptimeMillis() - wakeFrameAt > 1000) { b.recycle(); return null; }
        return b;
    }

    static boolean consumeHomeBack(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_BACK && event.getKeyCode() != KeyEvent.KEYCODE_ESCAPE) return false;
        if (SystemClock.uptimeMillis() > suppressHomeBackUntil) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) suppressHomeBackUntil = 0;
        return true;
    }

    private static void suppressHomeBack() {
        suppressHomeBackUntil = SystemClock.uptimeMillis() + 1500;
    }

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);
        setFullscreen(true);
        setScreenBright(true);
        setContentView(new SaverView(this));
    }

    @Override public void onDreamingStopped() {
        suppressHomeBack();
        super.onDreamingStopped();
    }

    static final class SaverView extends View {
        private static final long PHOTO_MS = 5 * 60_000L, CORNER_MS = 4 * 60_000L;
        private static final long ENTRY_FADE_MS = 450, PHOTO_FADE_MS = 2500, CORNER_FADE_MS = 600, DRIFT_MS = 90_000L;

        private final Prefs prefs;
        private final Weather weather;
        private Bitmap current, previous;
        private String credit;
        private long shownAt, firstShownAt, nextAt, fadeDuration = ENTRY_FADE_MS, started = SystemClock.uptimeMillis();
        private boolean loading, loadAgain;
        private boolean shownFirstImage;
        private final Random random = new Random();
        private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint timeP, lineP, creditP;
        private final Matrix m = new Matrix();
        private LinearGradient shade;
        private float s = 1, vh = Ui.H;  // vh: the view's height in design px
        private long textMinute = -1;
        private String cachedTime = "", cachedLine = "", textDescription;
        private int textTemp = Integer.MIN_VALUE;
        private boolean textWeather;
        private boolean attached, finishing;

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
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) suppressHomeBack();
            return true;
        }

        @Override public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
            // Consume the complete press before finishing; otherwise Home can receive the
            // trailing Back event and immediately launch the screensaver again.
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) suppressHomeBack();
            if (finishing) return true;
            finishing = true;
            if (!prefs.is(Prefs.MOTION, "reduced")) { wakeFrame = capture(); wakeFrameAt = SystemClock.uptimeMillis(); }
            post(() -> ((Screensaver) getContext()).finish());
            return true;
        }

        /**
         * This frame at half size. Home is not drawn while the screensaver is up, so it can't fade to reveal it;
         * instead Home opens under a copy of this frame and dissolves it.
         */
        private Bitmap capture() {
            if (getWidth() == 0) return null;
            try {
                Bitmap b = Bitmap.createBitmap(getWidth() / 2, getHeight() / 2, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(b);
                c.scale(.5f, .5f);
                draw(c);
                return b;
            } catch (OutOfMemoryError e) {
                return null;
            }
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
            vh = h / s;
            shade = new LinearGradient(0, vh, 0, vh * .45f, Color.argb(150, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP);
            load();
        }

        /** The next image, off the main thread: a NASA photo, its cached fallback, or a landscape. */
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
                    if (!shownFirstImage) firstShownAt = shownAt;
                    fadeDuration = shownFirstImage ? PHOTO_FADE_MS : ENTRY_FADE_MS;
                    shownFirstImage = true;
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
            float fade = Ui.clamp((now - shownAt) / (float) fadeDuration, 0, 1);
            if (previous != null && fade < 1) drawDrifting(c, previous, 255, now);
            if (current != null) drawDrifting(c, current, Math.round(255 * fade), now);
            if (fade >= 1 && previous != null) { previous.recycle(); previous = null; }

            c.save();
            c.scale(s, s);
            fill.setShader(shade);
            c.drawRect(0, 0, Ui.W, vh, fill);
            fill.setShader(null);
            drawText(c, now);
            c.restore();
            boolean reduced = prefs.is(Prefs.MOTION, "reduced");
            long cornerTime = (now - started) % CORNER_MS;
            boolean fading = fade < 1 || cornerTime < CORNER_FADE_MS || cornerTime > CORNER_MS - CORNER_FADE_MS;
            if (fading) postInvalidateOnAnimation();  // fades run at the display's full rate; the slow drift doesn't need it
            else postInvalidateDelayed(reduced ? 1000 : 33);
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
            // Starts top-right, where Home's clock sits, then 3 top-left, 0 bottom-left, 1 bottom-right
            int corner = (int) (((now - started) / CORNER_MS + 2) % 4);  // 0 bottom-left, 1 bottom-right, 2 top-right, 3 top-left
            long into = (now - started) % CORNER_MS;
            float a = Ui.clamp(Math.min(into, CORNER_MS - into) / (float) CORNER_FADE_MS, 0, 1);  // fade out and in when moving
            if (now - started < CORNER_MS / 2) a = firstShownAt == 0 ? 0 : Ui.clamp((now - firstShownAt) / (float) ENTRY_FADE_MS, 0, 1);  // first corner: in with the picture
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

            float x = right ? Ui.W - 120 : 120, timeBase = top ? 96 + 150 * .73f : vh - 104 - 48;
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
                c.drawText(credit, right ? Ui.SAFE_X : Ui.W - Ui.SAFE_X, top ? vh - 40 : 64, creditP);
            }
        }
    }
}
