package dev.lumen.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The tile menu (long-press OK on a tile) and the settings panel. Both are glass over the home screen: the menu
 * over a blurred snapshot (depth of field), the panel over the live scene so every change previews behind it.
 */
final class Overlays {
    interface Host {
        AppStore apps();
        Prefs prefs();
        Bitmap snapshot();         // the home screen, blurred, for the menu's depth of field
        Bitmap liveBackdrop();     // the scene, blurred, for the panel's glass
        void moveTile(AppStore.App a);
        void toggleRow(AppStore.App a);
        void hide(AppStore.App a);
        void unhide(String pkg);
        void appInfo(AppStore.App a);
        void changed(String key);  // a setting changed: apply it now
        void chooseLocation();
        void sources();
        void panelOpened();        // focus moves to the clock-and-weather pill the dropdown hangs from
        String weatherLine();      // "62° · H 68° L 51°", or null; the condition is shown by its glyph
        String weatherKind();
        void redraw();
    }

    static final int NONE = 0, MENU = 1, PANEL = 2;

    private static final class Item {
        final String label, icon;
        String value;
        final Runnable action;

        Item(String label, String icon, String value, Runnable action) { this.label = label; this.icon = icon; this.value = value; this.action = action; }
    }

    private static final int HEAD = 0, CHOICE = 1, PAGE = 2, TOGGLE = 3, NOTE = 4;

    private static final class Row {
        final int kind;
        final String label, key;
        String value;
        android.text.StaticLayout note;

        Row(int kind, String label, String key, String value) { this.kind = kind; this.label = label; this.key = key; this.value = value; }

        boolean focusable() { return kind != HEAD && kind != NOTE; }
        float height() { return kind == NOTE ? Math.max(36, note.getHeight() + 12) : ROW; }
    }

    private final Host host;
    int mode = NONE;
    private int closingMode = NONE;
    private LinearGradient fade;
    private final android.graphics.Path clipPath = new android.graphics.Path();
    // Overlays open with a hint of life and close briskly, with no bounce
    private static final float OPEN_HZ = 3.4f, OPEN_DAMPING = .8f, CLOSE_HZ = 5f;
    private final Spring open = new Spring(OPEN_HZ, OPEN_DAMPING), scroll = new Spring(3f, 1f), pill = new Spring(4f, .85f);

    // menu
    private AppStore.App target;
    private boolean targetInRow;
    private final List<Item> items = new ArrayList<>();
    private int itemI;
    private Bitmap snap;

