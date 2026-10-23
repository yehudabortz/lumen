package dev.lumen.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Photos from Unsplash's official API (random photo for a query, landscape, strict content filter), as Unsplash's
 * API guidelines ask: hotlinked image URLs, the download endpoint pinged when a photo is used, and the photographer
 * credited on screen. Needs an access key in local.properties (unsplash.access_key); without one it's switched off.
 * The current photo is cached on disk so it's there at boot and offline. Network calls run on the caller's thread.
 */
final class Unsplash {
    static final String QUERY = "desktop wallpaper";
    private static final String API = "https://api.unsplash.com";

    static final class Photo {
        final Bitmap bitmap;
        final String credit;  // "Photo by Jane Doe on Unsplash"

        Photo(Bitmap bitmap, String credit) { this.bitmap = bitmap; this.credit = credit; }
    }

    private Unsplash() {}

    static boolean available() { return Keys.UNSPLASH != null && !Keys.UNSPLASH.isEmpty(); }

    /** The cached photo for `slot` ("home" or "saver"), center-cropped to w x h, or null. */
    static Photo cached(Context c, String slot, int w, int h) {
        File f = file(c, slot);
        if (!f.exists()) return null;
        Bitmap b = BitmapFactory.decodeFile(f.getPath());
        if (b == null) return null;
        return new Photo(crop(b, w, h), prefs(c).getString(slot + ".credit", "Unsplash"));
    }

    /** How long ago the cached photo for `slot` was fetched, in ms (Long.MAX_VALUE if never). */
    static long age(Context c, String slot) {
        long at = prefs(c).getLong(slot + ".at", 0);
        return at == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - at;
    }

    /** Fetches a new random photo, sized for a w x h screen, caches it for `slot` and returns it. */
    static Photo next(Context c, String slot, int w, int h) throws Exception {
        String q = URLEncoder.encode(QUERY, "UTF-8");
        JSONObject j = new JSONObject(get(API + "/photos/random?orientation=landscape&content_filter=high&query=" + q, true));
        String name = j.getJSONObject("user").optString("name", "Unknown");
        String raw = j.getJSONObject("urls").getString("raw");
        String location = j.getJSONObject("links").optString("download_location", null);

        byte[] data = bytes(raw + (raw.contains("?") ? "&" : "?") + "w=" + w + "&h=" + h + "&fit=crop&crop=entropy&fm=jpg&q=82", false);
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (b == null) throw new IllegalStateException("not an image");
        if (location != null) {  // Unsplash's guidelines: count the use of a photo as a download
            try { get(location, true); } catch (Exception ignored) { }
        }
        File f = file(c, slot), tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(data); }
        if (!tmp.renameTo(f)) throw new IllegalStateException("cache write failed");
        String credit = "Photo by " + name + " on Unsplash";
        prefs(c).edit().putString(slot + ".credit", credit).putLong(slot + ".at", System.currentTimeMillis()).apply();
        return new Photo(crop(b, w, h), credit);
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("unsplash", Context.MODE_PRIVATE); }

    private static File file(Context c, String slot) { return new File(c.getFilesDir(), "unsplash-" + slot + ".jpg"); }

    /** Center-crops to exactly w x h (the screen), so the scene pipeline can treat it like a rendered scene. */
    static Bitmap crop(Bitmap src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h && src.isMutable()) return src;
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        int cw = Math.round(w / scale), ch = Math.round(h / scale);
        int cx = (src.getWidth() - cw) / 2, cy = (src.getHeight() - ch) / 2;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, new Rect(cx, cy, cx + cw, cy + ch), new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
        if (out != src) src.recycle();
        return out;
    }

    private static String get(String url, boolean auth) throws Exception { return new String(bytes(url, auth), "UTF-8"); }

    private static byte[] bytes(String url, boolean auth) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(15000);
        if (auth) {
            con.setRequestProperty("Authorization", "Client-ID " + Keys.UNSPLASH);
            con.setRequestProperty("Accept-Version", "v1");
        }
        try (InputStream in = con.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally {
            con.disconnect();
        }
    }
}
