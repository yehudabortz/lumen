package dev.lumen.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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
 * clock, the Top Shelf, the glass dock and All Apps, with the tile menu and settings on top (Overlays).
 * Layout numbers follow docs/design/DESIGN.md.
 */
final class HomeView extends View implements Overlays.Host {
    interface Host {
        void launch(AppStore.App app);
        void open(ShelfStore.Item item, AppStore.App fallback);
        void appInfo(String pkg);
        void reloadApps();
        void chooseLocation(Runnable changed);
        void screensaver();
    }

    // ---- Layout (design px) ----
    private static final float PAD = 40, GAP = 42, TILE_W = AppStore.TILE_W, TILE_H = AppStore.TILE_H;
    private static final float DOCK_W = Ui.W - 2 * Ui.SAFE_X, LEFT = Ui.SAFE_X + PAD;  // ROW_MAX tiles fill the dock exactly
    // The dock's outer edges: the shelf, headings and text start and end here, like the dock itself
    private static final float EDGE_L = Ui.SAFE_X, EDGE_R = Ui.W - Ui.SAFE_X;
    // Tiles and cards use Apple's 60% corner smoothing; the dock's outline is the tiles' outline grown by PAD
    private static final float R_TILE = 22, TILE_SMOOTH = .6f, R_DOCK = R_TILE + PAD, DOCK_SMOOTH = concentric(R_TILE, TILE_SMOOTH, PAD);
    // Vertical anchors: the dock sits DOCK_MARGIN above the screen's real bottom edge (room for the focused app's
    // name and the hint/credit row), the Top Shelf hangs from SHELF_TOP under the clock, and in All Apps the dock
    // rises to APPS_DOCK_TOP. Heights follow the view, so any TV aspect keeps the dock at the bottom.
    private static final float DOCK_MARGIN = 128, SHELF_TOP = 190, APPS_DOCK_TOP = 86, DOCK_H = TILE_H + 2 * PAD, ROW_MAX = 5;
    private static final float CARD_W_MAX = 548, CARD_H_MAX = CARD_W_MAX * 9 / 16;
    private float cardW, cardH, shelfTop, cardTop;  // set by layoutShelf() from the Card size setting
    private static final float GRID_STEP_Y = TILE_H + 84;
    private static final long HEARTBEAT_MS = 3400;
    private static final long HEARTBEAT_SETTLE_MS = 700;

    private enum Area { ROW, SHELF, GRID, STATUS }  // STATUS: the clock-and-weather pill (settings)
    private static final Object STATUS_KEY = new Object();
    private Area statusFrom = Area.ROW;

    private final Host host;
    private final Prefs prefs;
    private final SharedPreferences focusPrefs;
    private final Overlays overlays;
    private float s = 1, vh = Ui.H;  // screen px per design px; the view's height in design px (1080 at 16:9)

    // Data
    private AppStore apps;
    private ShelfStore shelfData;
    private ShelfStore.Shelf shelf;
    private final ArtCache art;
    private final Weather weather;

    // Scene
    private Scene.Palette palette;
    private Bitmap scene, glassBlur, deepBlur;
    private boolean sceneBusy, sceneAgain;
    private String photoCredit;  // set while the background is a NASA photo
    private long photoAt;

    // Focus and motion: springs carry velocity, so changing focus mid-move bends smoothly
    private Area area = Area.ROW;
    private int rowI, cardI, gridI;
    private boolean moving;
    private final Spring appsS = new Spring(1.9f, .95f), rowScroll = new Spring(2.3f, 1f), cardScroll = new Spring(2.3f, 1f),
            gridScroll = new Spring(2.3f, 1f), spec = new Spring(2.1f, .88f), barHide = new Spring(2.1f, 1f);
    private final Map<Object, Spring> focus = new HashMap<>();
    private boolean returningHome = true, heartbeatArmed;
    private long lastFrame, lastKey, toastUntil, heartbeatEpoch = SystemClock.uptimeMillis();
    private String toast, clockText;
    private int clockMinute = -1;

