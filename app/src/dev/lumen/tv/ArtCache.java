package dev.lumen.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Artwork for shelf cards, fetched from the URIs the apps publish, center-cropped to the card's size and
 * kept in a small memory cache. Only what's on screen gets loaded.
 */
final class ArtCache {
    interface Listener { void onArt(); }

    private final Context context;
    private final int width, height;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> pending = new HashSet<>(), failed = new HashSet<>();
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private final Listener listener;

    ArtCache(Context c, int width, int height, Listener listener) {
        this.context = c.getApplicationContext();
        this.width = width;
        this.height = height;
        this.listener = listener;
    }

    /** The bitmap if it's ready; otherwise starts loading it and returns null. */
    Bitmap get(Uri uri) {
        if (uri == null) return null;
        String key = uri.toString();
        Bitmap b = cache.get(key);
        if (b != null || failed.contains(key) || !pending.add(key)) return b;
        pool.execute(() -> {
            Bitmap loaded = null;
            try { loaded = fetch(uri); } catch (Exception ignored) { }
            Bitmap result = loaded;
            main.post(() -> {
                pending.remove(key);
                if (result == null) failed.add(key); else { cache.put(key, result); listener.onArt(); }
            });
        });
        return null;
    }

    private Bitmap fetch(Uri uri) throws Exception {
        byte[] data;
        String scheme = uri.getScheme();
        if ("http".equals(scheme) || "https".equals(scheme)) {
            HttpURLConnection con = (HttpURLConnection) new URL(uri.toString()).openConnection();
            con.setConnectTimeout(6000);
            con.setReadTimeout(10000);
            try (InputStream in = con.getInputStream()) { data = read(in); } finally { con.disconnect(); }
        } else {
            try (InputStream in = context.getContentResolver().openInputStream(uri)) { data = read(in); }
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= width && o.outHeight / (sample * 2) >= height) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (src == null) return null;
        // center-crop to the card's aspect
        float scale = Math.max(width / (float) src.getWidth(), height / (float) src.getHeight());
        int cw = Math.round(width / scale), ch = Math.round(height / scale);
        int cx = (src.getWidth() - cw) / 2, cy = (src.getHeight() - ch) / 2;
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, new Rect(cx, cy, cx + cw, cy + ch), new Rect(0, 0, width, height), new Paint(Paint.FILTER_BITMAP_FLAG));
        src.recycle();
        return out;
    }

    private static byte[] read(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    void forgetFailures() { failed.clear(); }
}
