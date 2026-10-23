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
import java.util.HashSet;
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

    static final class Item {
        String pkg, title, line, intentUri;
        Uri art;
        float progress;       // 0..1, or 0 when there's nothing to resume
        long engaged;         // last engagement, for ordering
        boolean watchNext;
        private String stableFocusKey;

        String focusKey() {
            if (stableFocusKey == null) stableFocusKey = "shelf\n" + pkg + "\n" + title + "\n" + intentUri;
            return stableFocusKey;
        }
    }

    static final class Shelf {
        final String heading, app;  // e.g. "Continue Watching" · "Northfilm"; app is null for Up Next
        final List<Item> items;

        Shelf(String heading, String app, List<Item> items) { this.heading = heading; this.app = app; this.items = items; }
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

    /** One relevance-sorted, interleaved shelf across every app the user left enabled. */
    Shelf forHome(String mode, Set<String> off) {
        if ("off".equals(mode)) return null;
        List<Item> active = new ArrayList<>(), otherWatchNext = new ArrayList<>(), suggested = new ArrayList<>();
        for (Item it : upNext) if (!off.contains(it.pkg)) {
            if (it.progress > 0) active.add(it); else otherWatchNext.add(it);
        }
        List<Item> continuing = new ArrayList<>(active);
        continuing.addAll(otherWatchNext);
        if (!"upnext".equals(mode)) for (Item it : recommendations) if (!off.contains(it.pkg)) suggested.add(it);
        List<Item> picks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, List<Item>> bySource = new LinkedHashMap<>();
        for (Item it : suggested) {
            List<Item> source = bySource.get(it.pkg);
            if (source == null) bySource.put(it.pkg, source = new ArrayList<>());
            if (source.size() < 2) source.add(it);
        }
        List<List<Item>> sources = new ArrayList<>(bySource.values());
        int[] sourceIndex = new int[sources.size()];
        int nextIndex = 0, roundRobin = 0;
        while (picks.size() < 24 && (nextIndex < continuing.size() || !sources.isEmpty())) {
            if (nextIndex < continuing.size()) add(picks, seen, continuing.get(nextIndex++));
            int source = -1, fallback = -1;
            String lastPackage = picks.isEmpty() ? null : picks.get(picks.size() - 1).pkg;
            for (int checked = 0; checked < sources.size(); checked++) {
                int candidate = roundRobin++ % sources.size();
                List<Item> items = sources.get(candidate);
                if (sourceIndex[candidate] >= items.size()) continue;
                if (fallback < 0) fallback = candidate;
                if (lastPackage == null || !lastPackage.equals(items.get(sourceIndex[candidate]).pkg)) { source = candidate; break; }
            }
            if (source < 0) source = fallback;
            if (source >= 0) add(picks, seen, sources.get(source).get(sourceIndex[source]++));
            boolean hasMoreSuggestions = false;
            for (int i = 0; i < sources.size(); i++) if (sourceIndex[i] < sources.get(i).size()) { hasMoreSuggestions = true; break; }
            if (nextIndex >= continuing.size() && !hasMoreSuggestions) break;
        }
        if (picks.isEmpty()) return null;
        return new Shelf("upnext".equals(mode) ? "Continue Watching" : "Top Picks for You", null, picks);
    }

    private static void add(List<Item> picks, Set<String> seen, Item it) {
        String key = it.pkg + "\u0000" + (it.intentUri == null ? it.title : it.intentUri);
        if (seen.add(key)) picks.add(it);
    }

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
    };

    private static String[] withEngagement() {
        String[] p = java.util.Arrays.copyOf(COLUMNS, COLUMNS.length + 1);
        p[COLUMNS.length] = TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS;
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

}
