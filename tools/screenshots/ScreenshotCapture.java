package dev.lumen.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Calendar;

/** Separate instrumentation APK: production HomeActivity/HomeView draw every pixel. */
public final class ScreenshotCapture extends Instrumentation {
    private HomeActivity activity;
    private HomeView view;
    private SharedPreferences preferences;
    private AppStore apps;
    private ShelfStore shelf;
    private File destination;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            preferences = getTargetContext().getSharedPreferences("lumen", 0);
            preferences.edit().clear().commit();
            if (!new Prefs(getTargetContext()).is(Prefs.BACKGROUND, "nasa"))
                throw new AssertionError("A fresh install must default to NASA photos");
            preferences.edit().clear().putString(Prefs.BACKGROUND, "nasa")
                    .putString(Prefs.TIME_OF_DAY, "off").putString(Prefs.DATE, "on")
                    .putString(Prefs.UNITS, "f").putString(Prefs.CLOCK, "12")
                    .putString(Prefs.SAVER, "nasa").commit();
            loadFixtures();
            destination = new File(getTargetContext().getExternalFilesDir(null), "screenshots");
            if (!destination.isDirectory() && !destination.mkdirs()) throw new IllegalStateException("Cannot create screenshot directory");
            File[] oldCaptures = destination.listFiles();
            if (oldCaptures != null) for (File file : oldCaptures)
                if (file.getName().endsWith(".png") && !file.delete()) throw new IllegalStateException("Cannot remove stale capture");
            Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(getTargetContext(), HomeActivity.class))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity = (HomeActivity) startActivitySync(intent);
            SystemClock.sleep(1800);
            view = (HomeView) field(activity, "view");
            // Freeze the periodic time-of-day input while capturing explicit day/dawn fixture palettes.
            runOnMainSync(() -> ((android.os.Handler) field(activity, "main")).removeCallbacks((Runnable) field(activity, "tick")));
            runOnMainSync(() -> { view.setApps(apps); view.setShelf(shelf); });
            verifyFocusRestore();
            waitForPhoto();
            capture("home");

            key(KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_UP);
            capture("status");
            key(KeyEvent.KEYCODE_MENU);
            capture("settings");
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            SystemClock.sleep(500);
            captureScreen("sources");
            key(KeyEvent.KEYCODE_BACK);
            for (int i = 0; i < 20; i++) key(KeyEvent.KEYCODE_DPAD_DOWN);
            capture("about");
            key(KeyEvent.KEYCODE_BACK);
            runOnMainSync(view::reset);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            runOnMainSync(() -> {
                ((Overlays) field(view, "overlays")).openMenu(apps.row.get(1), true);
            });
            capture("tile-menu");
            key(KeyEvent.KEYCODE_BACK);

            rows("continue");
            runOnMainSync(view::reset);
            capture("up-next");
            rows("picks");
            key(KeyEvent.KEYCODE_DPAD_UP);
            capture("top-picks");
            preference(Prefs.CARD_SIZE, "large"); capture("large-cards");
            preference(Prefs.CARD_SIZE, "compact"); preference(Prefs.CARD_TEXT, "title"); capture("compact-cards");
            preference(Prefs.CARD_SIZE, "standard"); preference(Prefs.CARD_TEXT, "details");

            // Top Shelf rows: the title as a switcher, switching, Follow the dock, then the Rows pages of settings
            rows("picks,continue,next,movies,short");
            runOnMainSync(view::reset);
            key(KeyEvent.KEYCODE_DPAD_UP); key(KeyEvent.KEYCODE_DPAD_UP);
            capture("shelf-rows");
            key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT);
            capture("shelf-movies");
            preference(Prefs.SHELF_ROW, "picks");
            preference(Prefs.FOLLOW, "on");
            runOnMainSync(view::reset);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            SystemClock.sleep(900);
            capture("follow-dock");
            preference(Prefs.FOLLOW, "off");
            runOnMainSync(() -> ((Overlays) field(view, "overlays")).openPanel(Prefs.PAGE_ROWS));
            capture("rows-settings");
            for (int i = 0; i < 3; i++) key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            capture("row-actions");
            key(KeyEvent.KEYCODE_DPAD_CENTER); key(KeyEvent.KEYCODE_DPAD_UP);
            capture("row-move");
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            for (int i = 0; i < 6; i++) key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            capture("add-row");
            key(KeyEvent.KEYCODE_BACK);
            rows("picks,continue,next");
            preference(Prefs.SHELF_ROW, "picks");
            runOnMainSync(view::reset);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            capture("all-apps");
            runOnMainSync(view::reset);

            landscape("forest", Scene.DAY); capture("forest-day");
            rows("continue");
            landscape("dunes", Scene.DAWN); capture("dunes-dawn");
            rows("picks");
            landscape("coast", Scene.DAY); capture("coast-day");
            preference(Prefs.SHELF, "off");
            landscape("black", Scene.DUSK); capture("quiet");

            final Screensaver.SaverView[] saver = new Screensaver.SaverView[1];
            runOnMainSync(() -> {
                saver[0] = new Screensaver.SaverView(activity);
                activity.setContentView(saver[0]);
            });
            long end = SystemClock.uptimeMillis() + 10000;
            while (field(saver[0], "current") == null && SystemClock.uptimeMillis() < end) SystemClock.sleep(100);
            if (field(saver[0], "current") == null) throw new IllegalStateException("Native screensaver image did not load");
            SystemClock.sleep(2000);
            captureScreen("screensaver");
            result.putString("output", destination.getAbsolutePath());
            result.putString("stream", "Captured 23 native Lumen screenshots with public sample content.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyFocusRestore() {
        runOnMainSync(() -> {
            view.prepareHomeEntry();
            java.util.Map<?, ?> before = (java.util.Map<?, ?>) field(view, "focus");
            Object spring = before.get(apps.row.get(0).pkg);
            if (spring == null || ((Spring) spring).x != 1 || ((Spring) spring).v != 0)
                throw new AssertionError("Initial Home focus must already be settled");
            AppStore replacement = new AppStore(getTargetContext(), 1);
            for (AppStore.App old : apps.all) {
                AppStore.App fresh = new AppStore.App(old.pkg, old.label, old.component); fresh.tile = old.tile;
                replacement.all.add(fresh); (replacement.all.size() <= 5 ? replacement.row : replacement.rest).add(fresh);
            }
            apps = replacement;
            view.setApps(replacement);
            if (((java.util.Map<?, ?>) field(view, "focus")).get(apps.row.get(0).pkg) != spring)
                throw new AssertionError("App reload must retain focus by package, not object identity");
            view.hostWindowFocusChanged(false); view.hostWindowFocusChanged(true);
            if (((Spring) spring).x != 1 || ((Spring) spring).v != 0 || (Boolean) field(view, "heartbeatArmed"))
                throw new AssertionError("Returning Home must not restart focus or heartbeat motion");
        });
        Bundle progress = new Bundle(); progress.putString("stream", "PASS: Home entry, app reload and window-return focus stay settled\n"); sendStatus(0, progress);
    }

    private void loadFixtures() throws Exception {
        JSONObject fixtures;
        try (InputStream in = getContext().getAssets().open("fixtures.json")) {
            fixtures = new JSONObject(new String(read(in), "UTF-8"));
        }
        AppStore installed = new AppStore(getTargetContext(), 1).load(getTargetContext());
        apps = new AppStore(getTargetContext(), 1);
        JSONArray entries = fixtures.getJSONArray("apps");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String pkg = entry.getString("package");
            AppStore.App app = new AppStore.App(pkg, entry.getString("name"), new ComponentName(pkg, "ScreenshotFixture"));
            AppStore.App real = installed.find(pkg);
            if (real != null) app.tile = real.tile;  // The bundled YouTube app supplies its real TV banner.
            else {
                try (InputStream in = getContext().getAssets().open(entry.getString("asset"))) {
                    Bitmap icon = BitmapFactory.decodeStream(in);
                    if (icon == null) throw new IllegalStateException("Unreadable icon: " + pkg);
                    app.tile = demoBanner(icon);
                    icon.recycle();
                }
            }
            apps.all.add(app);
            (i < 5 ? apps.row : apps.rest).add(app);
        }
        shelf = new ShelfStore();
        JSONArray cards = fixtures.getJSONArray("cards");
        seedWallpaper(cards.getJSONObject(3), "home");
        seedWallpaper(cards.getJSONObject(2), "saver");
        Uri[] art = new Uri[cards.length()];
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.getJSONObject(i);
            File image = new File(getTargetContext().getFilesDir(), card.getString("asset"));
            try (InputStream in = getContext().getAssets().open(card.getString("asset")); FileOutputStream out = new FileOutputStream(image)) {
                out.write(read(in));
            }
            art[i] = Uri.fromFile(image);
        }
        // Fictional programs on the public sample photos, one or more for every row type. The first five keep
        // the original cards (photo i on app i); the rest reuse photos for Up Next, Watchlist, live and short rows.
        // title, photo, app, Watch Next type (-1 = a suggestion), program type, minutes, progress, live, line
        Object[][] programs = {
            {cards.getJSONObject(0).getString("title"), 0, 0, ShelfRows.NEXT_CONTINUE, ShelfRows.TYPE_EPISODE, 50, .64f, false, "S1 · E3 · 18 min left"},
            {cards.getJSONObject(2).getString("title"), 2, 2, ShelfRows.NEXT_CONTINUE, ShelfRows.TYPE_EPISODE, 45, .28f, false, "S1 · E2 · 32 min left"},
            {"Storm Season", 1, 1, ShelfRows.NEXT_CONTINUE, ShelfRows.TYPE_EPISODE, 42, .88f, false, "S3 · E1 · 5 min left"},
            {"Night Shift Over Europe", 2, 3, ShelfRows.NEXT_NEXT, ShelfRows.TYPE_EPISODE, 44, 0f, false, "S2 · E5 · 44 min"},
            {"The Long Glacier", 4, 4, ShelfRows.NEXT_NEW, ShelfRows.TYPE_EPISODE, 52, 0f, false, "S1 · E7 · New episode"},
            {"Desert Crossing", 0, 1, ShelfRows.NEXT_WATCHLIST, ShelfRows.TYPE_MOVIE, 108, 0f, false, "Film · 1 h 48 min"},
            {cards.getJSONObject(1).getString("title"), 1, 1, -1, ShelfRows.TYPE_MOVIE, 48, 0f, false, "Documentary · 48 min"},
            {cards.getJSONObject(3).getString("title"), 3, 3, -1, ShelfRows.TYPE_MOVIE, 52, 0f, false, "Documentary · 52 min"},
            {cards.getJSONObject(4).getString("title"), 4, 4, -1, ShelfRows.TYPE_SERIES, 0, 0f, false, "Series · 6 episodes"},
            {"Ten-Minute Tides", 1, 0, -1, ShelfRows.TYPE_CLIP, 10, 0f, false, "Clip · 10 min"},
            {"Reef Cam", 3, 0, -1, ShelfRows.TYPE_EVENT, 0, 0f, true, "Live now"},
            {"Aurora Hunters", 2, 2, -1, ShelfRows.TYPE_SERIES, 0, 0f, false, "Series · 2 seasons"},
        };
        for (int i = 0; i < programs.length; i++) {
            Object[] p = programs[i];
            ShelfStore.Item item = new ShelfStore.Item();
            item.title = (String) p[0];
            item.art = art[(Integer) p[1]];
            item.pkg = apps.row.get((Integer) p[2]).pkg;
            item.nextType = (Integer) p[3];
            item.watchNext = item.nextType >= 0;
            item.type = (Integer) p[4];
            item.duration = (Integer) p[5] * 60_000L;
            item.progress = (Float) p[6];
            item.live = (Boolean) p[7];
            item.line = (String) p[8];
            item.intentUri = "https://images.nasa.gov/#" + i;
            item.engaged = 100 - i;
            if (item.watchNext) shelf.upNext.add(item); else shelf.recommendations.add(item);
        }
    }

    /** Sets the Top Shelf's rows, first one on Home. */
    private void rows(String ids) {
        runOnMainSync(() -> {
            preferences.edit().putString(Prefs.SHELF_ROWS, ids).remove(Prefs.SHELF_ROW).commit();
            view.changed(Prefs.SHELF_ROWS);
        });
    }

    /** Demo TV banner from the publisher's icon: preserve the logo's pixels and aspect ratio. */
    private static Bitmap demoBanner(Bitmap icon) {
        int w = icon.getWidth(), h = icon.getHeight();
        int background = icon.getPixel(w / 10, h / 10);
        if (Color.alpha(background) < 128) background = Color.BLACK;
        int left = w, top = h, right = 0, bottom = 0;
        for (int y = h / 16; y < h * 15 / 16; y++) for (int x = w / 16; x < w * 15 / 16; x++) {
            int pixel = icon.getPixel(x, y);
            int dr = Color.red(pixel) - Color.red(background), dg = Color.green(pixel) - Color.green(background), db = Color.blue(pixel) - Color.blue(background);
            if (Color.alpha(pixel) > 128 && dr * dr + dg * dg + db * db > 10000) {
                left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x + 1); bottom = Math.max(bottom, y + 1);
            }
        }
        if (right <= left || bottom <= top) { left = 0; top = 0; right = w; bottom = h; }
        Bitmap banner = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(banner); canvas.drawColor(background);
        float scale = Math.min(224f / (right - left), 112f / (bottom - top));
        float width = (right - left) * scale, height = (bottom - top) * scale;
        canvas.drawBitmap(icon, new Rect(left, top, right, bottom), new RectF((320 - width) / 2, (180 - height) / 2,
                (320 + width) / 2, (180 + height) / 2), new Paint(Paint.FILTER_BITMAP_FLAG));
        return banner;
    }

    private void seedWallpaper(JSONObject card, String slot) throws Exception {
        Method directory = Nasa.class.getDeclaredMethod("directory", android.content.Context.class);
        directory.setAccessible(true);
        File image = new File((File) directory.invoke(null, getTargetContext()), card.getString("nasa_id") + ".jpg");
        try (InputStream in = getContext().getAssets().open(card.getString("asset")); FileOutputStream out = new FileOutputStream(image)) {
            out.write(read(in));
        }
        JSONObject entry = new JSONObject().put("id", card.getString("nasa_id")).put("title", card.getString("image_title"))
                .put("credit", card.getString("credit")).put("url", card.getString("image"));
        Method prefs = Nasa.class.getDeclaredMethod("prefs", android.content.Context.class); prefs.setAccessible(true);
        ((SharedPreferences) prefs.invoke(null, getTargetContext())).edit().putString(slot + ".entry", entry.toString())
                .putLong(slot + ".at", System.currentTimeMillis()).remove(slot + ".retry").commit();
    }

    private void preference(String key, String value) {
        runOnMainSync(() -> { preferences.edit().putString(key, value).commit(); view.changed(key); });
    }

    private void waitForPhoto() throws Exception {
        long end = SystemClock.uptimeMillis() + 60000;
        while (field(view, "photoCredit") == null && SystemClock.uptimeMillis() < end) SystemClock.sleep(250);
        if (field(view, "photoCredit") == null) throw new IllegalStateException("NASA sample wallpaper did not finish decoding");
    }

    private void landscape(String kind, Scene.Palette palette) throws Exception {
        preference(Prefs.BACKGROUND, kind);
        long end = SystemClock.uptimeMillis() + 20000;
        final boolean[] busy = new boolean[1];
        do {
            runOnMainSync(() -> busy[0] = (Boolean) field(view, "sceneBusy"));
            if (!busy[0]) break;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < end);
        if (busy[0]) throw new IllegalStateException("Landscape render did not settle: " + kind);
        // Freeze the time-of-day input, while using the production renderer, blur and legibility code.
        Bitmap scene = Scene.render(kind, palette, 42, 1920, 1080);
        Bitmap glass = Blur.blurred(scene, 240, 4), deep = Blur.blurred(scene, 120, 4);
        Method legibility = HomeView.class.getDeclaredMethod("legibility", Bitmap.class, boolean.class);
        legibility.setAccessible(true);
        legibility.invoke(null, scene, Scene.isBright(palette, kind));
        runOnMainSync(() -> {
            put(view, "scene", scene); put(view, "glassBlur", glass); put(view, "deepBlur", deep);
            put(view, "palette", palette); put(view, "photoCredit", null);
            ((java.util.Map<?, ?>) field(view, "shaders")).clear();
            view.invalidate();
        });
    }

    private void capture(String name) throws Exception {
        SystemClock.sleep(1600);
        runOnMainSync(() -> {
            Weather weather = (Weather) field(view, "weather");
            weather.kind = "partly"; weather.temp = 68; weather.high = 73; weather.low = 59; weather.description = "Partly cloudy";
            put(weather, "nextFetch", Long.MAX_VALUE);
            Calendar now = Calendar.getInstance();
            put(view, "clockMinute", now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
            put(view, "clockText", "9:41");
            put(view, "heartbeatEpoch", SystemClock.uptimeMillis() + 60000);
            view.invalidate();
        });
        SystemClock.sleep(100);
        waitForIdleSync();
        captureScreen(name);
    }

    private void captureScreen(String name) throws Exception {
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null || bitmap.getWidth() != 1920 || bitmap.getHeight() != 1080)
            throw new IllegalStateException("Expected a native 1920×1080 framebuffer");
        try (FileOutputStream out = new FileOutputStream(new File(destination, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
        Bundle progress = new Bundle(); progress.putString("stream", "Captured " + name + "\n"); sendStatus(0, progress);
    }

    private void key(int code) { sendKeyDownUpSync(code); SystemClock.sleep(250); }

    private static byte[] read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384]; int count;
        while ((count = in.read(buf)) != -1) out.write(buf, 0, count);
        return out.toByteArray();
    }

    private static Object field(Object object, String name) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (Exception error) { throw new IllegalStateException(name, error); }
    }

    private static void put(Object object, String name, Object value) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value); }
        catch (Exception error) { throw new IllegalStateException(name, error); }
    }
}