    // panel
    private String page = null;  // null = main, or Prefs.PAGE_*
    private final List<Row> rows = new ArrayList<>();
    private int rowI;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), glass = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final android.text.TextPaint ellipsizeP = new android.text.TextPaint();
    private final Paint titleP, nameP, subP, rowP, valueP, headP, noteP;
    private final RectF pillRect = new RectF(), r = new RectF(), src = new RectF(), screen = new RectF(0, 0, Ui.W, Ui.H);
    private float screenH = Ui.H;  // the home view's height in design px
    private final Matrix m = new Matrix();
    private Bitmap shaderBitmap;
    private BitmapShader shader;
    private final LinearGradient rimU = new LinearGradient(0, 0, 0, 1,
            new int[]{Color.argb(170, 255, 255, 255), Color.argb(56, 255, 255, 255), Color.argb(16, 255, 255, 255), Color.argb(10, 255, 255, 255), Color.argb(40, 255, 255, 255)},
            new float[]{0, .08f, .3f, .8f, 1}, Shader.TileMode.CLAMP);

    Overlays(Host host) {
        this.host = host;
        stroke.setStyle(Paint.Style.STROKE);
        titleP = Ui.text(Ui.bold, 40, Ui.TEXT, -.03f);
        nameP = Ui.text(Ui.semibold, 27, Ui.TEXT, -.02f);
        subP = Ui.text(Ui.medium, 19, Ui.TEXT_2, 0);
        rowP = Ui.text(Ui.medium, 24, Ui.TEXT, -.01f);
        valueP = Ui.text(Ui.medium, 24, Ui.TEXT_2, -.01f);
        valueP.setTextAlign(Paint.Align.RIGHT);
        headP = Ui.text(Ui.semibold, 20, Ui.TEXT_3, 0);
        noteP = Ui.text(Ui.medium, 20, Ui.TEXT_2, -.005f);
    }

    /** Follows the home view's real height, so menus center and panels fit on any screen. */
    void screenHeight(float h) { screenH = h; screen.set(0, 0, Ui.W, h); }

    boolean isOpen() { return mode != NONE; }

    /** True while an overlay is on screen, including its closing animation. */
    boolean visible() { return mode != NONE || open.x > 0.004f; }

    // ---------------------------------------------------------------- opening

    void openMenu(AppStore.App app, boolean inRow) {
        target = app;
        targetInRow = inRow;
        snap = host.snapshot();
        buildMenu();
        itemI = 0;
        pill.at(itemI);
        mode = closingMode = MENU;
        open.tune(OPEN_HZ, OPEN_DAMPING).target = 1;
        host.redraw();
    }

    void openPanel() {
        if (mode == MENU) open.at(0);
        page = null;
        buildRows();
        rowI = firstFocusable();
        scroll.at(0);
        pill.at(rowI);
        mode = closingMode = PANEL;
        open.tune(OPEN_HZ, OPEN_DAMPING).target = 1;
        host.panelOpened();
        host.redraw();
    }

    void close() {
        mode = NONE;
        open.tune(CLOSE_HZ, 1f).target = 0;
        host.redraw();
    }

    private void buildMenu() {
        items.clear();
        final AppStore.App a = target;
        Prefs p = host.prefs();
        items.add(new Item("Move", "move", null, () -> { close(); host.moveTile(a); }));
        items.add(new Item(targetInRow ? "Remove from Top Row" : "Add to Top Row", targetInRow ? "remove" : "add", null, () -> { close(); host.toggleRow(a); }));
        items.add(new Item("Show in Top Shelf", "shelf", p.contains(Prefs.SHELF_OFF, a.pkg) ? "Off" : "On", () -> {
            p.toggle(Prefs.SHELF_OFF, a.pkg);
            items.get(itemI).value = p.contains(Prefs.SHELF_OFF, a.pkg) ? "Off" : "On";
            host.changed(Prefs.SHELF_OFF);
        }));
        items.add(new Item("Hide", "hide", null, () -> { close(); host.hide(a); }));
        items.add(new Item("App info", "info", null, () -> { close(); host.appInfo(a); }));
        items.add(new Item("Home Screen Settings", "gear", null, this::openPanel));
    }

    private void buildRows() {
        rows.clear();
        Prefs p = host.prefs();
        AppStore apps = host.apps();
        if (page == null) {
            rows.add(new Row(PAGE, "Weather by Open-Meteo", "sources", "CC BY 4.0"));
            String section = null;
            for (Prefs.Option o : Prefs.OPTIONS) {
                if (!p.relevant(o)) continue;
                if (!o.section.equals(section)) { rows.add(new Row(HEAD, o.section, null, null)); section = o.section; }
                if (o.isPage()) rows.add(new Row(PAGE, o.label, o.key, pageSummary(o.key)));
                else rows.add(new Row(CHOICE, o.label, o.key, p.name(o.key)));
            }
            rows.add(new Row(HEAD, "About", null, null));
            rows.add(new Row(NOTE, "Lumen " + BuildInfo.VERSION + " · by Yehuda Bortz", null, null));
            rows.add(new Row(NOTE, "No ads, no tracking: the shelf shows only what your apps publish.", null, null));
        } else if (Prefs.PAGE_HIDDEN.equals(page)) {
            List<AppStore.App> every = new ArrayList<>(apps.all);
            every.addAll(apps.hidden);
            java.util.Collections.sort(every, (x, y) -> x.label.compareToIgnoreCase(y.label));
            for (AppStore.App a : every) rows.add(new Row(TOGGLE, a.label, a.pkg, apps.hidden.contains(a) ? "Hidden" : "Shown"));
        } else {
            for (AppStore.App a : apps.all) rows.add(new Row(TOGGLE, a.label, a.pkg, p.contains(Prefs.SHELF_OFF, a.pkg) ? "Off" : "On"));
        }
        android.text.TextPaint notePaint = new android.text.TextPaint(noteP);
        notePaint.setColor(Ui.TEXT_3);
        for (Row row : rows) if (row.kind == NOTE)
            row.note = android.text.StaticLayout.Builder.obtain(row.label, 0, row.label.length(), notePaint, Math.round(600 - 2 * IN))
                    .setIncludePad(false).setLineSpacing(4, 1).build();
    }

    void locationChanged() {
        for (Row row : rows) if (Prefs.PAGE_LOCATION.equals(row.key)) row.value = pageSummary(row.key);
        host.redraw();
    }

    private String pageSummary(String key) {
        if (Prefs.PAGE_LOCATION.equals(key)) {
            Weather.Place city = host.prefs().city();
            return city == null ? "Time zone (approx.)" : city.name;
        }
        if (Prefs.PAGE_HIDDEN.equals(key)) {
            int n = host.apps().hidden.size();
            return n == 0 ? "None" : n + " hidden";
        }
        int off = 0;
        for (AppStore.App a : host.apps().all) if (host.prefs().contains(Prefs.SHELF_OFF, a.pkg)) off++;
        return off == 0 ? "All" : off + " off";
    }

    private int lastFocusable() {
        for (int i = rows.size() - 1; i >= 0; i--) if (rows.get(i).focusable()) return i;
        return 0;
    }

    private int firstFocusable() {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).focusable()) return i;
        return 0;
    }

    // ---------------------------------------------------------------- input

    /** D-pad and Back while an overlay is open. OK arrives through click(). */
    boolean key(int code, KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (code) {
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE:
                if (mode == PANEL && page != null) { goPage(null); }
                else close();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP: step(-1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: step(1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: side(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: side(1); return true;
        }
        return true;
    }

    void click() {
        if (mode == MENU) { items.get(itemI).action.run(); host.redraw(); return; }
        if (mode != PANEL || rows.isEmpty()) return;
        Row row = rows.get(rowI);
        if (row.kind == CHOICE) side(1);
        else if (row.kind == PAGE) {
            if ("sources".equals(row.key)) host.sources();
            else if (Prefs.PAGE_LOCATION.equals(row.key)) host.chooseLocation();
            else goPage(row.key);
        }
        else if (row.kind == TOGGLE) toggle(row);
    }

    private void step(int d) {
        if (mode == MENU) {
            itemI = Math.max(0, Math.min(items.size() - 1, itemI + d));
            pill.target = itemI;
        } else {
            int i = rowI + d;
            while (i >= 0 && i < rows.size() && !rows.get(i).focusable()) i += d;
            if (i >= 0 && i < rows.size()) { rowI = i; pill.target = rowI; }
            else if (d < 0) scroll.target = 0;  // reveal the title above the first row
        }
        host.redraw();
    }

    private void side(int d) {
        if (mode == MENU) {
            if ("shelf".equals(items.get(itemI).icon)) items.get(itemI).action.run();
            host.redraw();
            return;
        }
        Row row = rows.get(rowI);
        if (row.kind == CHOICE) {
            host.prefs().cycle(row.key, d);
            row.value = host.prefs().name(row.key);
            host.changed(row.key);
            if (Prefs.BACKGROUND.equals(row.key) && page == null) {
                buildRows();
                for (int i = 0; i < rows.size(); i++) if (Prefs.BACKGROUND.equals(rows.get(i).key)) { rowI = i; pill.at(i); }
            }
        } else if (row.kind == TOGGLE) toggle(row);
        host.redraw();
    }

    private void toggle(Row row) {
        if (Prefs.PAGE_HIDDEN.equals(page)) {
            AppStore.App a = findIn(host.apps().hidden, row.key);
            if (a != null) { host.unhide(row.key); row.value = "Shown"; }
            else { a = findIn(host.apps().all, row.key); if (a != null) { host.hide(a); row.value = "Hidden"; } }
        } else {
            host.prefs().toggle(Prefs.SHELF_OFF, row.key);
            row.value = host.prefs().contains(Prefs.SHELF_OFF, row.key) ? "Off" : "On";
            host.changed(Prefs.SHELF_OFF);
        }
        host.redraw();
    }

    private static AppStore.App findIn(List<AppStore.App> list, String pkg) {
        for (AppStore.App a : list) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    private void goPage(String p) {
        String from = page;
        page = p;
        buildRows();
        rowI = firstFocusable();
        if (p == null && from != null) for (int i = 0; i < rows.size(); i++) if (from.equals(rows.get(i).key)) rowI = i;
        pill.at(rowI);
        scroll.at(0);
        host.redraw();
    }

    // ---------------------------------------------------------------- drawing

    /** Advances the overlay's springs; returns true while anything moves. */
    boolean step(long dt) {
        boolean a = open.step(dt);
        a |= scroll.step(dt);
        a |= pill.step(dt);
        if (mode == NONE && open.x <= 0.004f) { open.at(0); closingMode = NONE; snap = null; }
        return a;
    }

    void draw(Canvas c) {
        float t = Ui.clamp(open.x, 0, 1);
        if (t <= 0 || closingMode == NONE) return;
        if (closingMode == MENU) drawMenu(c, t); else drawPanel(c, t);
    }

    /*
     * Shared geometry for the tile menu and the settings dropdown (design px):
     *   container radius 32, padding 16  ->  selection row radius 16, height 60
     *   text 24 inside a row  ->  every label, title and heading sits 40 from the container's edge
     *   section heading: 24 above, 8 below; title block: 28 above the cap height
     */
    private static final float PAD = Ui.MENU_PAD, IN = PAD + Ui.TEXT_INSET, ROW = Ui.ROW_H;

    private float baseline(Paint p, float rowTop, float rowH) { return rowTop + rowH / 2 + .36f * p.getTextSize(); }

    private void drawMenu(Canvas c, float t) {
        // depth of field: the home screen softens and dims behind the menu
        if (snap != null) {
            glass.setShader(null);
            glass.setAlpha(Math.round(255 * t));
            c.drawBitmap(snap, null, screen, glass);
            glass.setAlpha(255);
        }
        fill.setShader(null);
        fill.setColor(Color.argb(Math.round(66 * t), 0, 0, 0));
        c.drawRect(screen, fill);

        final float header = 24 + 56 + 24;  // 24 · app tile (56) · 24, then a hairline
        float w = 560, h = header + 1 + 8 + ROW * items.size() + PAD, cx = Ui.W / 2, cy = screenH * .42f + (1 - t) * 12;
        float sc = .95f + .05f * t;
        c.save();
        c.scale(sc, sc, cx, cy);
        r.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        panelGlass(c, r, Ui.MENU_R, snap, t);
        float x = r.left, top = r.top;
        int al = Math.round(255 * t);
        // header: the app's tile, its name and where it lives, aligned with the item icons below
        float tileW = 100, tileH = 56;
        if (target.tile != null) {
            RectF mini = new RectF(x + IN - 4, top + 24, x + IN - 4 + tileW, top + 24 + tileH);
            src.set(0, 0, target.tile.getWidth(), target.tile.getHeight());
            m.setRectToRect(src, mini, Matrix.ScaleToFit.FILL);
            BitmapShader sh = new BitmapShader(target.tile, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            sh.setLocalMatrix(m);
            fill.setShader(sh);
            fill.setAlpha(al);
            rrect(c, mini, 10, fill);
            fill.setShader(null);
        }
        float nx = x + IN - 4 + tileW + 20;
        nameP.setAlpha(al);
        subP.setAlpha(Math.round(Color.alpha(Ui.TEXT_2) * t));
        c.drawText(target.label, nx, top + 24 + 24, nameP);
        c.drawText(targetInRow ? "In Top Row" : "All Apps", nx, top + 24 + 50, subP);
        fill.setColor(Color.argb(Math.round(26 * t), 255, 255, 255));
        c.drawRect(x + IN, top + header, x + w - IN, top + header + 1, fill);

        float y0 = top + header + 1 + 8;
        drawPill(c, x + PAD, y0 + pill.x * ROW, x + w - PAD, ROW, Ui.ROW_R, t);
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            float y = y0 + i * ROW;
            float f = Ui.clamp(1 - Math.abs(pill.x - i), 0, 1);
            int ink = Ui.mix(Ui.TEXT, Color.parseColor("#0c0c10"), f);
            stroke.setColor(Ui.withAlpha(ink, t));
            Glyphs.menu(c, it.icon, x + IN + 12, y + ROW / 2, 24, stroke);
            rowP.setColor(Ui.withAlpha(ink, t));
            c.drawText(it.label, x + IN + 24 + 16, baseline(rowP, y, ROW), rowP);
            if (it.value != null) {
                valueP.setColor(Ui.withAlpha(Ui.mix(Ui.TEXT_2, Color.argb(140, 0, 0, 0), f), t));
                c.drawText(it.value, x + w - IN, baseline(valueP, y, ROW), valueP);
            }
        }
        c.restore();
    }

    /** Settings as a compact dropdown hanging 12 below the status bar, right edges aligned, as tall as its content. */
    private void drawPanel(Canvas c, float t) {
        fill.setShader(null);
        fill.setColor(Color.argb(Math.round(40 * t), 0, 0, 0));
        c.drawRect(screen, fill);

        // layout (relative to the dropdown's top): title, weather line, then sections of rows
        String wxLine = page == null ? host.weatherLine() : null;
        float head = 28 + 34 + (wxLine != null ? 10 + 26 : 0) + 8;  // cap-height block of the title (+ weather)
        float[] ys = new float[rows.size()];
        float y = head;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.kind == HEAD) { y += 24; ys[i] = y; y += 21 + 8; continue; }
            ys[i] = y;
            y += row.height();
        }
        float contentH = y + PAD;
        float w = 600, right = Ui.BAR_RIGHT, x = right - w, top = Ui.BAR_TOP + Ui.BAR_H + Ui.DROPDOWN_GAP;
        float h = Math.min(contentH, screenH - 48 - top), bottom = top + h;

        if (!rows.isEmpty()) {  // keep the focused row in view
            float rowTop = ys[rowI], rowBottom = rowTop + ROW, visible = h - PAD;
            if (rowBottom - scroll.target > visible) scroll.target = rowBottom - visible;
            if (rowTop - scroll.target < head) scroll.target = Math.max(0, rowTop - head);
            if (rowI == firstFocusable()) scroll.target = 0;
            if (rowI == lastFocusable()) scroll.target = Math.max(0, contentH - h);  // reveal the notes below
        }

        // grows out of the gear chip, opaque early so it never looks ghostly mid-grow, and settles with the
        // spring's slight overshoot; closing, it shrinks only a little and is mostly a quick fade
        boolean closing = mode == NONE;
        float sc = closing ? .96f + .04f * t : .9f + .1f * Math.min(open.x, 1.02f);
        float opacity = closing ? t : Ui.clamp(t / .45f, 0, 1);
        c.save();
        c.scale(sc, sc, right - Ui.CHIP_INSET - Ui.CHIP_D / 2, Ui.BAR_TOP + Ui.BAR_H / 2);
        r.set(x, top, right, bottom);
        boolean fading = opacity < .99f;
        if (fading) c.saveLayerAlpha(r.left - 120, r.top - 120, r.right + 120, r.bottom + 160, Math.round(255 * opacity));
        panelGlass(c, r, Ui.MENU_R, host.liveBackdrop(), 1);
        c.save();
        c.clipRect(r);
        c.translate(0, top - scroll.x);
        String title = page == null ? "Settings" : Prefs.PAGE_HIDDEN.equals(page) ? "Hidden apps" : "Apps in Top Shelf";
        titleP.setTextSize(34);
        titleP.setAlpha(255);
        c.drawText(title, x + IN, 28 + 34 * .73f, titleP);
        if (wxLine != null) {
            float wy = 28 + 34 + 10 + 13;  // centre of the weather line
            Glyphs.weather(c, host.weatherKind(), x + IN + 14, wy, 14, fill, stroke);
            noteP.setColor(Ui.TEXT_2);
            c.drawText(wxLine, x + IN + 28 + 10, wy + .36f * noteP.getTextSize(), noteP);
        }
        if (!rows.isEmpty()) {
            int lo = (int) Math.floor(Ui.clamp(pill.x, 0, rows.size() - 1)), hi = (int) Math.ceil(Ui.clamp(pill.x, 0, rows.size() - 1));
            drawPill(c, x + PAD, Ui.lerp(ys[lo], ys[hi], pill.x - lo), right - PAD, ROW, Ui.ROW_R, 1);
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float ry = ys[i];
            if (ry - scroll.x > h || ry - scroll.x + ROW < 0) continue;
            if (row.kind == HEAD) { c.drawText(row.label, x + IN, ry + 21 * .73f, headP); continue; }
            if (row.kind == NOTE) {
                c.save(); c.translate(x + IN, ry + (row.height() - row.note.getHeight()) / 2);
                row.note.draw(c); c.restore(); continue;
            }
            float f = i == rowI ? Ui.clamp(1 - Math.abs(pill.x - i), 0, 1) : 0;
            rowP.setColor(Ui.mix(Ui.TEXT, Color.parseColor("#0c0c10"), f));
            c.drawText(row.label, x + IN, baseline(rowP, ry, ROW), rowP);
            valueP.setColor(Ui.mix(Ui.TEXT_2, Color.argb(140, 0, 0, 0), f));
            String v = row.value == null ? "" : row.value;
            if (row.kind == CHOICE && f > .5f) v = "‹  " + v + "  ›";
            else if (row.kind == PAGE) v = v + "  ›";
            float valueWidth = w - 2 * IN - rowP.measureText(row.label) - 24;
            ellipsizeP.set(valueP);
            v = android.text.TextUtils.ellipsize(v, ellipsizeP,
                    Math.max(0, valueWidth), android.text.TextUtils.TruncateAt.END).toString();
            c.drawText(v, right - IN, baseline(valueP, ry, ROW), valueP);
        }
        c.restore();
        if (contentH > h) {  // soft fade where rows scroll under the bottom edge
            if (fade == null) fade = new LinearGradient(0, 0, 0, 1, 0x0022222A, Color.argb(210, 30, 30, 38), Shader.TileMode.CLAMP);
            m.setScale(1, 64);
            m.postTranslate(0, bottom - 64);
            fade.setLocalMatrix(m);
            fill.setShader(fade);
            c.save();
            c.clipPath(Ui.squircle(clipPath, r, Ui.MENU_R));
            c.drawRect(x, bottom - 64, right, bottom, fill);
            c.restore();
            fill.setShader(null);
        }
        if (fading) c.restore();
        c.restore();
    }

    /** Dark frosted glass: blurred backdrop, a charcoal tint so text stays crisp, a specular rim and a soft shadow. */
    private void panelGlass(Canvas c, RectF rr, float radius, Bitmap backdrop, float alpha) {
        Shadow.draw(c, rr, radius, 50, 26, .55f * alpha, fill);
        if (backdrop != null) {
            BitmapShader sh = shaderFor(backdrop);
            src.set(0, 0, backdrop.getWidth(), backdrop.getHeight());
            m.setRectToRect(src, screen, Matrix.ScaleToFit.FILL);
            sh.setLocalMatrix(m);
            glass.setShader(sh);
            glass.setAlpha(Math.round(255 * alpha));
            rrect(c, rr, radius, glass);
            glass.setShader(null);
            glass.setAlpha(255);
        }
        fill.setShader(null);
        fill.setColor(Color.argb(Math.round(160 * alpha), 34, 34, 43));
        rrect(c, rr, radius, fill);
        stroke.setStrokeWidth(1.5f);
        m.setScale(1, rr.height());
        m.postTranslate(0, rr.top);
        rimU.setLocalMatrix(m);
        stroke.setShader(rimU);
        stroke.setAlpha(Math.round(255 * alpha));
        rrect(c, rr.left + .75f, rr.top + .75f, rr.right - .75f, rr.bottom - .75f, radius, stroke);
        stroke.setShader(null);
        stroke.setAlpha(255);
    }

    /** The near-white selection pill, as on a clean interface; it glides between rows. */
    private void drawPill(Canvas c, float l, float t, float rgt, float h, float radius, float alpha) {
        pillRect.set(l, t, rgt, t + h);
        Shadow.draw(c, pillRect, radius, 12, 6, .35f * alpha, fill);
        fill.setShader(null);
        fill.setColor(Color.argb(Math.round(240 * alpha), 255, 255, 255));
        rrect(c, pillRect, radius, fill);
    }

    private BitmapShader shaderFor(Bitmap b) {
        if (b != shaderBitmap) { shaderBitmap = b; shader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); }
        return shader;
    }

    // ---- continuous-corner drawing (continuous rounded rects; see Ui.squircle) ----
    private final android.graphics.Path rrPath = new android.graphics.Path();

    private void rrect(Canvas c, RectF rr, float radius, Paint p) { c.drawPath(Ui.squircle(rrPath, rr, radius), p); }

    private void rrect(Canvas c, float l, float t, float r, float b, float radius, Paint p) { c.drawPath(Ui.squircle(rrPath, l, t, r, b, radius), p); }
}
