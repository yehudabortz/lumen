package dev.lumen.tv;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The home screen, drawn on one Canvas in design pixels (1920x1080, scaled to the screen): scene, weather and
 * clock, the Top Shelf, the glass dock and All Apps. It also runs focus and navigation for the remote.
 * Layout numbers follow docs/design/DESIGN.md.
 */
final class HomeView extends View {
    interface Host {
        void launch(AppStore.App app);
        void open(ShelfStore.Item item, AppStore.App fallback);
    }

    // ---- Layout (design px) ----
    private static final float LEFT = 185, PAD = 22, GAP = 28, TILE_W = AppStore.TILE_W, TILE_H = AppStore.TILE_H;
    private static final float R_TILE = 20, R_DOCK = R_TILE + PAD, DOCK_BOTTOM = 872, ROW_MAX = 5;
    private static final float SHELF_TOP = 262, CARD_W = 376, CARD_H = 211, CARD_TOP = SHELF_TOP + 50;
    private static final float GRID_TOP = 1018, GRID_STEP_Y = TILE_H + 70, APPS_SHIFT = 540;

    private enum Area { ROW, SHELF, GRID }

    private final Host host;
    private float s = 1;  // screen px per design px

    // Data
    private AppStore apps;
    private ShelfStore shelfData;
    private ShelfStore.Shelf shelf;
    private final ArtCache art;
    private final Weather weather;

    // Scene
    private String kind = "alpine";
    private Scene.Palette palette;
    private Bitmap scene, glassBlur, deepBlur;
    private boolean sceneBusy;

    // Focus and motion
    private Area area = Area.ROW;
    private int rowI, cardI, gridI;
    private boolean moving;
    private float appsT, appsTarget, rowScroll, cardScroll, gridScroll, specX = -1;
    private final Map<Object, Float> focusAmt = new HashMap<>();
    private Object glintOf;
    private long glintStart, lastFrame, lastKey, toastUntil, frameDt = 16;
    private String toast;

