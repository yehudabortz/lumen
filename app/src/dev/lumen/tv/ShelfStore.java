package dev.lumen.tv;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Top Shelf's content, read from Android TV's own provider (TvContract): each app's Watch Next items
 * ("continue watching") and the programs on its preview channels (its own recommendations). Lumen adds
 * nothing of its own. Reading other apps' rows needs READ_TV_LISTINGS; without it the shelf is simply empty.
 */
final class ShelfStore {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";

    static final class Item extends ShelfRows.Program {
        String line;
        Uri art;
        private String stableFocusKey;

        String focusKey() {
            if (stableFocusKey == null) stableFocusKey = "shelf\n" + pkg + "\n" + title + "\n" + intentUri;
            return stableFocusKey;
        }
    }

    static final class Shelf {
        final String id, heading;  // a ShelfRows id and its title, e.g. "movies" · "Movies"
        final List<Item> items;

        Shelf(String id, String heading, List<Item> items) { this.id = id; this.heading = heading; this.items = items; }
    }

    /** Watch Next for every app, newest first: continue, then next episode, then watchlist. */
    final List<Item> upNext = new ArrayList<>();
    /** Preview-channel suggestions in provider order. */
    final List<Item> recommendations = new ArrayList<>();
    /** Per package: its Watch Next items, then its preview programs. */
    final Map<String, List<Item>> byApp = new HashMap<>();

    static boolean allowed(Context c) {
        return c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    ShelfStore load(Context c) {
        upNext.clear();
        recommendations.clear();
        byApp.clear();
        if (!allowed(c)) return this;
        ContentResolver cr = c.getContentResolver();
        List<Item> next = query(cr, TvContract.WatchNextPrograms.CONTENT_URI, true);
        Collections.sort(next, (a, b) -> Long.compare(b.engaged, a.engaged));
        upNext.addAll(next);
        for (Item it : next) list(it.pkg).add(it);
        for (Item it : query(cr, TvContract.PreviewPrograms.CONTENT_URI, false)) {
            List<Item> l = list(it.pkg);
            if (l.size() < 16) { l.add(it); recommendations.add(it); }
        }
        removeUnavailableApps(c, upNext);
        removeUnavailableApps(c, recommendations);
        return this;
    }

    /** One row of the shelf, or null when it has nothing to show right now. */
    Shelf row(String id, Set<String> off, ShelfRows.Labels labels) {
        List<Item> items = ShelfRows.build(id, upNext, recommendations, off);
        return items.isEmpty() ? null : new Shelf(id, ShelfRows.title(id, labels), items);
    }

    int count(String id, Set<String> off) { return ShelfRows.build(id, upNext, recommendations, off).size(); }

    private static void removeUnavailableApps(Context c, List<Item> items) {
        java.util.Iterator<Item> iterator = items.iterator();
        while (iterator.hasNext()) {
            Item it = iterator.next();
            try { if (!c.getPackageManager().getApplicationInfo(it.pkg, 0).enabled) iterator.remove(); }
            catch (Exception ignored) { iterator.remove(); }
        }
    }

    private List<Item> list(String pkg) {
        List<Item> l = byApp.get(pkg);
        if (l == null) byApp.put(pkg, l = new ArrayList<>());
        return l;
    }

    /** Only the columns the shelf uses; both program tables share these names. */
    private static final String[] COLUMNS = {
            TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME, TvContract.WatchNextPrograms.COLUMN_TITLE,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE, TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
            TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
            TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_TYPE, TvContract.WatchNextPrograms.COLUMN_LIVE,
    };

    private static String[] withEngagement() {
        String[] p = java.util.Arrays.copyOf(COLUMNS, COLUMNS.length + 2);
        p[COLUMNS.length] = TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS;
        p[COLUMNS.length + 1] = TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE;
        return p;
    }

    private static List<Item> query(ContentResolver cr, Uri uri, boolean watchNext) {
        List<Item> out = new ArrayList<>();
        // Without ACCESS_ALL_EPG_DATA the provider refuses any selection, so filter here instead.
        String[] projection = watchNext ? withEngagement() : COLUMNS;
        try (Cursor cur = cr.query(uri, projection, null, null, null)) {
            if (cur == null) return out;
            Map<String, Integer> col = new LinkedHashMap<>();
            for (String name : cur.getColumnNames()) col.put(name, cur.getColumnIndex(name));
            while (cur.moveToNext()) {
                Item it = new Item();
                it.watchNext = watchNext;
                it.pkg = str(cur, col, TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME);
                it.title = str(cur, col, TvContract.WatchNextPrograms.COLUMN_TITLE);
                it.intentUri = str(cur, col, TvContract.WatchNextPrograms.COLUMN_INTENT_URI);
                if (it.pkg == null || it.title == null || it.intentUri == null) continue;
                String thumb = str(cur, col, TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI);
                String poster = str(cur, col, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI);
                String a = thumb != null ? thumb : poster;
                it.art = a == null ? null : Uri.parse(a);
                long pos = longOf(cur, col, TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS);
                long dur = longOf(cur, col, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS);
                it.progress = pos > 0 && dur > 0 ? Math.min(1f, pos / (float) dur) : 0;
                it.engaged = watchNext ? longOf(cur, col, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS) : 0;  // only when present
                it.duration = dur;
                it.nextType = intOf(cur, col, TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE);
                it.type = intOf(cur, col, TvContract.WatchNextPrograms.COLUMN_TYPE);
                it.live = intOf(cur, col, TvContract.WatchNextPrograms.COLUMN_LIVE) == 1;
                it.line = line(cur, col, pos, dur);
                out.add(it);
            }
        } catch (RuntimeException e) {
            // SecurityException if the permission was revoked, or a provider quirk: an empty shelf, not a crash.
        }
        return out;
    }

    /** "S2 · E4 · 22 min left", "Film · 1 h 48 min", or the episode title. */
    private static String line(Cursor cur, Map<String, Integer> col, long pos, long dur) {
        StringBuilder sb = new StringBuilder();
        String season = str(cur, col, TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER);
        String episode = str(cur, col, TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER);
        if (season != null) sb.append('S').append(season);
        if (episode != null) sb.append(sb.length() > 0 ? " · " : "").append('E').append(episode);
        if (sb.length() == 0) {
            String ep = str(cur, col, TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE);
            if (ep != null) sb.append(ep);
        }
        if (dur > 0) {
            long left = pos > 0 ? dur - pos : dur;
            long min = Math.max(1, left / 60000);
            String t = min >= 60 ? (min / 60) + " h " + String.format("%02d", min % 60) + " min" : min + " min";
            sb.append(sb.length() > 0 ? " · " : "").append(t).append(pos > 0 ? " left" : "");
        }
        return sb.toString();
    }

    private static String str(Cursor c, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        if (i == null || i < 0 || c.isNull(i)) return null;
        String s = c.getString(i);
        return s == null || s.isEmpty() ? null : s;
    }

    private static long longOf(Cursor c, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        return i == null || i < 0 || c.isNull(i) ? 0 : c.getLong(i);
    }

    /** An integer column, or -1 when the app left it unset. */
    private static int intOf(Cursor c, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        return i == null || i < 0 || c.isNull(i) ? -1 : c.getInt(i);
    }

}
