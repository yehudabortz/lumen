package dev.lumen.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** What each Top Shelf row collects, and how saved rows are read back. */
public final class ShelfRowsTest {
    private static int checks;

    public static void main(String[] args) {
        List<ShelfRows.Program> next = new ArrayList<>(), previews = new ArrayList<>();
        ShelfRows.Program started = watchNext("a", "Started", ShelfRows.NEXT_CONTINUE, .4f, 60),
                almost = watchNext("b", "Almost done", ShelfRows.NEXT_CONTINUE, .9f, 60),
                episode = watchNext("a", "Next episode", ShelfRows.NEXT_NEXT, 0, 45),
                fresh = watchNext("c", "New episode", ShelfRows.NEXT_NEW, 0, 40),
                saved = watchNext("b", "Saved film", ShelfRows.NEXT_WATCHLIST, 0, 120);
        started.type = ShelfRows.TYPE_EPISODE; almost.type = ShelfRows.TYPE_MOVIE; saved.type = ShelfRows.TYPE_MOVIE;
        next.addAll(Arrays.asList(started, almost, episode, fresh, saved));
        ShelfRows.Program film = preview("a", "Film", ShelfRows.TYPE_MOVIE, 100), clip = preview("c", "Clip", ShelfRows.TYPE_CLIP, 8),
                series = preview("b", "Series", ShelfRows.TYPE_SERIES, 0), game = preview("c", "Game", ShelfRows.TYPE_EVENT, 180);
        game.live = true;
        previews.addAll(Arrays.asList(film, clip, series, game));
        Set<String> none = Collections.emptySet();

        expect(ShelfRows.CONTINUE, next, previews, none, started, almost);
        expect(ShelfRows.NEXT, next, previews, none, episode);
        expect(ShelfRows.NEW, next, previews, none, fresh);
        expect(ShelfRows.WATCHLIST, next, previews, none, saved);
        expect(ShelfRows.MOVIES, next, previews, none, almost, saved, film);
        expect(ShelfRows.SHOWS, next, previews, none, started, series);
        expect(ShelfRows.LIVE, next, previews, none, game);
        expect(ShelfRows.ALMOST, next, previews, none, almost);
        expect(ShelfRows.SHORT, next, previews, none, almost, clip);  // 6 of 60 min left; an 8 min clip
        expect(ShelfRows.APP_PREFIX + "a", next, previews, none, started, episode, film);
        expect(ShelfRows.MOVIES, next, previews, new HashSet<>(Collections.singletonList("b")), film);  // switched off
        expect(ShelfRows.APP_PREFIX + "b", next, previews, new HashSet<>(Collections.singletonList("b")));

        // Top Picks: what you're watching first, then suggestions interleaved without repeating an app back to back
        List<ShelfRows.Program> picks = ShelfRows.build(ShelfRows.PICKS, next, previews, none);
        check(picks.get(0) == started && picks.contains(film) && picks.contains(game), "picks mix watching and suggestions");
        for (int i = 1; i < picks.size(); i++)
            if (!picks.get(i).watchNext && !picks.get(i - 1).watchNext) check(!picks.get(i).pkg.equals(picks.get(i - 1).pkg), "picks alternate apps");

        // Duplicates of the same program (same app and link) appear once
        ShelfRows.Program twin = preview("a", "Film", ShelfRows.TYPE_MOVIE, 100);
        List<ShelfRows.Program> withTwin = new ArrayList<>(previews);
        withTwin.add(twin);
        check(ShelfRows.build(ShelfRows.MOVIES, next, withTwin, none).size() == 3, "a repeated program shows once");

        check(ShelfRows.appRows(next, previews, none).equals(Arrays.asList("app:a", "app:b", "app:c")), "app rows in first-seen order");
        check(ShelfRows.parse("picks, movies,bogus,picks,app:,app:x").equals(Arrays.asList("picks", "movies", "app:x")), "parse drops unknown and repeats");
        check(ShelfRows.join(ShelfRows.parse("continue,next")).equals("continue,next"), "join round-trips");
        check(ShelfRows.title("app:x", pkg -> "Northfilm").equals("From Northfilm"), "app row title");
        check(ShelfRows.title("app:x", pkg -> null).equals("From an app"), "app row title without a label");
        for (String id : ShelfRows.VIEWING) check(ShelfRows.known(id) && !ShelfRows.describe(id, null).isEmpty(), "described: " + id);
        for (String id : ShelfRows.MIXES) check(ShelfRows.known(id) && !ShelfRows.describe(id, null).isEmpty(), "described: " + id);
        System.out.println("PASS: " + checks + " Top Shelf row checks");
    }

    private static ShelfRows.Program watchNext(String pkg, String title, int nextType, float progress, int minutes) {
        ShelfRows.Program p = program(pkg, title, minutes);
        p.watchNext = true;
        p.nextType = nextType;
        p.progress = progress;
        return p;
    }

    private static ShelfRows.Program preview(String pkg, String title, int type, int minutes) {
        ShelfRows.Program p = program(pkg, title, minutes);
        p.type = type;
        return p;
    }

    private static ShelfRows.Program program(String pkg, String title, int minutes) {
        ShelfRows.Program p = new ShelfRows.Program();
        p.pkg = pkg;
        p.title = title;
        p.intentUri = "lumen://" + pkg + "/" + title;
        p.duration = minutes * 60_000L;
        return p;
    }

    private static void expect(String id, List<ShelfRows.Program> next, List<ShelfRows.Program> previews, Set<String> off, ShelfRows.Program... want) {
        check(ShelfRows.build(id, next, previews, off).equals(Arrays.asList(want)), id);
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
    }
}
