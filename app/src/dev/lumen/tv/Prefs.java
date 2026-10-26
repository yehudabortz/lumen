package dev.lumen.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** The user's choices, and the list of options the settings panel shows. The first value of each is the default. */
final class Prefs {
    static final String BACKGROUND = "bg", TIME_OF_DAY = "tod", MOTION = "motion", SHELF = "shelf", LABELS = "labels",
            CLOCK = "clock", DATE = "date", WEATHER = "weather", UNITS = "units", PHOTOS = "photos", SAVER = "saver";
    static final String CARD_SIZE = "cardSize", CARD_TEXT = "cardText";
    static final String HIDDEN = "hidden", SHELF_OFF = "shelfOff";

    /** One row of the settings panel. Pages open a list; choices cycle through values. */
    static final class Option {
        final String section, key, label;
        final String[] values, names;

        Option(String section, String key, String label, String[] values, String[] names) {
            this.section = section; this.key = key; this.label = label; this.values = values; this.names = names;
        }

        boolean isPage() { return values == null; }
    }

    static final String PAGE_SHELF_APPS = "page:shelfApps", PAGE_HIDDEN = "page:hidden", PAGE_LOCATION = "page:location";

    static final Option[] OPTIONS = {
        new Option("Appearance", BACKGROUND, "Background",
                new String[]{"nasa", "horizon", "sorbet", "beam", "ember", "aurora", "dusk", "lagoon", "midnight", "citrus",
                        "rose", "graphite", "black"},
                new String[]{"NASA photos", "Horizon", "Sorbet", "Beam", "Ember", "Aurora", "Dusk", "Lagoon", "Midnight",
                        "Citrus", "Rose", "Graphite", "Black"}),
        new Option("Appearance", PHOTOS, "New photo", new String[]{"hour", "day"}, new String[]{"Every hour", "Every day"}),
        new Option("Appearance", TIME_OF_DAY, "Follow time of day", new String[]{"on", "off"}, new String[]{"On", "Off"}),
        new Option("Appearance", MOTION, "Motion", new String[]{"full", "reduced"}, new String[]{"Full", "Reduced"}),
        new Option("Top Shelf", SHELF, "Top Shelf", new String[]{"rich", "upnext", "off"}, new String[]{"Top Picks for You", "Continue Watching", "Off"}),
        new Option("Top Shelf", CARD_SIZE, "Card size", new String[]{"standard", "large", "compact"}, new String[]{"Standard", "Large", "Compact"}),
        new Option("Top Shelf", CARD_TEXT, "Card text", new String[]{"details", "title", "none"}, new String[]{"Title & details", "Title only", "None"}),
        new Option("Top Shelf", PAGE_SHELF_APPS, "Apps in Top Shelf", null, null),
        new Option("Clock & Weather", CLOCK, "Clock", new String[]{"system", "12", "24", "off"}, new String[]{"System", "12-hour", "24-hour", "Off"}),
        new Option("Clock & Weather", DATE, "Show date", new String[]{"off", "on"}, new String[]{"Off", "On"}),
        new Option("Clock & Weather", WEATHER, "Weather", new String[]{"on", "off"}, new String[]{"On", "Off"}),
        new Option("Clock & Weather", PAGE_LOCATION, "Location", null, null),
        new Option("Clock & Weather", UNITS, "Units", new String[]{"auto", "f", "c"}, new String[]{"Auto", "°F", "°C"}),
        new Option("Screensaver", SAVER, "Screensaver", new String[]{"nasa", "scene", "black"}, new String[]{"NASA photos", "Gradients", "Black"}),
        new Option("Apps", LABELS, "Labels", new String[]{"focus", "always", "never"}, new String[]{"On focus", "Always", "Never"}),
        new Option("Apps", PAGE_HIDDEN, "Hidden apps", null, null),
    };

    private final SharedPreferences sp;

    Prefs(Context c) { sp = c.getSharedPreferences("lumen", Context.MODE_PRIVATE); }

    static Option option(String key) {
        for (Option o : OPTIONS) if (o.key.equals(key)) return o;
        throw new IllegalArgumentException(key);
    }

    String get(String key) {
        Option o = option(key);
        String v = legacy(key, sp.getString(key, firstUsable(o)));
        for (String x : o.values) if (x.equals(v)) return v;
        return firstUsable(o);
    }

    /** Whether a row applies right now (photo frequency only matters for a NASA photo background). */
    boolean relevant(Option o) {
        if (CARD_SIZE.equals(o.key) || CARD_TEXT.equals(o.key)) return !is(SHELF, "off");
        if (PHOTOS.equals(o.key)) return is(BACKGROUND, "nasa");
        return true;
    }

    /** Backgrounds saved by 1.0 and earlier, when the drawn ones were landscapes: each keeps its nearest gradient. */
    private static String legacy(String key, String v) {
        if (!BACKGROUND.equals(key) || v == null) return v;
        switch (v) {
            case "alpine": return "midnight";
            case "forest": return "aurora";
            case "dunes": return "horizon";
            case "coast": return "lagoon";
            default: return v;
        }
    }

    private static String firstUsable(Option o) {
        return o.values[0];
    }

    boolean is(String key, String value) { return get(key).equals(value); }

    String name(String key) {
        Option o = option(key);
        String v = get(key);
        for (int i = 0; i < o.values.length; i++) if (o.values[i].equals(v)) return o.names[i];
        return o.names[0];
    }

    /** Moves a choice one step (dir = +1 or -1), wrapping around. */
    void cycle(String key, int dir) {
        Option o = option(key);
        String v = get(key);
        int i = 0;
        while (i < o.values.length && !o.values[i].equals(v)) i++;
        i = (i + dir + o.values.length) % o.values.length;
        sp.edit().putString(key, o.values[i]).apply();
    }

    Weather.Place city() {
        String saved = sp.getString("weatherCity", null);
        if (saved == null) return null;
        try { return new Weather.Place(new org.json.JSONObject(saved)); }
        catch (Exception ignored) { return null; }
    }

    void city(Weather.Place city) {
        sp.edit().putString("weatherCity", city == null ? null : city.json().toString()).apply();
    }

    Set<String> set(String key) { return new HashSet<>(sp.getStringSet(key, Collections.<String>emptySet())); }

    boolean contains(String key, String member) { return set(key).contains(member); }

    void toggle(String key, String member) {
        Set<String> s = set(key);
        if (!s.remove(member)) s.add(member);
        sp.edit().putStringSet(key, s).apply();
    }
}
