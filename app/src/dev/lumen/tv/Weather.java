package dev.lumen.tv;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.TimeZone;

/**
 * Current weather from Open-Meteo (free, no key, no account), refreshed every 30 minutes. The location comes
 * from a saved city, falling back to an approximate time-zone city. No location permission is needed.
 */
final class Weather {
    interface Listener { void onWeather(); }

    /** A geocoded city. The full label disambiguates places with the same name. */
    static final class Place {
        final String name, label;
        final double latitude, longitude;

        Place(JSONObject j) throws Exception {
            name = j.getString("name");
            latitude = j.getDouble("latitude");
            longitude = j.getDouble("longitude");
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) throw new IllegalArgumentException("coordinates");
            String region = j.optString("admin1", ""), country = j.optString("country", "");
            label = j.optString("label", name + (region.isEmpty() || region.equals(name) ? "" : ", " + region)
                    + (country.isEmpty() ? "" : ", " + country));
        }

        JSONObject json() {
            JSONObject j = new JSONObject();
            try { j.put("name", name).put("label", label).put("latitude", latitude).put("longitude", longitude); }
            catch (Exception e) { throw new IllegalStateException(e); }
            return j;
        }
    }

    static List<Place> search(String query) throws Exception {
        String language = URLEncoder.encode(Locale.getDefault().getLanguage(), "UTF-8");
        JSONObject j = request("https://geocoding-api.open-meteo.com/v1/search?name="
                + URLEncoder.encode(query, "UTF-8") + "&count=5&language=" + language + "&format=json");
        List<Place> places = new ArrayList<>();
        JSONArray results = j.optJSONArray("results");
        if (results != null) for (int i = 0; i < results.length(); i++) places.add(new Place(results.getJSONObject(i)));
        return places;
    }

    private static JSONObject request(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        try (InputStream in = con.getInputStream(); Scanner scanner = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
            return new JSONObject(scanner.hasNext() ? scanner.next() : "");
        } finally { con.disconnect(); }
    }

    /** Main city per time zone: latitude, longitude. Zones not listed show no weather until a city is set. */
    private static final Map<String, float[]> ZONES = new HashMap<>();
    static {
        Object[][] z = {
            {"America/New_York", 40.71f, -74.01f}, {"America/Chicago", 41.88f, -87.63f}, {"America/Denver", 39.74f, -104.99f},
            {"America/Phoenix", 33.45f, -112.07f}, {"America/Los_Angeles", 34.05f, -118.24f}, {"America/Anchorage", 61.22f, -149.9f},
            {"Pacific/Honolulu", 21.31f, -157.86f}, {"America/Detroit", 42.33f, -83.05f}, {"America/Indiana/Indianapolis", 39.77f, -86.16f},
            {"America/Toronto", 43.65f, -79.38f}, {"America/Vancouver", 49.28f, -123.12f}, {"America/Edmonton", 53.55f, -113.49f},
            {"America/Winnipeg", 49.9f, -97.14f}, {"America/Halifax", 44.65f, -63.58f}, {"America/Mexico_City", 19.43f, -99.13f},
            {"America/Sao_Paulo", -23.55f, -46.63f}, {"America/Argentina/Buenos_Aires", -34.6f, -58.38f}, {"America/Bogota", 4.71f, -74.07f},
            {"America/Lima", -12.05f, -77.04f}, {"America/Santiago", -33.45f, -70.67f},
            {"Europe/London", 51.51f, -0.13f}, {"Europe/Dublin", 53.35f, -6.26f}, {"Europe/Paris", 48.86f, 2.35f},
            {"Europe/Berlin", 52.52f, 13.41f}, {"Europe/Madrid", 40.42f, -3.7f}, {"Europe/Lisbon", 38.72f, -9.14f},
            {"Europe/Rome", 41.9f, 12.5f}, {"Europe/Amsterdam", 52.37f, 4.9f}, {"Europe/Brussels", 50.85f, 4.35f},
            {"Europe/Zurich", 47.38f, 8.54f}, {"Europe/Vienna", 48.21f, 16.37f}, {"Europe/Stockholm", 59.33f, 18.07f},
            {"Europe/Oslo", 59.91f, 10.75f}, {"Europe/Copenhagen", 55.68f, 12.57f}, {"Europe/Helsinki", 60.17f, 24.94f},
            {"Europe/Warsaw", 52.23f, 21.01f}, {"Europe/Prague", 50.08f, 14.44f}, {"Europe/Athens", 37.98f, 23.73f},
            {"Europe/Istanbul", 41.01f, 28.98f}, {"Europe/Kyiv", 50.45f, 30.52f}, {"Europe/Moscow", 55.76f, 37.62f},
            {"Asia/Jerusalem", 31.77f, 35.21f}, {"Asia/Dubai", 25.2f, 55.27f}, {"Asia/Kolkata", 19.08f, 72.88f},
            {"Asia/Singapore", 1.35f, 103.82f}, {"Asia/Bangkok", 13.76f, 100.5f}, {"Asia/Jakarta", -6.21f, 106.85f},
            {"Asia/Manila", 14.6f, 120.98f}, {"Asia/Hong_Kong", 22.32f, 114.17f}, {"Asia/Shanghai", 31.23f, 121.47f},
            {"Asia/Taipei", 25.03f, 121.57f}, {"Asia/Seoul", 37.57f, 126.98f}, {"Asia/Tokyo", 35.68f, 139.69f},
            {"Australia/Sydney", -33.87f, 151.21f}, {"Australia/Melbourne", -37.81f, 144.96f}, {"Australia/Brisbane", -27.47f, 153.03f},
            {"Australia/Perth", -31.95f, 115.86f}, {"Pacific/Auckland", -36.85f, 174.76f}, {"Africa/Johannesburg", -26.2f, 28.05f},
            {"Africa/Cairo", 30.04f, 31.24f}, {"Africa/Lagos", 6.52f, 3.38f}, {"Africa/Nairobi", -1.29f, 36.82f},
        };
        for (Object[] e : z) ZONES.put((String) e[0], new float[]{(Float) e[1], (Float) e[2]});
    }

    /** What's shown: a glyph and a temperature. kind is one of sun, moon, partly, cloud, fog, rain, snow, storm. */
    volatile String kind;
    volatile int temp = Integer.MIN_VALUE;
    volatile int high, low;
    volatile String description = "";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final Prefs prefs;
    private String configuration;
    private long nextFetch;
    private int generation;
    private boolean fetching;

    Weather(Prefs prefs, Listener listener) {
        this.prefs = prefs;
        this.listener = listener;
    }

    boolean ready() { return temp != Integer.MIN_VALUE; }

    /** Main-thread entry point. A changed city or unit invalidates any older in-flight response. */
    void refresh() {
        Place city = prefs.city();
        String unit = prefs.get(Prefs.UNITS);
        final boolean fahrenheit = "f".equals(unit) || ("auto".equals(unit) && AppStore.usesFahrenheit());
        float[] zone = ZONES.get(TimeZone.getDefault().getID());
        String config = (city == null ? TimeZone.getDefault().getID() : city.json().toString()) + ":" + fahrenheit;
        if (!config.equals(configuration)) {
            configuration = config;
            generation++;
            fetching = false;
            temp = Integer.MIN_VALUE;
            nextFetch = 0;
            listener.onWeather();
        }
        if (city == null && zone == null) return;
        long now = SystemClock.elapsedRealtime();
        if (fetching || now < nextFetch) return;
        final double lat = city == null ? zone[0] : city.latitude, lon = city == null ? zone[1] : city.longitude;
        final int requestGeneration = generation;
        fetching = true;
        new Thread(() -> {
            try {
                JSONObject j = request("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&forecast_days=1"
                        + "&timezone=auto&temperature_unit=" + (fahrenheit ? "fahrenheit" : "celsius"));
                JSONObject cur = j.getJSONObject("current"), d = j.getJSONObject("daily");
                int code = cur.getInt("weather_code");
                boolean day = cur.optInt("is_day", 1) == 1;
                int hi = rounded(d.getJSONArray("temperature_2m_max").getDouble(0));
                int lo = rounded(d.getJSONArray("temperature_2m_min").getDouble(0));
                int temperature = rounded(cur.getDouble("temperature_2m"));
                main.post(() -> {
                    if (requestGeneration != generation) return;
                    high = hi; low = lo; kind = kindOf(code, day); description = describe(code); temp = temperature;
                    fetching = false;
                    nextFetch = SystemClock.elapsedRealtime() + 30 * 60_000L;
                    listener.onWeather();
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (requestGeneration != generation) return;
                    fetching = false;
                    temp = Integer.MIN_VALUE;
                    nextFetch = SystemClock.elapsedRealtime() + 5 * 60_000L;
                    listener.onWeather();
                });
            }
        }, "weather").start();
    }

    private static int rounded(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("missing temperature");
        return (int) Math.round(value);
    }

    /** WMO weather codes, as Open-Meteo reports them. */
    private static String kindOf(int code, boolean day) {
        if (code == 0) return day ? "sun" : "moon";
        if (code <= 2) return "partly";
        if (code == 3) return "cloud";
        if (code == 45 || code == 48) return "fog";
        if (code >= 95) return "storm";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow";
        return "rain";
    }

    private static String describe(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 95) return "Thunderstorms";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Snow";
        if (code >= 51 && code <= 57) return "Drizzle";
        return "Rain";
    }
}
