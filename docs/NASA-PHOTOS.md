# NASA photos

Lumen searches the public [NASA Image and Video Library](https://images.nasa.gov) for modern **ISS camera
photographs of Earth**, with scenic subjects such as coastlines, mountains, islands, dunes, oceans, glaciers,
clouds and auroras. Searches target the title field in Johnson Space Center's archive from 2010 onward, and
require an ISS photographic record with a scenic subject in its title. General event and planetary collections
are excluded. Results still come from live searches at each rotation; no image IDs are predefined.

The selector rejects people/events/training, launch and spacecraft hardware, radar and Doppler products,
false-color/infrared imagery, maps, models, diagrams and other-world terrain. Natural-color and photographic
records rank higher, with variety retained within the strongest matches. New downloads must be landscape
JPEGs at least 1000 × 600 pixels, at most 1920 × 1280 and 2 MiB. A small sampled exposure check rejects
mostly black or blank frames. Search, selection, downloading and this check run in the background. These
conservative catalog and exposure checks improve the pool; they cannot measure subjective beauty perfectly.

No account or API key is needed. NASA documents the public search fields in its
[API guide](https://images.nasa.gov/docs/images.nasa.gov_api_docs.pdf).

The image title and photographer or NASA center are shown on screen. Lumen keeps the original image file and
metadata, then center-crops it for display, with a legibility gradient or slow screensaver motion. These display
changes do not alter the source. Lumen is independent of NASA; no endorsement is implied. NASA material is
generally not copyrighted in the United States, but third-party credits and exceptions still apply; see
[NASA media guidelines](https://www.nasa.gov/nasa-brand-center/images-and-media/).

- Home searches again hourly or daily, according to **New photo**.
- The screensaver searches every five minutes.
- Selecting NASA photos starts a search immediately. On a cold cache, Lumen keeps the landscape visible until
  the first result downloads, then replaces it with the NASA image.
- Search and download run at background priority. Search metadata is capped at 512 KiB; each image is capped
  at 2 MiB and 1920 × 1280 pixels. The private cache retains at most six images and is shared by Home and the
  screensaver. A cached image remains visible if a search fails or the TV is offline.
- Failed network requests retry after two minutes. API and image requests have connection/read timeouts and are
  made only when a NASA photo background is in use and its rotation is due.

NASA search results can contain third-party material; displayed attribution is taken from each result's
photographer or NASA center field. The API metadata is the source for the label shown by Lumen.

The scenic policy uses a fresh cache namespace, so previously selected event or technical images are discarded
on upgrade. Old cache files are removed by the background worker. Offline fallback remains the last good
photo from the new scenic collection, or the drawn landscape while the first eligible photo loads.
