package dev.lumen.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The TV's launchable apps, their tile artwork, and which ones sit in the Top Row (in what order). */
final class AppStore {
    static final class App {
        final String pkg, label;
        final ComponentName component;
        Bitmap tile;  // 288x162 design px, rendered once

        App(String pkg, String label, ComponentName component) {
            this.pkg = pkg; this.label = label; this.component = component;
        }

        Intent launchIntent() {
            return new Intent(Intent.ACTION_MAIN).setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
    }

    /** Video apps most people want first in a fresh Top Row; everything else follows A–Z. */
    private static final List<String> PREFERRED = Arrays.asList(
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus", "com.hulu.livingroomplus", "com.wbd.stream", "com.plexapp.android", "com.peacocktv.peacockandroid", "com.cbs.ott",
            "tv.pluto.android", "com.spotify.tv.android");
    private static final int DEFAULT_ROW = 5;
    static final float TILE_W = 296, TILE_H = TILE_W * 9 / 16;  // five fill the dock with 42 px gaps

    final List<App> all = new ArrayList<>();     // visible apps
    final List<App> hidden = new ArrayList<>();  // hidden from Lumen (still installed)
    final List<App> row = new ArrayList<>();
    final List<App> rest = new ArrayList<>();    // All Apps: everything not in the row, A–Z until rearranged
    private final SharedPreferences prefs;
    private final float scale;

    AppStore(Context c, float pxPerDesignPx) {
        prefs = c.getSharedPreferences("lumen", Context.MODE_PRIVATE);
        scale = pxPerDesignPx;
    }

    /** Reloads the app list. Runs off the main thread; returns this for chaining. */
    AppStore load(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, App> byPkg = new HashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> found = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0);
            for (ResolveInfo ri : found) {
                ActivityInfo ai = ri.activityInfo;
                if (ai.packageName.equals(c.getPackageName()) || byPkg.containsKey(ai.packageName)) continue;
                App app = new App(ai.packageName, String.valueOf(ri.loadLabel(pm)), new ComponentName(ai.packageName, ai.name));
                app.tile = renderTile(pm, ri);
                byPkg.put(ai.packageName, app);
            }
        }
        Set<String> hiddenSet = prefs.getStringSet(Prefs.HIDDEN, Collections.<String>emptySet());
        all.clear();
        hidden.clear();
        for (App a : byPkg.values()) (hiddenSet.contains(a.pkg) ? hidden : all).add(a);
        Collections.sort(all, (a, b) -> a.label.compareToIgnoreCase(b.label));
        Collections.sort(hidden, (a, b) -> a.label.compareToIgnoreCase(b.label));
        arrange(byPkg);
        return this;
    }

    private void arrange(Map<String, App> byPkg) {
        row.clear();
        rest.clear();
        String saved = prefs.getString("row", null);
        List<String> order = new ArrayList<>();
        if (saved != null) {
            for (String p : saved.split(",")) if (!p.isEmpty()) order.add(p);
        } else {
            for (String p : PREFERRED) if (byPkg.containsKey(p) && order.size() < DEFAULT_ROW) order.add(p);
            for (App a : all) if (order.size() < DEFAULT_ROW && !order.contains(a.pkg)) order.add(a.pkg);
        }
        Set<String> inRow = new LinkedHashSet<>();
        for (String p : order) {
            App a = find(p);
            if (a != null && inRow.add(p)) row.add(a);
        }
        // a saved All Apps order keeps its apps in place; apps it doesn't know yet (new installs) follow, A–Z
        String savedRest = prefs.getString("rest", null);
        if (savedRest != null) for (String p : savedRest.split(",")) {
            App a = find(p);
            if (a != null && !inRow.contains(p) && !rest.contains(a)) rest.add(a);
        }
        for (App a : all) if (!inRow.contains(a.pkg) && !rest.contains(a)) rest.add(a);
    }

    App find(String pkg) {
        for (App a : all) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    void saveRow() {
        StringBuilder sb = new StringBuilder();
        for (App a : row) sb.append(a.pkg).append(',');
        prefs.edit().putString("row", sb.toString()).apply();
    }

    /** Saves the All Apps order, once the user has rearranged it; until then it stays A–Z. */
    private void saveRest() {
        if (prefs.contains("rest")) writeRest();
    }

    private void writeRest() {
        StringBuilder sb = new StringBuilder();
        for (App a : rest) sb.append(a.pkg).append(',');
        prefs.edit().putString("rest", sb.toString()).apply();
    }

    void addToRow(App a) {
        if (row.contains(a)) return;
        row.add(a);
        rest.remove(a);
        saveRow();
        saveRest();
    }

    void removeFromRow(App a) {
        if (!row.remove(a)) return;
        if (prefs.contains("rest")) rest.add(0, a);  // a rearranged grid: first, where it is easy to find and move
        else {
            rest.add(a);
            Collections.sort(rest, (x, y) -> x.label.compareToIgnoreCase(y.label));
        }
        saveRow();
        saveRest();
    }

    /** Moves an All Apps tile from i to j, shifting the tiles between; from then on the order is saved. */
    void moveInRest(int i, int j) {
        rest.add(j, rest.remove(i));
        writeRest();
    }

    void swapInRow(int i, int j) {
        Collections.swap(row, i, j);
        saveRow();
    }

    /**
     * The tile: the app's own 16:9 banner edge to edge, or, for apps without one, its icon on a color taken
     * from the icon, under a soft frosted square.
     */
    private Bitmap renderTile(PackageManager pm, ResolveInfo ri) {
        int w = Math.round(TILE_W * scale * 1.1f), h = Math.round(TILE_H * scale * 1.1f);  // 1.1: room for the focus zoom
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Drawable banner = ri.activityInfo.loadBanner(pm);
        if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
        if (banner != null) {
            banner.setBounds(0, 0, w, h);
            banner.draw(c);
            return b;
        }
        Drawable icon = ri.loadIcon(pm);
        int base = averageColor(icon);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, Ui.mix(base, Color.WHITE, .08f), Ui.mix(base, Color.BLACK, .35f), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        float sq = h * .54f, l = (w - sq) / 2f, t = (h - sq) / 2f;
        p.setColor(Color.argb(44, 255, 255, 255));
        c.drawRoundRect(l, t, l + sq, t + sq, sq * .26f, sq * .26f, p);
        float pad = sq * .14f;
        icon.setBounds(Math.round(l + pad), Math.round(t + pad), Math.round(l + sq - pad), Math.round(t + sq - pad));
        icon.draw(c);
        return b;
    }

    private static int averageColor(Drawable d) {
        Bitmap one = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 8, 8);
        d.draw(new Canvas(one));
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int c = one.getPixel(x, y);
            if (Color.alpha(c) < 128) continue;
            r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++;
        }
        if (n == 0) return Color.parseColor("#3a3f48");
        int avg = Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
        // keep it rich but dark enough for a white icon to read
        float[] hsv = new float[3];
        Color.colorToHSV(avg, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.15f);
        hsv[2] = Math.min(hsv[2], .62f);
        return Color.HSVToColor(hsv);
    }

    static boolean usesFahrenheit() {
        String cc = Locale.getDefault().getCountry();
        return "US".equals(cc) || "LR".equals(cc) || "MM".equals(cc) || "BS".equals(cc) || "KY".equals(cc) || "PW".equals(cc);
    }
}
