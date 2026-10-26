package dev.lumen.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Live NASA Image Library search with a small private rolling cache. */
final class Nasa {
    private static final long MAX_BYTES = 2 * 1024 * 1024, RETRY_MS = 2 * 60_000L;
    private static final int MAX_CACHE_IMAGES = 6, MAX_SEARCH_BYTES = 512 * 1024;
    private static final String[] NATURE_QUERIES = {
            "aurora", "coastline", "mountains", "dunes", "island", "ocean", "glacier", "clouds"
    };
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean warming;
    private static final List<Runnable> callbacks = new ArrayList<>();

    static final class Photo {
        final Bitmap bitmap;
        final String credit;
        Photo(Bitmap bitmap, String credit) { this.bitmap = bitmap; this.credit = credit; }
    }

    private static final class Entry {
        final String id, title, credit, url;
        Entry(String id, String title, String credit, String url) {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,100}") || !validAssetUrl(url))
                throw new IllegalArgumentException("NASA asset");
            this.id = id; this.title = clean(title, "NASA image");
            this.credit = clean(credit, "NASA"); this.url = url;
        }
        Entry(JSONObject j) { this(j.optString("id"), j.optString("title"), j.optString("credit"), j.optString("url")); }
        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("title", title).put("credit", credit).put("url", url);
        }
    }

    private Nasa() {}

    private static File directory(Context c) {
        File dir = new File(c.getCacheDir(), "nasa-scenic-v2"); dir.mkdirs(); return dir;
    }
    private static File file(Context c, Entry e) { return new File(directory(c), e.id + ".jpg"); }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("nasa-scenic-v2", Context.MODE_PRIVATE); }

    /** Cached decode only; the live search and download run on one background worker. */
    static Photo photo(Context c, String slot, int w, int h, long period, Runnable ready) {
        SharedPreferences p = prefs(c);
        Entry current = null;
        try {
            String saved = p.getString(slot + ".entry", null);
            if (saved != null) current = new Entry(new JSONObject(saved));
        } catch (Exception ignored) { }
        long now = System.currentTimeMillis();
        long at = p.getLong(slot + ".at", 0), retry = p.getLong(slot + ".retry", 0);
        if (current == null || ((now < at || now - at >= period) && now >= retry))
            warm(c, slot, ready);
        if (current == null) return null;
        Bitmap bitmap = decode(file(c, current), w, h);
        if (bitmap == null) {
            // Missing/evicted cached data is searched and fetched again immediately.
            if (now >= retry) warm(c, slot, ready);
            return null;
        }
        return new Photo(bitmap, current.title + " · " + current.credit);
    }

    private static synchronized void warm(Context context, String slot, Runnable ready) {
        if (ready != null) callbacks.add(ready);
        if (warming) return;
        warming = true;
        Context c = context.getApplicationContext();
        new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            boolean success = false;
            SharedPreferences p = prefs(c);
            try {
                // Retire old selections so an event/radar image cannot survive the new scenic policy.
                for (int version = 1; version <= 4; version++) {
                    File old = new File(c.getCacheDir(), "nasa-live-v" + version);
                    File[] files = old.listFiles();
                    if (files != null) { for (File file : files) file.delete(); old.delete(); }
                }
                File oldScenic = new File(c.getCacheDir(), "nasa-scenic-v1");
                File[] retired = oldScenic.listFiles();
                if (retired != null) { for (File file : retired) file.delete(); oldScenic.delete(); }
                // A retry timestamp left by a killed request must never block the next cold-cache fetch.
                p.edit().remove(slot + ".retry").apply();
                String previous = null;
                try {
                    String raw = p.getString(slot + ".entry", null);
                    if (raw != null) previous = new JSONObject(raw).optString("id", null);
                } catch (Exception ignored) { }
                Entry found = searchAndDownload(c, previous);
                if (found != null) {
                    p.edit().putString(slot + ".entry", found.json().toString())
                            .putLong(slot + ".at", System.currentTimeMillis()).remove(slot + ".retry").apply();
                    trimCache(c, p, found.id);
                    success = true;
                }
            } catch (Exception e) {
                p.edit().putLong(slot + ".retry", System.currentTimeMillis() + RETRY_MS).apply();
                android.util.Log.w("Lumen", "NASA photo search/download failed", e);
            }
            finally {
                ArrayList<Runnable> notify;
                synchronized (Nasa.class) { warming = false; notify = new ArrayList<>(callbacks); callbacks.clear(); }
                if (success) for (Runnable callback : notify) MAIN.post(callback);
            }
        }, "nasa-search").start();
    }

    /** Search live NASA Earth and nature results, then choose a display-sized JPEG rendition. */
    private static Entry searchAndDownload(Context c, String previousId) throws Exception {
        Random random = new Random();
        List<String> queries = new ArrayList<>(java.util.Arrays.asList(NATURE_QUERIES));
        Collections.shuffle(queries, random);
        for (int i = 0; i < 3; i++) {
            Entry entry = searchQuery(c, queries.get(i), previousId, random);
            if (entry != null) return entry;
        }
        throw new IllegalStateException("No scenic NASA photographs found");
    }

    private static Entry searchQuery(Context c, String query, String previousId, Random random) throws Exception {
        JSONObject response = getJson("https://images-api.nasa.gov/search?title=" +
                URLEncoder.encode(query, "UTF-8") + "&center=JSC&year_start=2010&media_type=image&page_size=40&page=1", MAX_SEARCH_BYTES);
        JSONObject collection = response.optJSONObject("collection");
        if (collection == null) return null;
        JSONArray items = collection.optJSONArray("items");
        if (items == null || items.length() == 0) return null;
        ArrayList<JSONObject> candidates = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            JSONArray data = item == null ? null : item.optJSONArray("data");
            JSONObject meta = data == null || data.length() == 0 ? null : data.optJSONObject(0);
            String id = meta == null ? "" : meta.optString("nasa_id");
            if (meta != null && id.matches("[A-Za-z0-9_-]{1,100}") && !id.equals(previousId)
                    && "image".equals(meta.optString("media_type")) && isNatureEarth(meta)) candidates.add(item);
        }
        Collections.shuffle(candidates, random);
        Collections.sort(candidates, (a, b) -> Integer.compare(scenicScore(b), scenicScore(a)));
        // Keep variety within the strongest photographic matches, rather than across the whole archive.
        if (candidates.size() > 12) candidates.subList(12, candidates.size()).clear();
        Collections.shuffle(candidates, random);
        int attempts = 0;
        for (JSONObject item : candidates) {
            if (attempts++ >= 3) break;
            JSONArray data = item.optJSONArray("data");
            JSONObject meta = data.optJSONObject(0);
            String id = meta.optString("nasa_id"), title = meta.optString("title", "NASA image");
            String photographer = meta.optString("photographer", "");
            String center = meta.optString("center", "NASA");
            String credit = photographer.isEmpty() ? center : photographer;
            String image = rendition(item.optJSONArray("links"));
            if (image == null) continue;
            try {
                Entry result = new Entry(id, title, credit, image);
                download(c, result);
                return result;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static boolean isNatureEarth(JSONObject meta) {
        // The modern ISS photography archive gives us actual camera photographs of Earth, rather than
        // NASA's event, planetary, instrument and visualization collections. IDs are discovered live.
        if (!meta.optString("nasa_id").matches("(?i)iss[0-9]{3}[-_a-z0-9]*")) return false;
        return NasaScenic.accepts(meta.optString("title"), meta.optString("description"), meta.optString("keywords"));
    }

    private static int scenicScore(JSONObject item) {
        JSONObject meta = item.optJSONArray("data").optJSONObject(0);
        return NasaScenic.score(meta.optString("title"), meta.optString("description"), meta.optString("keywords"));
    }

    private static String rendition(JSONArray assets) {
        if (assets == null) return null;
        String medium = null, large = null, original = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject item = assets.optJSONObject(i);
            String href = item == null ? "" : item.optString("href");
            if (!validAssetUrl(href) || !href.toLowerCase().matches(".*\\.(jpg|jpeg)(\\?.*)?")) continue;
            String lower = href.toLowerCase();
            if (lower.contains("~medium.")) medium = href;
            else if (lower.contains("~large.")) large = href;
            else if (lower.contains("~orig.")) original = href;
        }
        return large != null ? large : medium != null ? medium : original;
    }

    private static JSONObject getJson(String url, int limit) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000); con.setReadTimeout(12000); con.setInstanceFollowRedirects(false);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent", "Lumen/" + BuildInfo.VERSION + " (Android TV)");
        try {
            if (con.getResponseCode() != 200 || con.getContentLengthLong() > limit) throw new IllegalStateException("NASA API response");
            try (InputStream in = con.getInputStream(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) != -1) { if (out.size() + n > limit) throw new IllegalStateException("NASA API too large"); out.write(buf, 0, n); }
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally { con.disconnect(); }
    }

    private static boolean validAssetUrl(String value) {
        return value != null && value.startsWith("https://images-assets.nasa.gov/image/") && value.length() < 2048;
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private static void trimCache(Context c, SharedPreferences p, String keep) {
        File[] files = directory(c).listFiles((d, name) -> name.endsWith(".jpg") && !name.equals(keep + ".jpg"));
        if (files == null) return;
        // Preserve the active Home and saver images and retain at most two extra recent files.
        java.util.HashSet<String> protectedIds = new java.util.HashSet<>(); protectedIds.add(keep);
        for (String slot : new String[]{"home", "saver"}) try {
            String raw = p.getString(slot + ".entry", null);
            if (raw != null) protectedIds.add(new JSONObject(raw).optString("id"));
        } catch (Exception ignored) { }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        int extras = 0;
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4);
            if (protectedIds.contains(id)) continue;
            if (++extras > MAX_CACHE_IMAGES - protectedIds.size()) f.delete();
        }
    }

    private static void download(Context c, Entry e) throws Exception {
        File target = file(c, e), part = new File(target.getPath() + ".part");
        HttpURLConnection con = (HttpURLConnection) new URL(e.url).openConnection();
        con.setConnectTimeout(8000); con.setReadTimeout(12000); con.setInstanceFollowRedirects(false);
        con.setRequestProperty("User-Agent", "Lumen/" + BuildInfo.VERSION + " (Android TV)");
        try {
            if (con.getResponseCode() != 200 || con.getContentLengthLong() > MAX_BYTES) throw new IllegalStateException("NASA image response");
            try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[16384]; long total = 0; int n;
                while ((n = in.read(buffer)) != -1) { total += n; if (total > MAX_BYTES) throw new IllegalStateException("NASA image too large"); out.write(buffer, 0, n); }
            }
            BitmapFactory.Options size = bounds(part);
            float aspect = size.outWidth / (float) size.outHeight;
            if (!valid(size) || size.outWidth < 1000 || size.outHeight < 600 || aspect < 1.25f || aspect > 2.5f)
                throw new IllegalStateException("NASA image is not a large landscape photograph");
            if (!scenicExposure(part, size)) throw new IllegalStateException("NASA photograph is mostly dark/blank");
            if (!part.renameTo(target)) throw new IllegalStateException("NASA cache write");
        } finally { con.disconnect(); part.delete(); }
    }

    private static BitmapFactory.Options bounds(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true; BitmapFactory.decodeFile(f.getPath(), o); return o;
    }
    private static boolean scenicExposure(File file, BitmapFactory.Options size) {
        BitmapFactory.Options sample = new BitmapFactory.Options();
        sample.inSampleSize = 1;
        while (size.outWidth / (sample.inSampleSize * 2) >= 96) sample.inSampleSize *= 2;
        Bitmap preview = BitmapFactory.decodeFile(file.getPath(), sample);
        if (preview == null) return false;
        int dark = 0, white = 0, count = 0; long light = 0;
        for (int y = 0; y < preview.getHeight(); y += 2) for (int x = 0; x < preview.getWidth(); x += 2) {
            int c = preview.getPixel(x, y), r = android.graphics.Color.red(c), g = android.graphics.Color.green(c), b = android.graphics.Color.blue(c);
            if (Math.max(r, Math.max(g, b)) < 32) dark++;
            if (Math.min(r, Math.min(g, b)) > 242) white++;
            light += (3 * r + 6 * g + b) / 10; count++;
        }
        preview.recycle();
        return count > 0 && dark < count * .5f && white < count * .9f && light / (float) count >= 40;
    }
    private static boolean valid(BitmapFactory.Options o) {
        return o.outWidth > 0 && o.outHeight > 0 && o.outWidth <= 1920 && o.outHeight <= 1280 && "image/jpeg".equals(o.outMimeType);
    }
    private static Bitmap decode(File f, int w, int h) {
        if (!f.exists()) return null;
        BitmapFactory.Options o = bounds(f);
        if (f.length() > MAX_BYTES || !valid(o)) { f.delete(); return null; }
        o.inJustDecodeBounds = false; o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) o.inSampleSize *= 2;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        return b == null ? null : crop(b, w, h);
    }
    private static Bitmap crop(Bitmap src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h && src.isMutable()) return src;
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        int cw = Math.round(w / scale), ch = Math.round(h / scale);
        int cx = (src.getWidth() - cw) / 2, cy = (src.getHeight() - ch) / 2;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new android.graphics.Canvas(out).drawBitmap(src, new android.graphics.Rect(cx, cy, cx + cw, cy + ch),
                new android.graphics.Rect(0, 0, w, h), new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        src.recycle();
        return out;
    }

}
