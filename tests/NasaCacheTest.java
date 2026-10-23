package dev.lumen.tv;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import java.util.HashMap;
import java.util.Map;

/** Runs with app_process and the built APK, using an isolated shell-owned directory and no network. */
public final class NasaCacheTest {
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Object at = thread.getMethod("systemMain").invoke(null);
        Context system = (Context) thread.getMethod("getSystemContext").invoke(at);
        Context base = system.createPackageContext("dev.lumen.tv", Context.CONTEXT_IGNORE_SECURITY);
        File root = new File(args[0]);
        File cache = new File(root, "cache-" + System.nanoTime()); cache.mkdirs();
        Map<String, SharedPreferences> stores = new HashMap<>();
        Context isolated = new ContextWrapper(base) {
            @Override public File getCacheDir() { return cache; }
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                if (!stores.containsKey(name)) stores.put(name, preferences());
                return stores.get(name);
            }
        };
        java.lang.reflect.Method directory = Nasa.class.getDeclaredMethod("directory", Context.class);
        directory.setAccessible(true);
        File dir = (File) directory.invoke(null, isolated);
        java.lang.reflect.Method prefs = Nasa.class.getDeclaredMethod("prefs", Context.class);
        prefs.setAccessible(true);
        SharedPreferences nasaPrefs = (SharedPreferences) prefs.invoke(null, isolated);
        if (args.length > 1 && args[1].equals("online")) {
            if (args.length > 3) {
                java.lang.reflect.Method query = Nasa.class.getDeclaredMethod("searchQuery", Context.class, String.class, String.class, java.util.Random.class);
                query.setAccessible(true);
                Object chosen = query.invoke(null, isolated, args[3], null, new java.util.Random());
                check(chosen != null, "a scenic result for " + args[3]);
                java.lang.reflect.Method json = chosen.getClass().getDeclaredMethod("json"); json.setAccessible(true);
                nasaPrefs.edit().putString("online.entry", json.invoke(chosen).toString())
                        .putLong("online.at", System.currentTimeMillis()).apply();
            } else Nasa.photo(isolated, "online", 320, 180, 1, null);
            Field worker = Nasa.class.getDeclaredField("warming"); worker.setAccessible(true);
            long deadline = System.currentTimeMillis() + 60000;
            while (worker.getBoolean(null) && System.currentTimeMillis() < deadline) Thread.sleep(100);
            File[] files = dir.listFiles((folder, name) -> name.endsWith(".jpg"));
            check(!worker.getBoolean(null) && files != null && files.length == 1, "one live NASA result downloaded");
            File f = files[0];
            long bytes = f.length();
            check(bytes <= 2 * 1024 * 1024 && f.getName().endsWith(".jpg"), "download bound");
            Nasa.Photo ph = Nasa.photo(isolated, "online", 320, 180, 3600000, null);
            check(ph != null && ph.credit.contains(" · "), "search result decodes with title and credit");
            ph.bitmap.recycle();
            System.out.println("Selected: " + ph.credit + " -> " + f.getAbsolutePath());
            System.out.println("SOURCE: " + nasaPrefs.getString("online.entry", ""));
            if (args.length < 3 || !args[2].equals("keep")) cleanup(cache);
            System.out.println("PASS: live NASA search selected and downloaded a scenic result; " + bytes + " bytes on disk");
            System.exit(0);
        }
        // Prevent all downloads: these tests exercise actual offline decoding and rotation, not a network mock.
        Field warming = Nasa.class.getDeclaredField("warming"); warming.setAccessible(true); warming.setBoolean(null, true);
        check(Nasa.photo(isolated, "empty", 320, 180, 3600000, null) == null, "empty cache falls back");
        dir.mkdirs();
        fixture(new File(dir, "sample_001.jpg"), 0xff223355);
        fixture(new File(dir, "sample_002.jpg"), 0xff335522);
        nasaPrefs.edit().putString("home.entry", entry("sample_001", "Sample image", "NASA")).apply();
        Nasa.Photo first = Nasa.photo(isolated, "home", 320, 180, 3600000, null);
        check(first != null && first.bitmap.getWidth() == 320 && first.bitmap.getHeight() == 180, "screen-sized offline decode");
        check(first.credit.contains("NASA"), "NASA attribution retained");
        String credit = first.credit; first.bitmap.recycle();
        Nasa.Photo stable = Nasa.photo(isolated, "home", 320, 180, 3600000, null);
        check(stable.credit.equals(credit), "home does not rotate early"); stable.bitmap.recycle();
        Nasa.Photo next = Nasa.photo(isolated, "home", 320, 180, 0, null);
        check(next.credit.equals(credit), "offline due rotation keeps the last good image"); next.bitmap.recycle();
        nasaPrefs.edit().putString("bad.entry", entry("sample_003", "Broken sample", "NASA")).apply();
        File bad = new File(dir, "sample_003.jpg");
        try (FileOutputStream out = new FileOutputStream(bad)) { out.write(new byte[]{1, 2, 3}); }
        Nasa.Photo recovered = Nasa.photo(isolated, "bad", 320, 180, 0, null);
        check(recovered == null && !bad.exists(), "corrupt cache removed and live refresh scheduled");
        Bitmap huge = Bitmap.createBitmap(2001, 8, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(bad)) { huge.compress(Bitmap.CompressFormat.JPEG, 80, out); }
        huge.recycle();
        nasaPrefs.edit().putString("bounds.entry", entry("sample_003", "Oversize sample", "NASA")).apply();
        Nasa.Photo safe = Nasa.photo(isolated, "bounds", 320, 180, 0, null);
        check(safe == null && !bad.exists(), "oversized dimensions rejected before decode");
        cleanup(cache);
        System.out.println("PASS: 7 NASA cache checks (offline fallback, size, attribution, stable selection, offline retention, corruption, bounds)");
        System.exit(0);
    }

    private static void cleanup(File root) {
        File[] files = root.listFiles();
        if (files != null) for (File f : files) { if (f.isDirectory()) cleanup(f); else f.delete(); }
        root.delete();
    }

    private static void fixture(File file, int color) throws Exception {
        Bitmap b = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
        b.eraseColor(color);
        try (FileOutputStream out = new FileOutputStream(file)) { b.compress(Bitmap.CompressFormat.JPEG, 80, out); }
        b.recycle();
    }

    private static String entry(String id, String title, String credit) throws Exception {
        return new org.json.JSONObject().put("id", id).put("title", title).put("credit", credit)
                .put("url", "https://images-assets.nasa.gov/image/" + id + "/" + id + "~large.jpg").toString();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static SharedPreferences preferences() {
        Map<String, Object> values = new HashMap<>();
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                new Class[]{SharedPreferences.Editor.class}, (p, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("put")) { values.put((String) args[0], args[1]); return p; }
                    if (name.equals("remove")) { values.remove(args[0]); return p; }
                    if (name.equals("clear")) { values.clear(); return p; }
                    if (name.equals("commit")) return true;
                    return null;
                });
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class[]{SharedPreferences.class}, (p, method, args) -> {
                    String name = method.getName();
                    if (name.equals("edit")) return editor;
                    if (name.equals("getAll")) return values;
                    if (name.equals("contains")) return values.containsKey(args[0]);
                    if (name.startsWith("get")) return values.containsKey(args[0]) ? values.get(args[0]) : args[1];
                    return null;
                });
    }
}
