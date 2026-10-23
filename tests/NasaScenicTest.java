package dev.lumen.tv;

/** Archive regressions that previously admitted events, scientific products and non-Earth images. */
public final class NasaScenicTest {
    private static int checks;
    public static void main(String[] args) {
        reject("Earth Day 2018 Activities", "An outdoor event at the space center.", "Earth, ocean");
        reject("Ocean Day celebration", "", "ocean");
        reject("Cloud researcher at a conference", "", "clouds");
        reject("Earth science research", "A research facility.", "science"); // ice in science, sea in research
        reject("Doppler radar image of ocean clouds", "", "");
        reject("Coastline", "The colors show radar reflectivity.", "");
        reject("Malaspina Glacier", "This false-color ASTER image combines infrared bands.", "");
        reject("SRTM Perspective View of the Coastline", "", "");
        reject("Glacier map", "", "");
        reject("Ocean temperature", "A color-coded data product.", "");
        reject("Jupiter aurora", "Compared with auroras on Earth.", "");
        reject("Earth ocean and Jupiter atmosphere", "", "");
        reject("Desert dunes on Mars", "", "");
        reject("Mountains and Plateaus on Io", "", "");
        reject("VALENT Exercise Atlantic Ocean", "", "");
        reject("Rocket launch over the ocean", "", "");
        reject("SPARTAN spacecraft over the Red Sea coastline", "", "");
        reject("Clouds", "An artist's concept of cloud formation.", "");
        reject("Aurora over Earth", "Solar arrays occupy the foreground.", "");
        accept("Aurora over the Earth", "A photograph taken with a Nikon camera.", "earth photography");
        accept("Coastline of New Zealand", "A natural-color photograph taken by an astronaut aboard the ISS.", "earth");
        accept("Desert dunes", "Wind-shaped sand photographed from orbit.", "landscape");
        accept("Ocean clouds", "This true-color photograph shows clouds over the Pacific.", "earth");
        accept("Earth's limb with an aurora", "A view from the station.", "earth");
        if (NasaScenic.score("Coastline", "A natural-color photograph", "") <= NasaScenic.score("Coastline", "", ""))
            throw new AssertionError("Natural-color photographic records should rank higher");
        System.out.println("PASS: " + (checks + 1) + " scenic NASA selection regressions");
    }
    private static void reject(String title, String description, String tags) { check(false, title, description, tags); }
    private static void accept(String title, String description, String tags) { check(true, title, description, tags); }
    private static void check(boolean expected, String title, String description, String tags) {
        if (NasaScenic.accepts(title, description, tags) != expected) throw new AssertionError(title);
        checks++;
    }
}
