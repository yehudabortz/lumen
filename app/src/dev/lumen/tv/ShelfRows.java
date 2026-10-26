package dev.lumen.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Top Shelf's row types and what each one shows. Plain Java (no Android types) so the rules run in fast JVM
 * tests; ShelfStore fills Programs from Android TV's provider and HomeView draws whichever row is chosen.
 * Rows are named by id: fixed ids below, or APP_PREFIX + package for one app's own row.
 */
final class ShelfRows {
    /** One program from Watch Next or a preview channel. ShelfStore.Item adds its artwork and detail line. */
    static class Program {
        String pkg, title, intentUri;
        float progress;       // 0..1, or 0 when there's nothing to resume
        long engaged;         // last engagement, for ordering
        long duration;        // ms, or 0 when unknown
        boolean watchNext, live;
        int nextType = -1;    // TvContract WATCH_NEXT_TYPE_*, for Watch Next items
        int type = -1;        // TvContract program TYPE_*, when the app sets one
    }

    // TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_* and the shared program TYPE_* values
    static final int NEXT_CONTINUE = 0, NEXT_NEXT = 1, NEXT_NEW = 2, NEXT_WATCHLIST = 3;
    static final int TYPE_MOVIE = 0, TYPE_SERIES = 1, TYPE_SEASON = 2, TYPE_EPISODE = 3, TYPE_CLIP = 4, TYPE_EVENT = 5;

    static final String PICKS = "picks", CONTINUE = "continue", NEXT = "next", NEW = "new", WATCHLIST = "watchlist",
            MOVIES = "movies", SHOWS = "shows", LIVE = "live", ALMOST = "almost", SHORT = "short", APP_PREFIX = "app:";

    /** The settings checklist's groups. Rows always show in this order, so there is nothing to arrange. */
    static final String[] VIEWING = {CONTINUE, NEXT, NEW, WATCHLIST}, DISCOVER = {PICKS, MOVIES, SHOWS, LIVE, ALMOST, SHORT};

    /** The rows a fresh install shows. */
    static final List<String> DEFAULT = Collections.unmodifiableList(Arrays.asList(CONTINUE, NEXT, PICKS));

    private static final long SHORT_MS = 30 * 60_000L;
    private static final int LIMIT = 24;

    interface Labels { String app(String pkg); }

    static String title(String id, Labels labels) {
        switch (id) {
            case PICKS: return "Top Picks for You";
            case CONTINUE: return "Continue Watching";
            case NEXT: return "Up Next";
            case NEW: return "New Episodes";
            case WATCHLIST: return "Watchlist";
            case MOVIES: return "Movies";
            case SHOWS: return "Shows";
            case LIVE: return "Live Now";
            case ALMOST: return "Almost Finished";
            case SHORT: return "Short Watches";
        }
        if (id.startsWith(APP_PREFIX)) {
            String label = labels == null ? null : labels.app(id.substring(APP_PREFIX.length()));
            return "From " + (label == null ? "an app" : label);
        }
        return id;
    }

    static boolean known(String id) {
        if (id.startsWith(APP_PREFIX)) return id.length() > APP_PREFIX.length();
        for (String s : VIEWING) if (s.equals(id)) return true;
        for (String s : DISCOVER) if (s.equals(id)) return true;
        return false;
    }

    /** "picks,continue" to the checklist's rows in their fixed order, dropping unknown, app and repeated ids. */
    static List<String> parse(String saved) {
        List<String> chosen = new ArrayList<>(), out = new ArrayList<>();
        if (saved != null) for (String id : saved.split(",")) chosen.add(id.trim());
        for (String[] group : new String[][]{VIEWING, DISCOVER}) for (String id : group) if (chosen.contains(id)) out.add(id);
        return out;
    }

