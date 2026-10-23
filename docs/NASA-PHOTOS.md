# NASA photos

Lumen searches the public [NASA Image and Video Library](https://images.nasa.gov) API for `space` images.
At each rotation, it chooses an image from the current search results and downloads the result's large or medium
JPEG rendition. The result set is live; Lumen does not ship or maintain
a predefined set of image IDs. No account or API key is needed. NASA documents the public search API in its
[API guide](https://images.nasa.gov/docs/images.nasa.gov_api_docs.pdf).

The image title and photographer or NASA center are shown on screen. Lumen keeps the original image file and
metadata, then center-crops it for display, with a legibility gradient or slow screensaver motion. These display
changes do not alter the source. Lumen is independent of NASA; no endorsement is implied. NASA material is
generally not copyrighted in the United States, but third-party credits and exceptions still apply; see
[NASA media guidelines](https://www.nasa.gov/nasa-brand-center/images-and-media/).

- Home searches again hourly or daily, according to **New photo**.
- The screensaver searches every five minutes.
- Search and download run at background priority. Search metadata is capped at 512 KiB; each image is capped
  at 2 MiB and 1920 × 1280 pixels. The private cache retains at most six images and is shared by Home and the
  screensaver. A cached image remains visible if a search fails or the TV is offline.
- Network retries wait 15 minutes. API and image requests have connection/read timeouts and are made only when a
  NASA photo background is in use and its rotation is due.

NASA search results can contain third-party material; displayed attribution is taken from each result's
photographer and center fields. The API's metadata is the source for the label shown by Lumen.