    // Paints
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), glassPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint clockP, wxP, headP, headAppP, titleP, metaP, labelP, hintP, gridHeadP, nameP, cardTitleP, toastP;
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final Matrix m = new Matrix();
    private final Map<Bitmap, BitmapShader> shaders = new HashMap<>();
    private final Matrix sm = new Matrix();
    private final RectF screenRect = new RectF(), srcRect = new RectF();
    private static final int WHITE_0 = 0x00FFFFFF;
    private final LinearGradient sheenU = new LinearGradient(0, 0, 0, 1, Color.argb(36, 255, 255, 255), WHITE_0, Shader.TileMode.CLAMP);
    private final LinearGradient tileRimU = new LinearGradient(0, 0, 0, 1, Color.argb(56, 255, 255, 255), Color.argb(26, 255, 255, 255), Shader.TileMode.CLAMP);
    private final LinearGradient dockRimU = new LinearGradient(0, 0, 0, 1,
            new int[]{Color.argb(199, 255, 255, 255), Color.argb(66, 255, 255, 255), Color.argb(18, 255, 255, 255), Color.argb(10, 255, 255, 255), Color.argb(51, 255, 255, 255)},
            new float[]{0, .14f, .42f, .78f, 1}, Shader.TileMode.CLAMP);
    private final LinearGradient dockTintU = new LinearGradient(0, 0, 0, 1,
            new int[]{Color.argb(22, 255, 255, 255), Color.argb(9, 255, 255, 255), Color.argb(5, 255, 255, 255)}, new float[]{0, .5f, 1}, Shader.TileMode.CLAMP);
    private final LinearGradient dockSheenU = new LinearGradient(0, 0, 0, 1, Color.argb(18, 255, 255, 255), WHITE_0, Shader.TileMode.CLAMP);
    private final RadialGradient glowU = new RadialGradient(0, 0, 1, Color.WHITE, WHITE_0, Shader.TileMode.CLAMP);

    HomeView(Context c, Host host) {
        super(c);
        this.host = host;
        Ui.init(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        stroke.setStyle(Paint.Style.STROKE);
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(1.45f);
        glassPaint.setColorFilter(new ColorMatrixColorFilter(sat));

        clockP = Ui.text(Ui.medium, 40, Ui.TEXT, -.02f);
        clockP.setFontFeatureSettings("tnum");
        clockP.setTextAlign(Paint.Align.RIGHT);
        wxP = Ui.text(Ui.medium, 30, Ui.TEXT_2, -.01f);
        wxP.setFontFeatureSettings("tnum");
        wxP.setTextAlign(Paint.Align.RIGHT);
        headP = Ui.text(Ui.semibold, 25, Ui.TEXT, -.02f);
        headAppP = Ui.text(Ui.medium, 25, Ui.TEXT_2, -.01f);
        titleP = Ui.text(Ui.semibold, 22, Ui.TEXT, -.015f);
        metaP = Ui.text(Ui.medium, 20, Ui.TEXT_2, -.005f);
        labelP = Ui.text(Ui.semibold, 25, Ui.TEXT, -.015f);
        labelP.setTextAlign(Paint.Align.CENTER);
        hintP = Ui.text(Ui.medium, 20, Ui.TEXT_3, 0);
        hintP.setTextAlign(Paint.Align.CENTER);
        gridHeadP = Ui.text(Ui.semibold, 26, Ui.TEXT_2, -.02f);
        nameP = Ui.text(Ui.semibold, 23, Ui.TEXT, -.015f);
        nameP.setTextAlign(Paint.Align.CENTER);
        cardTitleP = Ui.text(Ui.bold, 30, Ui.TEXT, -.03f);
        toastP = Ui.text(Ui.medium, 22, Ui.TEXT, -.01f);
        toastP.setTextAlign(Paint.Align.CENTER);
        for (Paint p : new Paint[]{clockP, headP, headAppP, titleP, metaP, labelP, nameP, cardTitleP}) p.setShadowLayer(14, 0, 2, Color.argb(90, 0, 0, 0));

        float px = c.getResources().getDisplayMetrics().widthPixels / Ui.W * 1.07f;  // card size on screen, focus zoom included
        art = new ArtCache(c, Math.round(CARD_W * px), Math.round(CARD_H * px), this::invalidate);
        weather = new Weather(AppStore.usesFahrenheit(), this::invalidate);
    }

    // ---------------------------------------------------------------- data

    void setApps(AppStore a) {
        apps = a;
        rowI = Math.min(rowI, Math.max(0, a.row.size() - 1));
        gridI = Math.min(gridI, Math.max(0, a.rest.size() - 1));
        refreshShelf();
        invalidate();
    }

    void setShelf(ShelfStore sh) {
        shelfData = sh;
        art.forgetFailures();
        refreshShelf();
        invalidate();
    }

    float scale() { return s; }

    /** Called every 30 s by the activity: clock, weather, and the scene when the time-of-day palette changes. */
    void tick() {
        weather.refresh();
        Scene.Palette p = Scene.forTime(Calendar.getInstance());
        if (p != palette) renderScene(p);
        invalidate();
    }

    /** Home pressed while already home: back to the dock, nothing open. */
    void reset() {
        area = Area.ROW;
        appsTarget = 0;
        moving = false;
        refreshShelf();
        invalidate();
    }

    private void refreshShelf() {
        if (shelfData == null) { shelf = null; return; }
        AppStore.App focused = focusedRowApp();
        ShelfStore.Shelf next = shelfData.forApp(focused == null ? null : focused.pkg, focused == null ? null : focused.label);
        if (next != shelf) { shelf = next; cardI = 0; cardScroll = 0; }
    }

    private AppStore.App focusedRowApp() {
        return apps == null || apps.row.isEmpty() ? null : apps.row.get(Math.min(rowI, apps.row.size() - 1));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        s = w / Ui.W;
        renderScene(Scene.forTime(Calendar.getInstance()));
    }

    private void renderScene(Scene.Palette p) {
        if (getWidth() == 0 || sceneBusy) return;
        sceneBusy = true;
        final int w = getWidth(), h = getHeight();
        final String k = kind;
        new Thread(() -> {
            Bitmap sc = Scene.render(k, p, 42, w, h);
            Bitmap glass = Blur.blurred(sc, 240, 4), deep = Blur.blurred(sc, 120, 4);
            legibility(sc, Scene.isBright(p, k));
            post(() -> {
                scene = sc; glassBlur = glass; deepBlur = deep; palette = p; sceneBusy = false;
                shaders.clear();
                invalidate();
            });
        }, "scene").start();
    }

    // ---------------------------------------------------------------- input

    private boolean longFired;

    boolean onKey(int code, KeyEvent e) {
        lastKey = SystemClock.uptimeMillis();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_BUTTON_A:
                if (down) {
                    if (e.getRepeatCount() == 0) longFired = false;
                    else if (!longFired && e.getEventTime() - e.getDownTime() > 500) { longFired = true; longPress(); }
                } else if (!longFired) click();
                return true;
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE:
                if (!down) back();
                return true;
        }
        if (!down) return false;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: move(-1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: move(1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_UP: move(0, -1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: move(0, 1); return true;
        }
        return false;
    }

    private void move(int dx, int dy) {
        if (apps == null) return;
        List<AppStore.App> row = apps.row, rest = apps.rest;
        if (moving) {
            int j = rowI + dx;
            if (dx != 0 && j >= 0 && j < row.size()) { apps.swapInRow(rowI, j); rowI = j; }
        } else if (area == Area.ROW) {
            if (dx != 0 && !row.isEmpty()) {
                int n = Math.max(0, Math.min(row.size() - 1, rowI + dx));
                if (n != rowI) { rowI = n; glint(row.get(rowI)); refreshShelf(); }
            }
            if (dy < 0 && shelf != null && !shelf.items.isEmpty()) { area = Area.SHELF; cardI = 0; glint(shelf.items.get(0)); }
            if (dy > 0 && !rest.isEmpty()) { area = Area.GRID; gridI = Math.min(rowI, Math.min(4, rest.size() - 1)); appsTarget = 1; glint(rest.get(gridI)); }
        } else if (area == Area.SHELF) {
            int n = Math.max(0, Math.min(shelf.items.size() - 1, cardI + dx));
            if (n != cardI) { cardI = n; glint(shelf.items.get(cardI)); }
            if (dy > 0) area = Area.ROW;
        } else {
            int n = gridI + dx + dy * 5;
            if (dy < 0 && gridI < 5) { area = Area.ROW; appsTarget = 0; }
            else if (n >= 0 && n < rest.size() && (dx == 0 || n / 5 == gridI / 5)) { gridI = n; glint(rest.get(gridI)); }
        }
        invalidate();
    }

    private void click() {
        if (apps == null) return;
        if (moving) { moving = false; showToast("Saved"); invalidate(); return; }
        if (area == Area.ROW && !apps.row.isEmpty()) host.launch(apps.row.get(rowI));
        else if (area == Area.GRID && !apps.rest.isEmpty()) host.launch(apps.rest.get(gridI));
        else if (area == Area.SHELF && shelf != null) {
            ShelfStore.Item it = shelf.items.get(cardI);
            host.open(it, apps.find(it.pkg));
        }
    }

    /** Long-press OK. A full tile menu comes later; for now: move a Top Row tile, or pin an app from All Apps. */
    private void longPress() {
        if (apps == null) return;
        if (area == Area.ROW && apps.row.size() > 1) { moving = true; invalidate(); }
        else if (area == Area.GRID && !apps.rest.isEmpty()) {
            AppStore.App a = apps.rest.get(gridI);
            apps.addToRow(a);
            gridI = Math.max(0, Math.min(gridI, apps.rest.size() - 1));
            showToast(a.label + " added to Top Row");
            if (apps.rest.isEmpty()) { area = Area.ROW; appsTarget = 0; }
            invalidate();
        }
    }

    private void back() {
        if (moving) moving = false;
        else if (area != Area.ROW) { area = Area.ROW; appsTarget = 0; }
        invalidate();
    }

    private void glint(Object o) { glintOf = o; glintStart = SystemClock.uptimeMillis(); }

    private void showToast(String t) { toast = t; toastUntil = SystemClock.uptimeMillis() + 1800; }

    // ---------------------------------------------------------------- drawing

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        long dt = lastFrame == 0 ? 16 : Math.min(64, now - lastFrame);
        lastFrame = now;
        frameDt = dt;
        boolean anim = step(dt);

        c.save();
        c.scale(s, s);
        drawBackground(c);
        drawTopBar(c);
        float pageY = -APPS_SHIFT * appsT;
        c.save();
        c.translate(0, pageY);
        if (appsT < 1) drawShelf(c, 1 - appsT);
        drawDock(c, pageY);
        if (appsT > 0) drawGrid(c, appsT);
        c.restore();
        drawHint(c, now);
        drawToast(c, now);
        c.restore();

        if (anim || now - glintStart < 1000 || (toast != null && now < toastUntil + 300) || moving) postInvalidateOnAnimation();
    }

    /** Advances every animated value toward its target; returns true while anything is still moving. */
    private boolean step(long dt) {
        boolean a = false;
        float t = Ui.approach(appsT, appsTarget, dt, 110);
        a |= t != appsT; appsT = t;
        if (apps != null) {
            int n = (int) Math.min(apps.row.size(), ROW_MAX);
            float rowTarget = Math.max(0, Math.min(rowI - (n - 2), apps.row.size() - n)) * (TILE_W + GAP);
            t = Ui.approach(rowScroll, area == Area.ROW ? rowTarget : rowScroll, dt, 90); a |= t != rowScroll; rowScroll = t;
            t = Ui.approach(gridScroll, Math.max(0, gridI / 5 - 1) * GRID_STEP_Y, dt, 90); a |= t != gridScroll; gridScroll = t;
            for (int i = 0; i < apps.row.size(); i++) a |= ease(apps.row.get(i), area == Area.ROW && i == rowI, dt);
            for (int i = 0; i < apps.rest.size(); i++) a |= ease(apps.rest.get(i), area == Area.GRID && i == gridI, dt);
        }
        if (shelf != null) {
            t = Ui.approach(cardScroll, Math.max(0, cardI - 3) * (CARD_W + GAP), dt, 90); a |= t != cardScroll; cardScroll = t;
            for (int i = 0; i < shelf.items.size(); i++) a |= ease(shelf.items.get(i), area == Area.SHELF && i == cardI, dt);
        }
        return a;
    }

    private boolean ease(Object key, boolean focused, long dt) {
        Float v = focusAmt.get(key);
        float cur = v == null ? 0 : v, n = Ui.approach(cur, focused ? 1 : 0, dt, 70);
        if (n == cur) return false;
        focusAmt.put(key, n);
        return true;
    }

    private float focusOf(Object key) { Float v = focusAmt.get(key); return v == null ? 0 : v; }

    private void drawBackground(Canvas c) {
        r.set(0, 0, Ui.W, Ui.H);
        if (scene == null) { c.drawColor(Color.BLACK); return; }
        bmp.setAlpha(255);
        c.drawBitmap(scene, null, r, bmp);
        if (appsT > 0) {  // All Apps: the scene blurs and dims to half
            bmp.setAlpha(Math.round(255 * appsT));
            c.drawBitmap(deepBlur, null, r, bmp);
            bmp.setAlpha(255);
            fill.setShader(null);
            fill.setColor(Color.argb(Math.round(128 * appsT), 0, 0, 0));
            c.drawRect(r, fill);
        }
    }

    /**
     * Bakes the legibility gradients into the scene bitmap once: a corner behind the clock, the bottom third, and
     * a band behind the shelf that is stronger on bright daytime scenes. Saves three full-screen passes per frame.
     */
    private static void legibility(Bitmap b, boolean bright) {
        Canvas c = new Canvas(b);
        c.scale(b.getWidth() / Ui.W, b.getHeight() / Ui.H);
        Paint p = new Paint();
        p.setShader(new RadialGradient(Ui.W, 0, 760, Color.argb(77, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, Ui.W, Ui.H, p);
        p.setShader(new LinearGradient(0, Ui.H, 0, Ui.H * .45f, Color.argb(97, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, Ui.W, Ui.H, p);
        int band = Color.argb(Math.round(255 * (bright ? .34f : .16f)), 0, 0, 0);
        p.setShader(new LinearGradient(0, Ui.H * .14f, 0, Ui.H * .66f, new int[]{0, band, band, 0}, new float[]{0, .23f, .81f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, Ui.W, Ui.H, p);
    }

    private void drawTopBar(Canvas c) {
        Calendar now = Calendar.getInstance();
        String time = DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "H:mm" : "h:mm", now).toString();
        float right = Ui.W - 104, base = 98;
        c.drawText(time, right, base, clockP);
        if (!weather.ready()) return;
        float x = right - clockP.measureText(time) - 22;
        fill.setColor(Color.argb(56, 255, 255, 255));
        c.drawRect(x - 1.5f, base - 30, x, base, fill);
        x -= 22;
        String t = weather.temp + "°";
        c.drawText(t, x, base - 2, wxP);
        x -= wxP.measureText(t) + 10 + 17;
        Glyphs.weather(c, weather.kind, x, base - 13, 17, fill, stroke);
    }

    private void drawShelf(Canvas c, float alpha) {
        if (shelf == null || shelf.items.isEmpty()) return;
        int a = Math.round(255 * alpha);
        headP.setAlpha(Math.round(Color.alpha(Ui.TEXT) * alpha));
        headAppP.setAlpha(Math.round(Color.alpha(Ui.TEXT_2) * alpha));
        String suffix = shelf.app != null ? " · " + shelf.app : " · from your apps";
        c.drawText(shelf.heading, LEFT, SHELF_TOP + 25, headP);
        c.drawText(suffix, LEFT + headP.measureText(shelf.heading), SHELF_TOP + 25, headAppP);
        c.save();
        c.clipRect(0, SHELF_TOP + 30, Ui.W, SHELF_TOP + 380);
        for (int i = 0; i < shelf.items.size(); i++) {
            float x = LEFT + i * (CARD_W + GAP) - cardScroll;
            if (x > Ui.W || x + CARD_W < 0) continue;
            drawCard(c, shelf.items.get(i), x, CARD_TOP, a);
        }
        c.restore();
    }

    private void drawCard(Canvas c, ShelfStore.Item it, float x, float y, int alpha) {
        float f = focusOf(it), sc = 1 + .07f * f;
        float cx = x + CARD_W / 2, cy = y + CARD_H / 2, w = CARD_W * sc, h = CARD_H * sc;
        r.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        shadow(c, r, R_TILE * sc, 24, Ui.lerp(.45f, .7f, f) * alpha / 255f, Ui.lerp(8, 20, f));
        Bitmap b = art.get(it.art);
        if (b != null) {
            drawBitmapRound(c, b, r, R_TILE * sc, alpha);
        } else {
            fill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, Color.parseColor("#2c2f3a"), Color.parseColor("#15161c"), Shader.TileMode.CLAMP));
            fill.setAlpha(alpha);
            c.drawRoundRect(r, R_TILE * sc, R_TILE * sc, fill);
            fill.setShader(null);
            cardTitleP.setAlpha(alpha);
            c.save();
            c.clipRect(r);
            c.drawText(ellipsize(it.title, cardTitleP, w - 44), r.left + 22, r.bottom - (it.progress > 0 ? 34 : 22), cardTitleP);  // no artwork yet
            c.restore();
        }
        tileFinish(c, r, R_TILE * sc, f, it, alpha);
        if (it.progress > 0) {
            float bl = r.left + 22 * sc, br = r.right - 22 * sc, by = r.bottom - 12 * sc;
            fill.setColor(Color.argb(Math.round(.28f * alpha), 255, 255, 255));
            c.drawRoundRect(bl, by - 5, br, by, 3, 3, fill);
            fill.setColor(Color.argb(alpha, 255, 255, 255));
            c.drawRoundRect(bl, by - 5, bl + (br - bl) * it.progress, by, 3, 3, fill);
        }
        // title, then the episode/time line
        float ty = y + CARD_H + 34 + 8 * f;
        titleP.setAlpha(Math.round(Color.alpha(Ui.TEXT) * alpha / 255f));
        c.drawText(ellipsize(it.title, titleP, CARD_W - 8), x + 4, ty, titleP);
        if (it.line != null && !it.line.isEmpty()) {
            metaP.setColor(Ui.mix(Ui.TEXT_2, Ui.TEXT, f * .5f));
            metaP.setAlpha(Math.round(Color.alpha(metaP.getColor()) * alpha / 255f));
            c.drawText(ellipsize(it.line, metaP, CARD_W - 8), x + 4, ty + 28, metaP);
        }
    }

    private void drawDock(Canvas c, float pageY) {
        if (apps == null) return;
        List<AppStore.App> row = apps.row;
        int n = (int) Math.max(1, Math.min(row.size(), ROW_MAX));
        float dockW = n * TILE_W + (n - 1) * GAP + 2 * PAD, dockH = TILE_H + 2 * PAD;
        float l = (Ui.W - dockW) / 2, t = DOCK_BOTTOM - dockH;
        r.set(l, t, l + dockW, t + dockH);

        // shadow, then glass: the pre-blurred scene behind the dock, saturated, tinted and lit from above
        shadow(c, r, R_DOCK, 60, .55f, 30);
        if (glassBlur != null) {
            Bitmap gb = appsT > 0 ? deepBlur : glassBlur;
            BitmapShader gs = shaderFor(gb);
            srcRect.set(0, 0, gb.getWidth(), gb.getHeight());
            screenRect.set(0, -pageY, Ui.W, Ui.H - pageY);  // the glass shows what is on screen behind it
            m.setRectToRect(srcRect, screenRect, Matrix.ScaleToFit.FILL);
            gs.setLocalMatrix(m);
            glassPaint.setShader(gs);
            c.drawRoundRect(r, R_DOCK, R_DOCK, glassPaint);
            glassPaint.setShader(null);
            if (appsT > 0) { fill.setColor(Color.argb(Math.round(128 * appsT), 0, 0, 0)); c.drawRoundRect(r, R_DOCK, R_DOCK, fill); }
        }
        fill.setColor(Color.WHITE);
        unit(fill, dockTintU, t, dockH, 255);
        c.drawRoundRect(r, R_DOCK, R_DOCK, fill);
        unit(fill, dockSheenU, t, dockH * .42f, 255);
        c.drawRoundRect(r, R_DOCK, R_DOCK, fill);
        fill.setShader(null);
        c.save();
        c.clipRect(r);  // tiles scrolled past the ends stay inside the dock

        // tiles
        float x0 = l + PAD - rowScroll;
        int focusedIdx = area == Area.ROW ? rowI : -1;
        for (int i = 0; i < row.size(); i++) {
            float x = x0 + i * (TILE_W + GAP);
            if (x > l + dockW || x + TILE_W < l || i == focusedIdx) continue;  // r is reused by drawTile, so use the dock's own bounds
            drawTile(c, row.get(i), x, t + PAD, TILE_W, TILE_H, false);
        }
        if (focusedIdx >= 0 && focusedIdx < row.size()) drawTile(c, row.get(focusedIdx), x0 + focusedIdx * (TILE_W + GAP), t + PAD, TILE_W, TILE_H, moving);
        c.restore();

        // specular rim, plus the highlight that slides to sit above the focused tile
        r.set(l, t, l + dockW, t + dockH);
        rim(c, r, R_DOCK, 1);
        float target = area == Area.ROW && !row.isEmpty() ? x0 + rowI * (TILE_W + GAP) + TILE_W / 2 : l + dockW / 2;
        specX = specX < 0 ? target : Ui.approach(specX, target, frameDt, 95);  // glides ~320 ms
        if (specX != target) postInvalidateOnAnimation();
        float specA = 1 - .65f * appsT;
        glow(c, r, R_DOCK, specX, t + dockH * .006f, 198, dockH * .032f, Math.round(242 * specA));
        glow(c, r, R_DOCK, specX, t, 239, dockH * .42f, Math.round(51 * specA));

        // focused app's name under the dock
        if (focusedIdx >= 0 && focusedIdx < row.size() && appsT < 1) {
            float f = focusOf(row.get(focusedIdx)) * (1 - appsT);
            labelP.setAlpha(Math.round(Color.alpha(Ui.TEXT) * f));
            float lx = Math.max(LEFT, Math.min(Ui.W - LEFT, x0 + focusedIdx * (TILE_W + GAP) + TILE_W / 2));
            c.drawText(row.get(focusedIdx).label, lx, t + dockH + 22 + 25, labelP);
        }
    }

    private void drawGrid(Canvas c, float alpha) {
        List<AppStore.App> rest = apps.rest;
        gridHeadP.setAlpha(Math.round(Color.alpha(Ui.TEXT_2) * alpha));
        c.drawText("All Apps", LEFT, 966 + 26 - gridScroll, gridHeadP);
        c.save();
        c.clipRect(0, DOCK_BOTTOM + 30, Ui.W, Ui.H + APPS_SHIFT);
        for (int i = 0; i < rest.size(); i++) {
            float x = LEFT + (i % 5) * (TILE_W + GAP), y = GRID_TOP + (i / 5) * GRID_STEP_Y - gridScroll;
            if (y > Ui.H + APPS_SHIFT || y + TILE_H < DOCK_BOTTOM) continue;
            AppStore.App a = rest.get(i);
            drawTile(c, a, x, y, TILE_W, TILE_H, false, alpha);
            float f = focusOf(a);
            if (f > 0.01f) {
                nameP.setAlpha(Math.round(255 * f * alpha));
                c.drawText(a.label, x + TILE_W / 2, y + TILE_H + 16 + 23 + 6 * f, nameP);
            }
        }
        c.restore();
    }

    private void drawTile(Canvas c, AppStore.App a, float x, float y, float w, float h, boolean wiggle) {
        drawTile(c, a, x, y, w, h, wiggle, 1);
    }

    private void drawTile(Canvas c, AppStore.App a, float x, float y, float w, float h, boolean wiggle, float alpha) {
        float f = focusOf(a), sc = 1 + .09f * f;
        float cx = x + w / 2, cy = y + h / 2, tw = w * sc, th = h * sc;
        int al = Math.round(255 * alpha);
        c.save();
        if (wiggle) c.rotate((float) Math.sin(SystemClock.uptimeMillis() / 130.0) * 1.0f, cx, cy);
        r.set(cx - tw / 2, cy - th / 2, cx + tw / 2, cy + th / 2);
        shadow(c, r, R_TILE * sc, 22, Ui.lerp(.3f, .65f, f) * alpha, Ui.lerp(5, 18, f));
        if (a.tile != null) drawBitmapRound(c, a.tile, r, R_TILE * sc, al);
        tileFinish(c, r, R_TILE * sc, f, a, al);
        c.restore();
    }

    /** Top-light sheen, hairline rim and the one-time glint that sweeps a newly focused tile. */
    private void tileFinish(Canvas c, RectF rr, float radius, float f, Object key, int alpha) {
        if (f > 0.01f) {
            unit(fill, sheenU, rr.top, rr.height() * .45f, Math.round(255 * f * alpha / 255f));
            c.drawRoundRect(rr, radius, radius, fill);
            fill.setShader(null);
            fill.setAlpha(255);
        }
        stroke.setStrokeWidth(1.2f);
        unit(stroke, tileRimU, rr.top, 18, alpha);
        c.drawRoundRect(rr.left + .6f, rr.top + .6f, rr.right - .6f, rr.bottom - .6f, radius, radius, stroke);
        stroke.setShader(null);
        stroke.setAlpha(255);
        if (key == glintOf) {
            float t = (SystemClock.uptimeMillis() - glintStart - 60) / 900f;
            if (t > 0 && t < 1) {
                float env = t < .3f ? t / .3f : 1 - (t - .3f) / .7f, e = Ui.easeOut(t);
                float bx = Ui.lerp(rr.left - rr.width() * .6f, rr.right + rr.width() * .6f, e);
                int a1 = Math.round(66 * env * alpha / 255f), a2 = Math.round(20 * env * alpha / 255f);
                fill.setShader(new LinearGradient(bx - rr.width() * .3f, rr.top, bx + rr.width() * .1f, rr.bottom,
                        new int[]{0, Color.argb(a1, 255, 255, 255), Color.argb(a2, 255, 255, 255), 0}, new float[]{0, .45f, .6f, 1}, Shader.TileMode.CLAMP));
                c.drawRoundRect(rr, radius, radius, fill);
                fill.setShader(null);
            }
        }
    }

    private void drawBitmapRound(Canvas c, Bitmap b, RectF rr, float radius, int alpha) {
        BitmapShader sh = shaderFor(b);
        srcRect.set(0, 0, b.getWidth(), b.getHeight());
        m.setRectToRect(srcRect, rr, Matrix.ScaleToFit.FILL);
        sh.setLocalMatrix(m);
        bmp.setShader(sh);
        bmp.setAlpha(alpha);
        c.drawRoundRect(rr, radius, radius, bmp);
        bmp.setShader(null);
        bmp.setAlpha(255);
    }

    /** The specular rim: bright where light hits the top edge, a faint bounce at the bottom. */
    private void rim(Canvas c, RectF rr, float radius, float alpha) {
        stroke.setStrokeWidth(1.5f);
        unit(stroke, dockRimU, rr.top, rr.height(), Math.round(255 * alpha));
        c.drawRoundRect(rr.left + .75f, rr.top + .75f, rr.right - .75f, rr.bottom - .75f, radius, radius, stroke);
        stroke.setShader(null);
        stroke.setAlpha(255);
    }

    /** A soft white elliptical glow at (cx, cy) with radii rx, ry, painted only inside the rounded rect `in`. */
    private void glow(Canvas c, RectF in, float radius, float cx, float cy, float rx, float ry, int alpha) {
        sm.setScale(rx, Math.max(.5f, ry));
        sm.postTranslate(cx, cy);
        glowU.setLocalMatrix(sm);
        fill.setShader(glowU);
        fill.setAlpha(alpha);
        c.drawRoundRect(in, radius, radius, fill);
        fill.setShader(null);
        fill.setAlpha(255);
    }

    /** Points a 0..1 vertical unit gradient at [top, top + height] and sets it on the paint. */
    private void unit(Paint p, Shader sh, float top, float height, int alpha) {
        sm.setScale(1, Math.max(1, height));
        sm.postTranslate(0, top);
        sh.setLocalMatrix(sm);
        p.setShader(sh);
        p.setAlpha(alpha);
    }

    private BitmapShader shaderFor(Bitmap b) {
        BitmapShader sh = shaders.get(b);
        if (sh == null) shaders.put(b, sh = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        return sh;
    }

    private void shadow(Canvas c, RectF rr, float radius, float blur, float opacity, float dy) {
        Bitmap sh = Ui.shadow(rr.width(), rr.height(), radius, blur);
        bmp.setAlpha(Math.round(255 * Ui.clamp(opacity, 0, 1)));
        c.drawBitmap(sh, null, new RectF(rr.left - blur, rr.top - blur + dy, rr.right + blur, rr.bottom + blur + dy), bmp);
        bmp.setAlpha(255);
    }

    private void drawHint(Canvas c, long now) {
        float idle = (now - lastKey) / 1000f;
        float a = (area == Area.ROW && !moving && apps != null && !apps.rest.isEmpty() ? 1 : 0) * Ui.clamp(4.6f - idle, 0, 1) * (1 - appsT);
        if (a <= 0) { if (idle < 5) postInvalidateDelayed(250); return; }
        hintP.setAlpha(Math.round(Color.alpha(Ui.TEXT_3) * a));
        float y = Ui.H - 38;
        c.drawText("All Apps", Ui.W / 2 + 14, y, hintP);
        stroke.setStrokeWidth(2.2f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(Ui.withAlpha(Ui.TEXT_3, a));
        float hx = Ui.W / 2 - hintP.measureText("All Apps") / 2 - 4, hy = y - 8;
        c.drawLine(hx - 6, hy - 3, hx, hy + 3, stroke);
        c.drawLine(hx, hy + 3, hx + 6, hy - 3, stroke);
        postInvalidateDelayed(250);
    }

    private void drawToast(Canvas c, long now) {
        String msg = moving ? "◀ ▶  Move      OK  Done" : toast;
        if (msg == null) return;
        float a = moving ? 1 : Ui.clamp((toastUntil - now) / 300f, 0, 1);
        if (a <= 0) { toast = null; return; }
        float w = toastP.measureText(msg) + 56, cx = Ui.W / 2, y = Ui.H - 40 - 54;
        r.set(cx - w / 2, y, cx + w / 2, y + 54);
        fill.setColor(Color.argb(Math.round(200 * a), 34, 34, 42));
        c.drawRoundRect(r, 27, 27, fill);
        rim(c, r, 27, .6f * a);
        toastP.setAlpha(Math.round(255 * a));
        c.drawText(msg, cx, y + 35, toastP);
    }

    private static String ellipsize(String s, Paint p, float max) {
        if (p.measureText(s) <= max) return s;
        int n = p.breakText(s, true, max - p.measureText("…"), null);
        return s.substring(0, Math.max(0, n)).trim() + "…";
    }
}