    static String join(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) sb.append(sb.length() > 0 ? "," : "").append(id);
        return sb.toString();
    }

    /**
     * The items of one row. `next` is Watch Next, newest engagement first; `previews` are the apps' own suggestions
     * in provider order; packages in `off` (hidden, or switched off for the shelf) never appear.
     */
    static <T extends Program> List<T> build(String id, List<T> next, List<T> previews, Set<String> off) {
        List<T> out = new ArrayList<>();
        if (PICKS.equals(id)) return picks(next, previews, off);
        if (id.startsWith(APP_PREFIX)) {
            String pkg = id.substring(APP_PREFIX.length());
            if (off.contains(pkg)) return out;
            for (T p : next) if (pkg.equals(p.pkg)) add(out, p);
            for (T p : previews) if (pkg.equals(p.pkg)) add(out, p);
            return cap(out);
        }
        List<T> all = new ArrayList<>(next);
        all.addAll(previews);
        for (T p : all) if (!off.contains(p.pkg) && matches(id, p)) add(out, p);
        return cap(out);
    }

    static boolean matches(String id, Program p) {
        switch (id) {
            case CONTINUE: return p.watchNext && (p.nextType == NEXT_CONTINUE || p.progress > 0);
            case NEXT: return p.watchNext && p.nextType == NEXT_NEXT;
            case NEW: return p.watchNext && p.nextType == NEXT_NEW;
            case WATCHLIST: return p.watchNext && p.nextType == NEXT_WATCHLIST;
            case MOVIES: return p.type == TYPE_MOVIE;
            case SHOWS: return p.type == TYPE_SERIES || p.type == TYPE_SEASON || p.type == TYPE_EPISODE;
            case LIVE: return p.live;
            case ALMOST: return p.progress >= .75f && p.progress < .98f;
            case SHORT: return p.duration > 0 && remaining(p) <= SHORT_MS;
        }
        return false;
    }

    /** Time left to watch: the rest of a started program, or all of an unstarted one. */
    static long remaining(Program p) { return Math.round(p.duration * (1 - p.progress)); }

    /**
     * Top Picks: what you're watching, interleaved with at most two suggestions per app, round-robin and never the
     * same app twice in a row when another can go next.
     */
    static <T extends Program> List<T> picks(List<T> next, List<T> previews, Set<String> off) {
        List<T> active = new ArrayList<>(), rest = new ArrayList<>();
        for (T p : next) if (!off.contains(p.pkg)) (p.progress > 0 ? active : rest).add(p);
        List<T> continuing = new ArrayList<>(active);
        continuing.addAll(rest);
        Map<String, List<T>> bySource = new LinkedHashMap<>();
        for (T p : previews) {
            if (off.contains(p.pkg)) continue;
            List<T> source = bySource.get(p.pkg);
            if (source == null) bySource.put(p.pkg, source = new ArrayList<>());
            if (source.size() < 2) source.add(p);
        }
        List<List<T>> sources = new ArrayList<>(bySource.values());
        int[] sourceIndex = new int[sources.size()];
        List<T> picks = new ArrayList<>();
        int nextIndex = 0, roundRobin = 0;
        while (picks.size() < LIMIT && (nextIndex < continuing.size() || !sources.isEmpty())) {
            if (nextIndex < continuing.size()) add(picks, continuing.get(nextIndex++));
            int source = -1, fallback = -1;
            String lastPackage = picks.isEmpty() ? null : picks.get(picks.size() - 1).pkg;
            for (int checked = 0; checked < sources.size(); checked++) {
                int candidate = roundRobin++ % sources.size();
                List<T> items = sources.get(candidate);
                if (sourceIndex[candidate] >= items.size()) continue;
                if (fallback < 0) fallback = candidate;
                if (lastPackage == null || !lastPackage.equals(items.get(sourceIndex[candidate]).pkg)) { source = candidate; break; }
            }
            if (source < 0) source = fallback;
            if (source >= 0) add(picks, sources.get(source).get(sourceIndex[source]++));
            boolean more = false;
            for (int i = 0; i < sources.size(); i++) if (sourceIndex[i] < sources.get(i).size()) { more = true; break; }
            if (nextIndex >= continuing.size() && !more) break;
        }
        return picks;
    }

    /** Adds unless the same program (by app and link) is already in the row. */
    private static <T extends Program> void add(List<T> row, T p) {
        String key = p.pkg + "\u0000" + (p.intentUri == null ? p.title : p.intentUri);
        for (T other : row) if (key.equals(other.pkg + "\u0000" + (other.intentUri == null ? other.title : other.intentUri))) return;
        row.add(p);
    }

    private static <T> List<T> cap(List<T> row) { return row.size() > LIMIT ? new ArrayList<>(row.subList(0, LIMIT)) : row; }

    private ShelfRows() {}
}
