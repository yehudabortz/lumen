package dev.lumen.tv;

import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative photographic subjects for a quiet wallpaper, not the whole NASA archive. */
final class NasaScenic {
    private static final Pattern PUNCTUATION = Pattern.compile("[^a-z0-9]+");
    private static final String[] SCENERY = {
            "aurora", "auroras", "aurorae", "ocean", "oceans", "coast", "coastline", "coastlines",
            "mountain", "mountains", "glacier", "glaciers", "island", "islands", "reef", "reefs",
            "desert", "dunes", "cloud", "clouds", "forest", "forests", "river", "rivers", "lake", "lakes",
            "waterfall", "waterfalls", "snow", "ice", "landscape", "landscapes", "sunrise", "sunset",
            "blue marble", "earth s limb", "earth horizon", "earth from orbit", "earth from space"
    };
    private static final String[] EVENTS_AND_PEOPLE = {
            "earth day", "ocean day", "open house", "activities", "celebration", "celebrations", "celebrating",
            "event", "events", "festival", "conference", "symposium", "workshop", "ceremony", "awards",
            "outreach", "press", "exhibit", "exhibition", "employees", "staff", "student", "students",
            "education", "educational", "tour", "volunteer", "volunteers", "portrait", "portraits",
            "people", "personnel", "scientist", "scientists", "engineer", "engineers", "researcher", "researchers",
            "group photo", "crew portrait", "astronaut portrait", "posing", "ribbon", "anniversary", "meeting",
            "presentation", "demonstration", "classroom", "graduation", "intern", "interns", "exercise", "exercises",
            "training", "rescue", "recovery", "diver", "divers", "navy", "military", "evacuation", "drill", "drills",
            "testing", "test", "tests", "operations", "operation", "splashdown"
    };
    private static final String[] SCIENCE_PRODUCTS = {
            "doppler", "radar", "radars", "reflectivity", "lidar", "sonar", "false color", "color coded",
            "pseudocolor", "infrared", "thermal", "spectral", "wavelength", "elevation", "topographic",
            "topography", "srtm", "aster", "synthetic", "anomaly", "data product", "visualization",
            "diagram", "diagrams", "schematic", "infographic", "map", "maps", "mapping", "chart", "charts",
            "graph", "graphs", "model", "models", "modeling", "rendering", "illustration", "illustrations",
            "artist", "artists", "simulation", "simulated", "computer generated", "concept", "concepts",
            "enhanced color", "color enhanced", "perspective view", "landsat overlay", "cross section"
    };
    private static final String[] HARDWARE = {
            "launch", "launches", "rocket", "rockets", "rollback", "rollout", "spacecraft", "spartan",
            "freeflying", "free flying", "payload", "capsule", "solar array", "solar arrays", "solar panel",
            "antenna", "antennas", "instrument", "instruments", "laboratory", "facility", "facilities",
            "building", "aircraft", "helicopter", "runway", "vehicle", "space center", "spacesuit", "spacewalk"
    };
    private static final String[] OTHER_WORLDS = {
            "mars", "martian", "jupiter", "jovian", "saturn", "venus", "mercury", "neptune", "uranus", "pluto",
            "europa", "enceladus", "titan", "exoplanet", "lunar", "io"
    };

    static boolean accepts(String title, String description, String keywords) {
        String subject = normalize(title), catalog = subject + normalize(keywords);
        String all = catalog + normalize(description);
        if (!any(subject, SCENERY)) return false;
        if (any(catalog, EVENTS_AND_PEOPLE) || any(all, HARDWARE)) return false;
        if (any(all, SCIENCE_PRODUCTS) || any(all, OTHER_WORLDS)) return false;
        // A generic satellite/hardware subject is not a landscape, even with "ocean" in its caption.
        if (has(subject, "satellite") && !(has(subject, "satellite view") || has(subject, "satellite image")
                || has(subject, "satellite sees") || has(subject, "satellite captures"))) return false;
        return true;
    }

    static int score(String title, String description, String keywords) {
        String all = normalize(title) + normalize(description) + normalize(keywords);
        int score = 0;
        if (has(all, "natural color") || has(all, "true color")) score += 5;
        if (has(all, "photograph") || has(all, "photography") || has(all, "photographed")) score += 4;
        if (has(all, "nikon") || has(all, "canon") || has(all, "camera")) score += 2;
        if (has(normalize(title), "aurora") || has(normalize(title), "coastline") || has(normalize(title), "dunes")) score += 2;
        return score;
    }

    private static String normalize(String text) {
        return " " + PUNCTUATION.matcher(text == null ? "" : text.toLowerCase(Locale.US)).replaceAll(" ").trim() + " ";
    }
    private static boolean has(String text, String term) { return text.contains(" " + term + " "); }
    private static boolean any(String text, String[] terms) {
        for (String term : terms) if (has(text, term)) return true;
        return false;
    }
    private NasaScenic() {}
}