    // Paints and reusable geometry (nothing is allocated per frame on the common path)
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), glassPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint creditP, clockP, dateP, wxP, headP, headAppP, titleP, metaP, labelP, labelDimP, hintP, gridHeadP, nameP, cardTitleP, toastP;
    private final RectF r = new RectF(), screenRect = new RectF(), srcRect = new RectF(), dock = new RectF();
    private final Matrix m = new Matrix(), sm = new Matrix();
    private final Map<Bitmap, BitmapShader> shaders = new HashMap<>();
    private static final int WHITE_0 = 0x00FFFFFF;
    private final LinearGradient sheenU = new LinearGradient(0, 0, 0, 1,
            new int[]{Color.argb(18, 255, 255, 255), Color.argb(9, 255, 255, 255), WHITE_0},
            new float[]{0, .48f, 1}, Shader.TileMode.CLAMP);
    private final LinearGradient dockRimU = new LinearGradient(0, 0, 0, 1,
            new int[]{Color.argb(199, 255, 255, 255), Color.argb(66, 255, 255, 255), Color.argb(18, 255, 255, 255), Color.argb(10, 255, 255, 255), Color.argb(51, 255, 255, 255)},
            new float[]{0, .14f, .42f, .78f, 1}, Shader.TileMode.CLAMP);
    private final LinearGradient dockTintU = new LinearGradient(0, 0, 0, 1,
            new int[]{Color.argb(22, 255, 255, 255), Color.argb(9, 255, 255, 255), Color.argb(5, 255, 255, 255)}, new float[]{0, .5f, 1}, Shader.TileMode.CLAMP);
    private final LinearGradient dockSheenU = new LinearGradient(0, 0, 0, 1, Color.argb(18, 255, 255, 255), WHITE_0, Shader.TileMode.CLAMP);
    private final LinearGradient scrollFade = new LinearGradient(0, 0, 0, 270,
            new int[]{Color.argb(42, 255, 255, 255), Color.argb(26, 255, 255, 255), Color.argb(12, 255, 255, 255), 0},
            new float[]{0, .38f, .7f, 1}, Shader.TileMode.CLAMP);
    private final Paint scrollMask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RadialGradient glowU = new RadialGradient(0, 0, 1, Color.WHITE, WHITE_0, Shader.TileMode.CLAMP);

    HomeView(Context c, Host host) {
        super(c);
        this.host = host;
        Ui.init(c);
        prefs = new Prefs(c);
        layoutShelf();
        focusPrefs = c.getSharedPreferences("lumen", Context.MODE_PRIVATE);
        overlays = new Overlays(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setDither(true);
        fill.setDither(true);
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(1.45f);
        glassPaint.setColorFilter(new ColorMatrixColorFilter(sat));

        creditP = Ui.text(Ui.medium, 17, Ui.TEXT_3, 0);
        creditP.setTextAlign(Paint.Align.RIGHT);
        creditP.setShadowLayer(10, 0, 1, Color.argb(110, 0, 0, 0));
        clockP = Ui.text(Ui.medium, 40, Ui.TEXT, -.02f);
        clockP.setFontFeatureSettings("tnum");
        clockP.setTextAlign(Paint.Align.RIGHT);
        dateP = Ui.text(Ui.medium, 21, Ui.TEXT_2, 0);
        dateP.setTextAlign(Paint.Align.RIGHT);
        wxP = Ui.text(Ui.medium, 30, Ui.TEXT_2, -.01f);
        wxP.setFontFeatureSettings("tnum");
        wxP.setTextAlign(Paint.Align.RIGHT);
        headP = Ui.text(Ui.semibold, 25, Ui.TEXT, -.02f);
        headAppP = Ui.text(Ui.medium, 25, Ui.TEXT_2, -.01f);
        titleP = Ui.text(Ui.semibold, 22, Ui.TEXT, -.015f);
        metaP = Ui.text(Ui.medium, 20, Ui.TEXT_2, -.005f);
        labelP = Ui.text(Ui.semibold, 25, Ui.TEXT, -.015f);
        labelP.setTextAlign(Paint.Align.CENTER);
        labelDimP = Ui.text(Ui.medium, 23, Ui.TEXT_2, -.01f);
        labelDimP.setTextAlign(Paint.Align.CENTER);
        hintP = Ui.text(Ui.medium, 20, Ui.TEXT_3, 0);
        hintP.setTextAlign(Paint.Align.LEFT);
        gridHeadP = Ui.text(Ui.semibold, 26, Ui.TEXT_2, -.02f);
        nameP = Ui.text(Ui.semibold, 23, Ui.TEXT, -.015f);
        nameP.setTextAlign(Paint.Align.CENTER);
        cardTitleP = Ui.text(Ui.bold, 30, Ui.TEXT, -.03f);
        toastP = Ui.text(Ui.medium, 22, Ui.TEXT, -.01f);
        toastP.setTextAlign(Paint.Align.CENTER);
        scrollMask.setShader(new LinearGradient(0, 0, 0, 270,
                new int[]{Color.WHITE, Color.WHITE, 0}, new float[]{0, .48f, 1}, Shader.TileMode.CLAMP));
        scrollMask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        for (Paint p : new Paint[]{clockP, dateP, headP, headAppP, titleP, metaP, labelP, labelDimP, nameP, cardTitleP}) p.setShadowLayer(14, 0, 2, Color.argb(90, 0, 0, 0));

        float px = c.getResources().getDisplayMetrics().widthPixels / Ui.W * 1.07f;  // card size on screen, focus zoom included
        art = new ArtCache(c, Math.round(CARD_W_MAX * px), Math.round(CARD_H_MAX * px), this::invalidate);  // largest card size
        weather = new Weather(prefs, this::invalidate);
    }

    // ---------------------------------------------------------------- data

    void setApps(AppStore a) {
        apps = a;
        heartbeatEpoch = SystemClock.uptimeMillis() + HEARTBEAT_SETTLE_MS;
        String focusedPackage = focusPrefs.getString("homeFocusPackage", null);
        if (focusedPackage != null) {
            for (int i = 0; i < a.row.size(); i++) if (focusedPackage.equals(a.row.get(i).pkg)) { rowI = i; break; }
            if (rowI >= a.row.size() || !focusedPackage.equals(a.row.get(rowI).pkg))
                focusPrefs.edit().remove("homeFocusPackage").apply();
        }
        rowI = Math.min(rowI, Math.max(0, a.row.size() - 1));
        gridI = Math.min(gridI, Math.max(0, a.rest.size() - 1));
        if (a.row.isEmpty() && area == Area.ROW && !a.rest.isEmpty()) { area = Area.GRID; appsS.target = 1; }
        refreshShelf();
        pruneFocus();
        if (returningHome) { settleFocus(); returningHome = false; }
        seedCurrentFocus();
        invalidate();
    }

    void setShelf(ShelfStore sh) {
        shelfData = sh;
        art.forgetFailures();
        refreshShelf();
        pruneFocus();
        seedCurrentFocus();
        invalidate();
    }

    /** Called every 30 s by the activity: clock, weather, and the scene when the time-of-day palette changes. */
    void tick() {
        if (prefs.is(Prefs.WEATHER, "on")) weather.refresh();
        if (prefs.is(Prefs.BACKGROUND, "nasa")) {
            long every = prefs.is(Prefs.PHOTOS, "day") ? 24 * 3600_000L : 3600_000L;
            if (SystemClock.uptimeMillis() - photoAt > (photoCredit == null ? 60_000L : every)) renderScene();
        } else if (wantedPalette() != palette) renderScene();
        invalidate();
    }

    /** Home pressed while already home: back to the dock, nothing open. */
    void reset() {
        if (overlays.isOpen()) overlays.close();
        area = Area.ROW;
        heartbeatEpoch = SystemClock.uptimeMillis() + HEARTBEAT_SETTLE_MS;
        appsS.target = 0;
        moving = false;
        refreshShelf();
        invalidate();
    }

    void hostWindowFocusChanged(boolean hasFocus) {
        prepareHomeEntry();
        if (!hasFocus) returningHome = true;
    }

    /** Returning Home restores an already focused tile; only D-pad navigation should animate selection. */
    void prepareHomeEntry() {
        returningHome = true;
        heartbeatArmed = false;
        heartbeatEpoch = SystemClock.uptimeMillis() + HEARTBEAT_SETTLE_MS;
        lastFrame = 0;
        settleFocus();
        invalidate();
    }

    private Object focusKey(Object key) {
        if (key instanceof AppStore.App) return ((AppStore.App) key).pkg;
        if (key instanceof ShelfStore.Item) return ((ShelfStore.Item) key).focusKey();
        return key;
    }

    private Object selectedFocusKey() {
        if (area == Area.STATUS) return STATUS_KEY;
        if (area == Area.ROW && apps != null && !apps.row.isEmpty()) return focusKey(apps.row.get(rowI));
        if (area == Area.GRID && apps != null && !apps.rest.isEmpty()) return focusKey(apps.rest.get(gridI));
        if (area == Area.SHELF && shelf != null && !shelf.items.isEmpty()) return focusKey(shelf.items.get(cardI));
        return null;
    }

    private void seedCurrentFocus() {
        Object selected = selectedFocusKey();
        if (selected != null && !focus.containsKey(selected)) focus.put(selected, new Spring(2.8f, 1f).at(1));
    }

    private void settleFocus() {
        Object selected = selectedFocusKey();
        for (Map.Entry<Object, Spring> entry : focus.entrySet()) entry.getValue().at(entry.getKey().equals(selected) ? 1 : 0);
        seedCurrentFocus();
        spec.at(0);  // The first frame places the dock light directly above the restored selection.
    }

    private void pruneFocus() {
        java.util.Set<Object> present = new java.util.HashSet<>();
        present.add(STATUS_KEY);
        if (apps != null) for (AppStore.App app : apps.all) present.add(focusKey(app));
        if (shelf != null) for (ShelfStore.Item item : shelf.items) present.add(focusKey(item));
        focus.keySet().retainAll(present);
    }

    private void refreshShelf() {
        if (shelfData == null) { shelf = null; return; }
        java.util.Set<String> excluded = prefs.set(Prefs.SHELF_OFF);
        excluded.addAll(prefs.set(Prefs.HIDDEN));
        Object selected = area == Area.SHELF ? selectedFocusKey() : null;
        ShelfStore.Shelf next = shelfData.forHome(prefs.get(Prefs.SHELF), excluded);
        if (next != shelf) {
            shelf = next;
            cardI = 0;
            if (selected != null && shelf != null) for (int i = 0; i < shelf.items.size(); i++)
                if (selected.equals(focusKey(shelf.items.get(i)))) { cardI = i; break; }
            if (cardI == 0) cardScroll.at(0);
            if (area == Area.SHELF && shelf == null) area = Area.ROW;
        }
    }

    private boolean reduced() { return prefs.is(Prefs.MOTION, "reduced"); }

    private Scene.Palette wantedPalette() {
        return prefs.is(Prefs.TIME_OF_DAY, "on") ? Scene.forTime(Calendar.getInstance()) : Scene.DUSK;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        s = w / Ui.W;
        vh = h / s;
        overlays.screenHeight(vh);
        layoutShelf();
        renderScene();
    }

    /** Renders the scene, its two blurred copies and the baked-in legibility gradients off the main thread. */
    private void renderScene() {
        if (getWidth() == 0) return;
        if (sceneBusy) { sceneAgain = true; return; }
        sceneBusy = true;
        final int w = Math.min(1920, getWidth()), h = Math.round(w * getHeight() / (float) getWidth());
        final String k = prefs.get(Prefs.BACKGROUND);
        final Scene.Palette p = wantedPalette();
        final long every = prefs.is(Prefs.PHOTOS, "day") ? 24 * 3600_000L : 3600_000L;
        new Thread(() -> {
            Bitmap sc = null;
            String credit = null;
            boolean bright;
            if ("nasa".equals(k)) {
                Nasa.Photo ph = Nasa.photo(getContext(), "home", w, h, every, () -> {
                    if (isAttachedToWindow() && prefs.is(Prefs.BACKGROUND, "nasa")) renderScene();
                });
                if (ph != null) { sc = ph.bitmap; credit = ph.credit; }
            }
            if (sc == null) sc = Scene.render("nasa".equals(k) ? "alpine" : k, p, 42, w, h);
            Bitmap glass = Blur.blurred(sc, 240, 4), deep = Blur.blurred(sc, 120, 4);
            bright = credit != null ? luminance(deep) > .5f : Scene.isBright(p, k);
            legibility(sc, bright);
            final Bitmap fsc = sc;
            final String fcredit = credit;
            post(() -> {
                if (!k.equals(prefs.get(Prefs.BACKGROUND))) {
                    fsc.recycle(); glass.recycle(); deep.recycle();
                    sceneBusy = false; sceneAgain = false; renderScene();
                    return;
                }
                scene = fsc; glassBlur = glass; deepBlur = deep; palette = p; photoCredit = fcredit; sceneBusy = false;
                photoAt = SystemClock.uptimeMillis();
                shaders.clear();
                invalidate();
                if (sceneAgain) { sceneAgain = false; renderScene(); }
            });
        }, "scene").start();
    }

    // ---------------------------------------------------------------- input

    private boolean longFired, backEaten;

    boolean onKey(int code, KeyEvent e) {
        lastKey = SystemClock.uptimeMillis();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_BUTTON_A:
                if (down) {
                    if (e.getRepeatCount() == 0) longFired = false;
                    else if (!longFired && (e.isLongPress() || e.getEventTime() - e.getDownTime() > 500)) { longFired = true; if (!overlays.isOpen()) longPress(); }
                } else if (!longFired) {
                    if (overlays.isOpen()) overlays.click(); else click();
                }
                invalidate();
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (!down) { if (overlays.mode == Overlays.PANEL) overlays.close(); else overlays.openPanel(); }
                return true;
        }
        if (overlays.isOpen()) {
            boolean backKey = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
            if (backKey && down) backEaten = true;
            overlays.key(code, e);
            if (backKey && !down && !overlays.isOpen()) backEaten = false;
            invalidate();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (!down) { if (backEaten) backEaten = false; else back(); }
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
        if (moving && area == Area.GRID) {  // ◀ ▶ one place, ▲ ▼ one row, the tiles between shift along
            int j = Math.max(0, Math.min(rest.size() - 1, gridI + dx + dy * 5));
            if (j != gridI) { apps.moveInRest(gridI, j); gridI = j; }
        } else if (moving) {
            int j = rowI + dx;
            if (dx != 0 && j >= 0 && j < row.size()) { apps.swapInRow(rowI, j); rowI = j; }
        } else if (area == Area.ROW) {
            if (dx != 0 && !row.isEmpty()) {
                int n = Math.max(0, Math.min(row.size() - 1, rowI + dx));
                if (n != rowI) { rowI = n; focusChanged(); }
            }
            if (dy < 0 && shelf != null && !shelf.items.isEmpty()) { area = Area.SHELF; cardI = 0; focusChanged(); }
            else if (dy < 0) toStatus();
            if (dy > 0 && !rest.isEmpty()) { area = Area.GRID; gridI = Math.min(rowI, Math.min(4, rest.size() - 1)); appsS.target = 1; focusChanged(); }
        } else if (area == Area.SHELF) {
            int n = Math.max(0, Math.min(shelf.items.size() - 1, cardI + dx));
            if (n != cardI) { cardI = n; focusChanged(); }
            if (dy > 0) area = Area.ROW;
            if (dy < 0) toStatus();
        } else if (area == Area.STATUS) {
            if (dy > 0) area = statusFrom == Area.STATUS ? Area.ROW : statusFrom;
        } else {
            int n = gridI + dx + dy * 5;
            if (dy < 0 && gridI < 5) { if (!row.isEmpty()) { area = Area.ROW; appsS.target = 0; } else toStatus(); }
            else if (n >= 0 && n < rest.size() && (dx == 0 || n / 5 == gridI / 5)) { gridI = n; focusChanged(); }
            else if (dy > 0 && n >= rest.size() && gridI / 5 < (rest.size() - 1) / 5) { gridI = rest.size() - 1; focusChanged(); }
        }
        rememberFocusedApp();
        invalidate();
    }

    private void rememberFocusedApp() {
        if (area == Area.ROW && apps != null && rowI >= 0 && rowI < apps.row.size())
            focusPrefs.edit().putString("homeFocusPackage", apps.row.get(rowI).pkg).apply();
    }

    private void toStatus() {
        statusFrom = area;
        area = Area.STATUS;
    }

    private void click() {
        if (apps == null) return;
        if (moving) { moving = false; showToast("Saved"); return; }
        if (area == Area.STATUS) { overlays.openPanel(); return; }
        if (area == Area.ROW && !apps.row.isEmpty()) host.launch(apps.row.get(rowI));
        else if (area == Area.GRID && !apps.rest.isEmpty()) {
            host.launch(apps.rest.get(gridI));
        } else if (area == Area.SHELF && shelf != null) {
            ShelfStore.Item it = shelf.items.get(cardI);
            host.open(it, apps.find(it.pkg));
        }
    }

    /** Long-press OK: the tile menu (or settings, on Lumen's own tile). */
    private void longPress() {
        if (apps == null || moving) return;
        if (area == Area.ROW && !apps.row.isEmpty()) overlays.openMenu(apps.row.get(rowI), true);
        else if (area == Area.GRID && !apps.rest.isEmpty()) {
            overlays.openMenu(apps.rest.get(gridI), false);
        }
    }

    private void back() {
        if (moving) moving = false;
        else if (area == Area.STATUS) area = statusFrom == Area.STATUS ? Area.ROW : statusFrom;
        else if (area != Area.ROW && !(apps != null && apps.row.isEmpty())) { area = Area.ROW; appsS.target = 0; }
        else { host.screensaver(); return; }
        invalidate();
    }

    private void focusChanged() {
        heartbeatArmed = true;
        heartbeatEpoch = SystemClock.uptimeMillis();
    }

    private void showToast(String t) { toast = t; toastUntil = SystemClock.uptimeMillis() + 2200; invalidate(); }

    // ---------------------------------------------------------------- Overlays.Host

    @Override public AppStore apps() { return apps; }
    @Override public Prefs prefs() { return prefs; }
    @Override public Bitmap liveBackdrop() { return deepBlur; }
    @Override public void redraw() { invalidate(); }

    @Override public void panelOpened() { if (area != Area.STATUS) toStatus(); }

    @Override public void sources() { Sources.show(getContext()); }

    @Override public void chooseLocation() {
        host.chooseLocation(() -> {
            changed(Prefs.PAGE_LOCATION);
            overlays.locationChanged();
        });
    }

    @Override public String weatherKind() { return weather.kind; }

    @Override public String weatherLine() {
        if (!prefs.is(Prefs.WEATHER, "on") || !weather.ready()) return null;
        return weather.description + " · " + weather.temp + "°  ·  H " + weather.high + "°  L " + weather.low + "°";
    }

    /** The home screen at quarter size, blurred: the menu's depth of field. */
    @Override public Bitmap snapshot() {
        Bitmap b = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(480 / Ui.W, 270 / vh);
        drawHome(c, SystemClock.uptimeMillis());
        Blur.boxBlur(b, 3, 3);
        return b;
    }

    @Override public void moveTile(AppStore.App a) {
        int i = apps.row.indexOf(a), g = apps.rest.indexOf(a);
        if (i >= 0) { area = Area.ROW; rowI = i; }
        else if (g >= 0) { area = Area.GRID; gridI = g; }
        else return;
        moving = true;
        invalidate();
    }

    @Override public void toggleRow(AppStore.App a) {
        if (apps.row.contains(a)) {
            apps.removeFromRow(a);
            rowI = Math.max(0, Math.min(rowI, apps.row.size() - 1));
            showToast(a.label + " moved to All Apps");
            if (apps.row.isEmpty()) { area = Area.GRID; appsS.target = 1; }
        } else {
            apps.addToRow(a);
            gridI = Math.max(0, Math.min(gridI, apps.rest.size() - 1));
            area = Area.ROW;
            appsS.target = 0;
            rowI = apps.row.size() - 1;
            showToast(a.label + " added to Top Row");
        }
        refreshShelf();
    }

    @Override public void hide(AppStore.App a) {
        prefs.toggle(Prefs.HIDDEN, a.pkg);
        showToast(a.label + " hidden · Settings › Hidden apps to show it again");
        host.reloadApps();
    }

    @Override public void unhide(String pkg) {
        if (prefs.contains(Prefs.HIDDEN, pkg)) prefs.toggle(Prefs.HIDDEN, pkg);
        host.reloadApps();
    }

    @Override public void appInfo(AppStore.App a) { host.appInfo(a.pkg); }

    @Override public void changed(String key) {
        switch (key) {
            case Prefs.BACKGROUND: case Prefs.TIME_OF_DAY: case Prefs.PHOTOS: renderScene(); break;
            case Prefs.SHELF: case Prefs.SHELF_OFF: refreshShelf(); break;
            case Prefs.CARD_SIZE: layoutShelf(); cardScroll.at(cardScrollTarget()); break;
            case Prefs.UNITS: case Prefs.PAGE_LOCATION: weather.refresh(); break;
            case Prefs.WEATHER: if (prefs.is(Prefs.WEATHER, "on")) weather.refresh(); break;
            case Prefs.CLOCK: clockMinute = -1; break;
        }
        invalidate();
    }

    // ---------------------------------------------------------------- drawing

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        long dt = lastFrame == 0 ? 16 : Math.min(50, now - lastFrame);
        lastFrame = now;
        boolean anim = step(dt) | overlays.step(dt);

        c.save();
        c.scale(s, s);
        drawHome(c, now);
        overlays.draw(c);
        drawToast(c, now);
        c.restore();

        if (anim || moving || (toast != null && now < toastUntil + 400)) postInvalidateOnAnimation();
        else if (heartbeatActive()) postInvalidateDelayed(64);
        else lastFrame = 0;  // idle: the next frame starts fresh
    }

    private boolean heartbeatActive() {
        if (!heartbeatArmed || reduced() || overlays.isOpen() || moving) return false;
        if (area == Area.ROW) return apps != null && !apps.row.isEmpty();
        if (area == Area.GRID) return apps != null && !apps.rest.isEmpty();
        return area == Area.SHELF && shelf != null && !shelf.items.isEmpty();
    }

    /** A quiet double-beat scale bump on focused tiles/cards; settings and menus stay still. */
    private float heartbeatScale(float focusAmount) {
        if (!heartbeatArmed || focusAmount < .75f || reduced() || overlays.isOpen()) return 1;
        float t = (SystemClock.uptimeMillis() - heartbeatEpoch) % HEARTBEAT_MS;
        float first = beat(t, 220, 520, 1);
        float second = beat(t, 900, 620, .55f);
        return 1 + .004f * Math.max(first, second);
    }

    private static float beat(float t, float start, float duration, float strength) {
        float phase = (t - start) / duration;
        if (phase <= 0 || phase >= 1) return 0;
        float wave = (float) Math.sin(Math.PI * phase);
        return strength * wave * wave;
    }

    private void drawHome(Canvas c, long now) {
        drawBackground(c);
        float appsT = Ui.clamp(appsS.x, 0, 1), pageY = -appsShift() * appsT;
        c.save();
        c.translate(0, pageY);
        if (appsT < 1) drawShelf(c, 1 - appsT);
        drawDock(c, pageY, appsT);
        if (appsT > 0.001f) drawGrid(c, appsT);
        c.restore();
        drawScrollingTopOverlay(c, appsT);
        drawTopBarHiding(c);
        if (!overlays.visible()) drawHint(c, now, appsT);
        drawCredit(c, appsT);
    }

    /** In All Apps the clock and weather lift up and fade out, leaving the dock alone at the top; Up brings them back. */
    private void drawTopBarHiding(Canvas c) {
        float hide = Ui.clamp(barHide.x, 0, 1);
        if (hide > .999f) return;
        if (hide < .001f) { drawTopBar(c); return; }
        int layer = c.saveLayerAlpha(0, 0, Ui.W, 270, Math.round(255 * (1 - hide)));
        if (!reduced()) c.translate(0, -(Ui.BAR_TOP + Ui.BAR_H) * hide);
        drawTopBar(c);
        c.restoreToCount(layer);
    }

    /** A pre-blurred glass fade darkens the top edge as All Apps scrolls underneath the dock. */
    private void drawScrollingTopOverlay(Canvas c, float progress) {
        if (progress <= .001f || deepBlur == null) return;
        screenRect.set(0, 0, Ui.W, vh);
        int layer = c.saveLayer(0, 0, Ui.W, 270, null);
        bmp.setAlpha(Math.round(215 * progress));
        c.drawBitmap(deepBlur, null, screenRect, bmp);
        c.drawRect(0, 0, Ui.W, 270, scrollMask);
        c.restoreToCount(layer);
        bmp.setAlpha(255);
        fill.setShader(scrollFade);
        fill.setAlpha(Math.round(255 * progress));
        c.drawRect(0, 0, Ui.W, 270, fill);
        fill.setShader(null);
        fill.setAlpha(255);
    }

    /** Advances every spring; returns true while anything is still moving. */
    private boolean step(long dt) {
        boolean a = appsS.step(dt);
        if (apps != null) {
            int n = (int) Math.min(apps.row.size(), ROW_MAX);
            if (area == Area.ROW) rowScroll.target = Math.max(0, Math.min(rowI - (n - 2), apps.row.size() - n)) * (TILE_W + GAP);
            gridScroll.target = Math.max(0, gridI / 5 - 1) * GRID_STEP_Y;
            a |= rowScroll.step(dt) | gridScroll.step(dt);
            for (int i = 0; i < apps.row.size(); i++) a |= ease(apps.row.get(i), area == Area.ROW && i == rowI, dt);
            for (int i = 0; i < apps.rest.size(); i++) a |= ease(apps.rest.get(i), area == Area.GRID && i == gridI, dt);
        }
        if (shelf != null) {
            cardScroll.target = cardScrollTarget();
            a |= cardScroll.step(dt);
            for (int i = 0; i < shelf.items.size(); i++) a |= ease(shelf.items.get(i), area == Area.SHELF && i == cardI, dt);
        }
        a |= ease(STATUS_KEY, area == Area.STATUS, dt);
        barHide.target = area == Area.GRID ? 1 : 0;  // keyed to focus, so Up to settings from a rowless grid still shows it
        a |= barHide.step(dt) | spec.step(dt);
        return a;
    }

    private boolean ease(Object key, boolean focused, long dt) {
        key = focusKey(key);
        Spring sp = focus.get(key);
        if (sp == null) {
            if (!focused) return false;
            focus.put(key, sp = reduced() ? new Spring(4.5f, 1f) : new Spring(2.8f, 1f));
        }
        sp.target = focused ? 1 : 0;
        return sp.step(dt);
    }

    private float focusOf(Object key) { Spring sp = focus.get(focusKey(key)); return sp == null ? 0 : sp.x; }

    private void drawBackground(Canvas c) {
        r.set(0, 0, Ui.W, vh);
        if (scene == null) { c.drawColor(Color.BLACK); return; }
        bmp.setAlpha(255);
        c.drawBitmap(scene, null, r, bmp);
        float appsT = Ui.clamp(appsS.x, 0, 1);
        if (appsT > 0.001f) {  // All Apps: the scene blurs and dims to half
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

    /**
     * Weather and clock at the top right. Focused (Up from the shelf or dock), they sit in a quiet glass bar and a
     * gear chip slides in at its end; the chip is the focused control. OK opens settings as a dropdown from here.
     * Geometry: bar 64 tall (radius 32), chip 52 inset 6 (radius 26, concentric), text 24 in from the bar's ends,
     * 14 either side of each hairline, everything on one optical centre line.
     */
    /** Average luminance (0..1) of a small bitmap, to tune legibility on photos. */
    private static float luminance(Bitmap b) {
        long sum = 0;
        int n = 0;
        for (int y = 0; y < b.getHeight(); y += 4) for (int x = 0; x < b.getWidth(); x += 4) {
            int c = b.getPixel(x, y);
            sum += (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
            n++;
        }
        return n == 0 ? 0 : sum / (255f * n);
    }

    /** The NASA title and creator credit, quietly in the bottom-right corner. */
    private void drawCredit(Canvas c, float appsT) {
        if (photoCredit == null || appsT > .99f) return;
        creditP.setAlpha(Math.round(Color.alpha(Ui.TEXT_3) * (1 - appsT)));
        c.drawText(ellipsize(photoCredit, creditP, EDGE_R - Ui.W / 2f - HINT_ROOM / 2), EDGE_R, vh - 38, creditP);  // clears the All Apps hint
    }

    private void drawTopBar(Canvas c) {
        boolean showClock = !prefs.is(Prefs.CLOCK, "off");
        boolean showWx = prefs.is(Prefs.WEATHER, "on") && weather.ready();
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (minute != clockMinute) {
            clockMinute = minute;
            String mode = prefs.get(Prefs.CLOCK);
            boolean h24 = "24".equals(mode) || ("system".equals(mode) && DateFormat.is24HourFormat(getContext()));
            clockText = DateFormat.format(h24 ? "H:mm" : "h:mm", now).toString();
        }
        float f = focusOf(STATUS_KEY), fc = Ui.clamp(f, 0, 1);
        final float R = Ui.BAR_RIGHT, cy = Ui.BAR_TOP + Ui.BAR_H / 2, sep = 14, hair = 1.5f;
        float chipD = Ui.CHIP_D, gearRoom = Ui.CHIP_INSET + chipD + sep + hair + sep;  // what the chip adds on the right
        float x = EDGE_R - (EDGE_R - R + gearRoom) * fc;  // right edge of the text: the dock's edge at rest, inside the pill focused

        float clockW = showClock ? clockP.measureText(clockText) : 0;
        String t = showWx ? weather.temp + "°" : null;
        float iconR = 15, wxW = showWx ? iconR * 2 + 8 + wxP.measureText(t) : 0;
        float contentW = clockW + wxW + (showClock && showWx ? sep * 2 + hair : 0);
        float barL = x - contentW - Ui.TEXT_INSET;

        if (fc > 0.01f) {  // the bar: quiet frosted glass
            r.set(barL, Ui.BAR_TOP, R, Ui.BAR_TOP + Ui.BAR_H);
            Shadow.draw(c, r, Ui.BAR_R, 16, 8, .22f * fc, bmp);
            fill.setShader(null);
            fill.setColor(Color.argb(Math.round(28 * fc), 255, 255, 255));
            rrect(c, r, Ui.BAR_R, fill);
            rim(c, r, Ui.BAR_R, .65f * fc);
            // the gear chip, the focused control: near-white, lifted, with a dark gear
            float gx = R - Ui.CHIP_INSET - chipD / 2 + (1 - fc) * 10, scale = 1 + .06f * Math.max(0, f);
            float rad = chipD / 2 * scale;
            r.set(gx - rad, cy - rad, gx + rad, cy + rad);
            Shadow.draw(c, r, rad, 10, 5, .4f * fc, bmp);
            fill.setColor(Color.argb(Math.round(245 * fc), 255, 255, 255));
            c.drawCircle(gx, cy, rad, fill);
            stroke.setColor(Ui.withAlpha(Color.parseColor("#1c1c22"), fc));
            Glyphs.menu(c, "gear", gx, cy, 28 * scale, stroke);
            fill.setColor(Color.argb(Math.round(56 * fc), 255, 255, 255));  // hairline before the chip
            c.drawRect(x + sep, cy - 14, x + sep + hair, cy + 14, fill);
        }
        if (showClock) {
            clockP.setAlpha(255);
            c.drawText(clockText, x, cy + .36f * clockP.getTextSize(), clockP);
            if (prefs.is(Prefs.DATE, "on") && overlays.mode != Overlays.PANEL)
                c.drawText(DateFormat.format("EEEE, MMMM d", now).toString(), EDGE_R, Ui.BAR_TOP + Ui.BAR_H + 30, dateP);
            x -= clockW;
        }
        if (!showWx) return;
        if (showClock) {
            fill.setShader(null);
            fill.setColor(Color.argb(56, 255, 255, 255));
            c.drawRect(x - sep - hair, cy - 14, x - sep, cy + 14, fill);
            x -= sep * 2 + hair;
        }
        wxP.setColor(Ui.mix(Ui.TEXT_2, Ui.TEXT, fc));
        c.drawText(t, x, cy + .36f * wxP.getTextSize(), wxP);
        x -= wxP.measureText(t) + 8 + iconR;
        Glyphs.weather(c, weather.kind, x, cy, iconR, fill, stroke);
    }

    private void drawShelf(Canvas c, float alpha) {
        if (shelf == null || shelf.items.isEmpty()) return;
        int a = Math.round(255 * alpha);
        headP.setAlpha(Math.round(Color.alpha(Ui.TEXT) * alpha));
        headAppP.setAlpha(Math.round(Color.alpha(Ui.TEXT_2) * alpha));
        float left = shelfLeft();
        String suffix = shelf.app != null ? " · " + shelf.app : " · across your apps";
        c.drawText(shelf.heading, left, shelfTop + 25, headP);
        c.drawText(suffix, left + headP.measureText(shelf.heading), shelfTop + 25, headAppP);
        // the focused card draws last, so its shadow lies over its neighbours
        int focusedIdx = area == Area.SHELF ? cardI : -1;
        // cards end with the dock, not at the screen edge: ones past it fade out as they slide on to the real edge,
        // never cut by a clip, so nothing stops short of the screen or slices a shadow
        for (int i = 0; i < shelf.items.size(); i++) {
            if (i == focusedIdx) continue;
            float x = left + i * (cardW + GAP) - cardScroll.x;
            int ca = Math.round(a * cardVisibility(x));
            if (ca > 0) drawCard(c, shelf.items.get(i), x, cardTop, ca);
        }
        if (focusedIdx >= 0) {
            float x = left + focusedIdx * (cardW + GAP) - cardScroll.x;
            drawCard(c, shelf.items.get(focusedIdx), x, cardTop, Math.round(a * cardVisibility(x)));
        }
    }

    /** 1 for a card inside the dock's edges, falling to 0 as it slides one card's step past either edge. */
    private float cardVisibility(float x) {
        float over = Math.max(0, Math.max(EDGE_L - x, x + cardW - EDGE_R));
        float t = Math.max(0, 1 - over / (cardW + GAP));
        return t * t * (3 - 2 * t);  // smoothstep: eases in and out at both ends of the slide
    }

    /** Card geometry for the Card size setting. Large cards lift the shelf so their text clears the dock. */
    private void layoutShelf() {
        String size = prefs.get(Prefs.CARD_SIZE);
        int across = "compact".equals(size) ? 5 : "large".equals(size) ? 3 : 4;  // cards span the dock, edge to edge
        cardW = (DOCK_W - (across - 1) * GAP) / across;
        cardH = cardW * 9 / 16;
        shelfTop = Math.min(SHELF_TOP, dockBottom() - DOCK_H - cardH - 145);  // pinned to the top; lifts only on short screens
        cardTop = shelfTop + 50;
    }

    /** Scrolls only once focus passes the last card that fits on screen. */
    private float cardScrollTarget() {
        int visible = Math.max(1, (int) ((EDGE_R - EDGE_L + GAP + .5f) / (cardW + GAP)));
        return Math.max(0, cardI - (visible - 1)) * (cardW + GAP);
    }

    /** The first recommendation card shares the dock's outer edge. */
    private float shelfLeft() { return EDGE_L; }

    /** The dock's bottom edge: a fixed margin above the bottom of whatever screen this is. */
    private float dockBottom() { return vh - DOCK_MARGIN; }

    /** How far the page rises in All Apps, so the dock ends up at APPS_DOCK_TOP. */
    private float appsShift() { return dockBottom() - DOCK_H - APPS_DOCK_TOP; }

    /** Bottom row: the All Apps hint centered, the photo credit on the right, never overlapping. */
    private static final float HINT_ROOM = 200;

    /**
     * Smoothing for a corner `pad` outside one of radius r and smoothing s, so the outer curve begins exactly `pad`
     * further along the edge (where the inner one's offset does) and the two outlines stay parallel.
     */
    private static float concentric(float r, float s, float pad) { return ((1 + s) * r + pad) / (r + pad) - 1; }

    private void drawCard(Canvas c, ShelfStore.Item it, float x, float y, int alpha) {
        float f = focusOf(it), sc = (reduced() ? 1 : 1 + .07f * f) * heartbeatScale(f);
        float cx = x + cardW / 2, cy = y + cardH / 2, w = cardW * sc, h = cardH * sc;
        r.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        Shadow.draw(c, r, R_TILE * sc, Ui.lerp(12, 20, f), Ui.lerp(8, 20, f), Ui.lerp(.38f, .58f, f) * alpha / 255f, bmp);
        Bitmap b = art.get(it.art);
        if (b != null) {
            drawBitmapRound(c, b, r, R_TILE * sc, alpha);
        } else {  // artwork still loading, or none: a quiet card with the title
            fill.setShader(null);
            fill.setColor(Color.argb(Math.round(.9f * alpha), 38, 40, 50));
            rrect(c, r, R_TILE * sc, TILE_SMOOTH, fill);
            cardTitleP.setAlpha(alpha);
            c.save();
            c.clipRect(r);
            c.drawText(ellipsize(it.title, cardTitleP, w - 44), r.left + 22, r.bottom - (it.progress > 0 ? 34 : 22), cardTitleP);
            c.restore();
        }
        tileFinish(c, r, R_TILE * sc, f, alpha);
        if (it.progress > 0) {
            float bl = r.left + 22 * sc, br = r.right - 22 * sc, by = r.bottom - 12 * sc;
            fill.setShader(null);
            fill.setColor(Color.argb(Math.round(.3f * alpha), 255, 255, 255));
            rrect(c, bl, by - 5, br, by, 3, fill);
            fill.setColor(Color.argb(alpha, 255, 255, 255));
            rrect(c, bl, by - 5, bl + (br - bl) * it.progress, by, 3, fill);
        }
        if (prefs.is(Prefs.CARD_TEXT, "none")) return;
        float ty = y + cardH + 34 + 8 * f;
        titleP.setAlpha(Math.round(Color.alpha(Ui.TEXT) * alpha / 255f));
        c.drawText(ellipsize(it.title, titleP, cardW), x, ty, titleP);
        if (prefs.is(Prefs.CARD_TEXT, "details") && it.line != null && !it.line.isEmpty()) {
            metaP.setColor(Ui.mix(Ui.TEXT_2, Ui.TEXT, f * .5f));
            metaP.setAlpha(Math.round(Color.alpha(metaP.getColor()) * alpha / 255f));
            c.drawText(ellipsize(it.line, metaP, cardW), x, ty + 28, metaP);
        }
    }

    private void drawDock(Canvas c, float pageY, float appsT) {
        if (apps == null) return;
        List<AppStore.App> row = apps.row;
        float dockW = DOCK_W, dockH = DOCK_H;  // full width between the overscan margins
        float l = Ui.SAFE_X, t = dockBottom() - dockH;
        dock.set(l, t, l + dockW, t + dockH);

        // shadow, then glass: the pre-blurred scene behind the dock, saturated, tinted and lit from above
        Shadow.draw(c, dock, R_DOCK, 36, 26, .45f, bmp);
        if (glassBlur != null) {
            Bitmap gb = appsT > 0.5f ? deepBlur : glassBlur;
            BitmapShader gs = shaderFor(gb);
            srcRect.set(0, 0, gb.getWidth(), gb.getHeight());
            screenRect.set(0, -pageY, Ui.W, vh - pageY);  // the glass shows what is on screen behind it
            m.setRectToRect(srcRect, screenRect, Matrix.ScaleToFit.FILL);
            gs.setLocalMatrix(m);
            glassPaint.setShader(gs);
            rrect(c, dock, R_DOCK, DOCK_SMOOTH, glassPaint);
            glassPaint.setShader(null);
            if (appsT > 0.001f) { fill.setShader(null); fill.setColor(Color.argb(Math.round(128 * appsT), 0, 0, 0)); rrect(c, dock, R_DOCK, DOCK_SMOOTH, fill); }
        }
        fill.setColor(Color.WHITE);
        unit(fill, dockTintU, t, dockH, 255);
        rrect(c, dock, R_DOCK, DOCK_SMOOTH, fill);
        unit(fill, dockSheenU, t, dockH * .42f, 255);
        rrect(c, dock, R_DOCK, DOCK_SMOOTH, fill);
        fill.setShader(null);
        fill.setAlpha(255);

        // tiles; clip only when the row scrolls, and only at the sides, so focus shadows are never cut
        float x0 = l + PAD - rowScroll.x;
        int focusedIdx = area == Area.ROW ? rowI : -1;
        c.save();
        if (row.size() > ROW_MAX) c.clipRect(l, -Ui.H, l + dockW, 2 * Ui.H);
        for (int i = 0; i < row.size(); i++) {
            float x = x0 + i * (TILE_W + GAP);
            if (x > l + dockW || x + TILE_W < l || i == focusedIdx) continue;
            drawTile(c, row.get(i), x, t + PAD, false, 1);
        }
        if (focusedIdx >= 0 && focusedIdx < row.size()) drawTile(c, row.get(focusedIdx), x0 + focusedIdx * (TILE_W + GAP), t + PAD, moving, 1);
        c.restore();

        // specular rim, plus the highlight that glides to sit above the focused tile
        rim(c, dock, R_DOCK, DOCK_SMOOTH, 1);
        spec.target = focusedIdx >= 0 && !row.isEmpty() ? x0 + focusedIdx * (TILE_W + GAP) + TILE_W / 2 : l + dockW / 2;
        if (spec.x == 0) spec.at(spec.target);
        if (spec.x != spec.target || spec.v != 0) postInvalidateOnAnimation();  // the target is set after step(), so keep frames coming
        float specA = 1 - .65f * appsT;
        glow(c, dock, R_DOCK, DOCK_SMOOTH, spec.x, t + dockH * .006f, 198, dockH * .032f, Math.round(242 * specA));
        glow(c, dock, R_DOCK, DOCK_SMOOTH, spec.x, t, 239, dockH * .42f, Math.round(51 * specA));

        // names under the dock
        String mode = prefs.get(Prefs.LABELS);
        if (appsT < 1 && !"never".equals(mode)) {
            boolean always = "always".equals(mode);
            for (int i = 0; i < row.size(); i++) {
                boolean isFocus = i == focusedIdx;
                if (!isFocus && !always) continue;
                float cxi = x0 + i * (TILE_W + GAP) + TILE_W / 2;
                if (cxi < l || cxi > l + dockW) continue;
                float f = isFocus ? Ui.clamp(focusOf(row.get(i)), 0, 1) : 0;
                Paint p = isFocus && (f > .5f || !always) ? labelP : labelDimP;
                float a = always ? 1 : f;
                p.setAlpha(Math.round(Color.alpha(p == labelP ? Ui.TEXT : Ui.TEXT_2) * (1 - appsT) * a));
                c.drawText(row.get(i).label, cxi, t + dockH + 22 + 25, p);
            }
        }
    }

    private void drawGrid(Canvas c, float alpha) {
        List<AppStore.App> rest = apps.rest;
        gridHeadP.setAlpha(Math.round(Color.alpha(Ui.TEXT_2) * alpha));
        c.save();
        float bottom = dockBottom(), gridTop = bottom + 146, end = vh + appsShift();
        c.clipRect(0, bottom + 20, Ui.W, end);  // rows scrolling up slide under the dock
        c.drawText("All Apps", LEFT, bottom + 120 - gridScroll.x, gridHeadP);  // lines up with the first column
        int focusedIdx = area == Area.GRID ? gridI : -1;
        String mode = prefs.get(Prefs.LABELS);  // the same Labels setting as the dock
        boolean always = "always".equals(mode), never = "never".equals(mode);
        for (int pass = 0; pass < 2; pass++) {  // focused tile last, so its shadow lies on top
            for (int i = 0; i < rest.size(); i++) {
                if ((i == focusedIdx) != (pass == 1)) continue;
                float x = LEFT + (i % 5) * (TILE_W + GAP), y = gridTop + (i / 5) * GRID_STEP_Y - gridScroll.x;
                if (y > end || y + TILE_H < bottom) continue;
                AppStore.App a = rest.get(i);
                drawTile(c, a, x, y, moving && i == focusedIdx, alpha);
                float f = Ui.clamp(focusOf(a), 0, 1);
                if (never) continue;
                if (always && f <= .5f) {  // unfocused names stay quiet, like the dock's
                    labelDimP.setAlpha(Math.round(Color.alpha(Ui.TEXT_2) * alpha));
                    c.drawText(ellipsize(a.label, labelDimP, TILE_W + GAP - 8), x + TILE_W / 2, y + TILE_H + 16 + 23 + 6 * f, labelDimP);
                } else if (f > 0.01f) {
                    nameP.setAlpha(Math.round(255 * (always ? 1 : f) * alpha));
                    c.drawText(a.label, x + TILE_W / 2, y + TILE_H + 16 + 23 + 6 * f, nameP);
                }
            }
        }
        c.restore();
    }

    private void drawTile(Canvas c, AppStore.App a, float x, float y, boolean wiggle, float alpha) {
        float f = focusOf(a), sc = (reduced() ? 1 : 1 + .09f * f) * heartbeatScale(f);
        float cx = x + TILE_W / 2, cy = y + TILE_H / 2, tw = TILE_W * sc, th = TILE_H * sc;
        int al = Math.round(255 * alpha);
        c.save();
        if (wiggle && !reduced()) c.rotate((float) Math.sin(SystemClock.uptimeMillis() / 130.0) * 1.0f, cx, cy);
        r.set(cx - tw / 2, cy - th / 2, cx + tw / 2, cy + th / 2);
        float fc = Ui.clamp(f, 0, 1);
        Shadow.draw(c, r, R_TILE * sc, Ui.lerp(8, 18, fc), Ui.lerp(4, 18, fc), Ui.lerp(.28f, .55f, fc) * alpha, bmp);
        if (a.tile != null) drawBitmapRound(c, a.tile, r, R_TILE * sc, al);
        tileFinish(c, r, R_TILE * sc, f, al);
        c.restore();
    }

    /** Static top light and a crisp focus ring shared by apps and recommendations, all with smoothed corners. */
    private void tileFinish(Canvas c, RectF rr, float radius, float f, int alpha) {
        if (f > 0.01f) {
            fill.setColor(Color.WHITE);
            unit(fill, sheenU, rr.top, rr.height(), Math.round(255 * Ui.clamp(f, 0, 1) * alpha / 255f));
            rrect(c, rr, radius, TILE_SMOOTH, fill);
            fill.setShader(null);
            fill.setAlpha(255);
        }
        stroke.setStrokeWidth(1.2f);
        stroke.setColor(Color.WHITE);
        stroke.setShader(null);
        stroke.setAlpha(255);
        stroke.setColor(Color.argb(Math.round(28 * alpha / 255f), 255, 255, 255));
        rimRect.set(rr.left + .6f, rr.top + .6f, rr.right - .6f, rr.bottom - .6f);
        rrect(c, rimRect, radius - .6f, TILE_SMOOTH, stroke);
        float focused = Ui.clamp(f, 0, 1) * alpha / 255f;
        if (focused > .01f) {
            stroke.setStrokeWidth(3);
            stroke.setColor(Color.argb(Math.round(255 * focused), 255, 255, 255));
            rimRect.set(rr.left - 4, rr.top - 4, rr.right + 4, rr.bottom + 4);
            rrect(c, rimRect, radius + 4, concentric(radius, TILE_SMOOTH, 4), stroke);
        }
        stroke.setStrokeWidth(1.2f);
        stroke.setAlpha(255);
    }

    private void drawBitmapRound(Canvas c, Bitmap b, RectF rr, float radius, int alpha) {
        BitmapShader sh = shaderFor(b);
        srcRect.set(0, 0, b.getWidth(), b.getHeight());
        m.setRectToRect(srcRect, rr, Matrix.ScaleToFit.FILL);
        sh.setLocalMatrix(m);
        bmp.setShader(sh);
        bmp.setAlpha(alpha);
        rrect(c, rr, radius, TILE_SMOOTH, bmp);
        bmp.setShader(null);
        bmp.setAlpha(255);
    }

    /** The specular rim: bright where light hits the top edge, a faint bounce at the bottom. */
    private void rim(Canvas c, RectF rr, float radius, float alpha) { rim(c, rr, radius, -1, alpha); }

    private void rim(Canvas c, RectF rr, float radius, float smoothing, float alpha) {
        stroke.setStrokeWidth(1.5f);
        stroke.setColor(Color.WHITE);
        unit(stroke, dockRimU, rr.top, rr.height(), Math.round(255 * alpha));
        rimRect.set(rr.left + .75f, rr.top + .75f, rr.right - .75f, rr.bottom - .75f);
        rrect(c, rimRect, radius - .75f, smoothing, stroke);
        stroke.setShader(null);
        stroke.setAlpha(255);
    }

    /** A soft white elliptical glow at (cx, cy) with radii rx, ry, painted only inside the rounded rect `in`. */
    private void glow(Canvas c, RectF in, float radius, float smoothing, float cx, float cy, float rx, float ry, int alpha) {
        sm.setScale(rx, Math.max(.5f, ry));
        sm.postTranslate(cx, cy);
        glowU.setLocalMatrix(sm);
        fill.setColor(Color.WHITE);
        fill.setShader(glowU);
        fill.setAlpha(alpha);
        rrect(c, in, radius, smoothing, fill);
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

    private void drawHint(Canvas c, long now, float appsT) {
        float idle = (now - lastKey) / 1000f;
        float a = (area == Area.ROW && !moving && apps != null && !apps.rest.isEmpty() ? 1 : 0) * Ui.clamp(4.6f - idle, 0, 1) * (1 - appsT);
        if (a <= 0) return;
        hintP.setAlpha(Math.round(Color.alpha(Ui.TEXT_3) * a));
        float y = vh - 38, hl = Ui.W / 2f - (22 + hintP.measureText("All Apps")) / 2;  // chevron and label centered together
        c.drawText("All Apps", hl + 22, y, hintP);
        stroke.setShader(null);
        stroke.setStrokeWidth(2.2f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(Ui.withAlpha(Ui.TEXT_3, a));
        float hx = hl + 6, hy = y - 8;
        c.drawLine(hx - 6, hy - 3, hx, hy + 3, stroke);
        c.drawLine(hx, hy + 3, hx + 6, hy - 3, stroke);
        if (idle < 4.7f) postInvalidateDelayed(120);  // fade it out, then stop drawing
    }

    private void drawToast(Canvas c, long now) {
        String msg = moving ? (area == Area.GRID ? "◀ ▶ ▲ ▼  Move      OK  Done" : "◀ ▶  Move      OK  Done") : toast;
        if (msg == null) return;
        float a = moving ? 1 : Ui.clamp((toastUntil - now) / 300f, 0, 1);
        if (a <= 0) { toast = null; return; }
        float w = toastP.measureText(msg) + 56, cx = Ui.W / 2, y = vh - 40 - 54;
        r.set(cx - w / 2, y, cx + w / 2, y + 54);
        Shadow.draw(c, r, 27, 16, 8, .4f * a, bmp);
        fill.setShader(null);
        fill.setColor(Color.argb(Math.round(215 * a), 34, 34, 42));
        rrect(c, r, 27, fill);
        rim(c, r, 27, .6f * a);
        toastP.setAlpha(Math.round(255 * a));
        c.drawText(msg, cx, y + 35, toastP);
    }

    private static String ellipsize(String s, Paint p, float max) {
        if (p.measureText(s) <= max) return s;
        int n = p.breakText(s, true, max - p.measureText("…"), null);
        return s.substring(0, Math.max(0, n)).trim() + "…";
    }

    // ---- continuous-corner drawing (continuous rounded rects; see Ui.squircle) ----
    private final android.graphics.Path rrPath = new android.graphics.Path();

    private final RectF rimRect = new RectF();

    private void rrect(Canvas c, RectF rr, float radius, Paint p) { c.drawPath(Ui.squircle(rrPath, rr, radius), p); }

    /** smoothing < 0 keeps the standard continuous corner; 0..1 uses Figma-style smoothing (the dock uses 1). */
    private void rrect(Canvas c, RectF rr, float radius, float smoothing, Paint p) {
        c.drawPath(smoothing < 0 ? Ui.squircle(rrPath, rr, radius) : Ui.squircle(rrPath, rr, radius, smoothing), p);
    }

    private void rrect(Canvas c, float l, float t, float r, float b, float radius, Paint p) { c.drawPath(Ui.squircle(rrPath, l, t, r, b, radius), p); }
}
