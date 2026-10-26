# Privacy

Lumen has no advertising, analytics SDK, account system or Lumen-operated backend. It does not upload your
watch history or installed-app list to a Lumen service.

## Stored on the device

Settings, favorites, hidden apps, the selected weather city, and a small photo cache are stored in Android's
private app storage. Android TV's listings provider holds Watch Next and recommendation records published
by your installed apps; Lumen reads those records after you grant permission. It launches the apps' supplied
links when you select content. Lumen disables Android app backup. Uninstalling removes its own settings and cache,
but does not erase other apps' data or Android TV's listings.

## Network requests

- **Weather and location:** city/postal-code search text is sent to Open-Meteo's geocoding API. Weather requests
  contain the selected city's coordinates, or an approximate city chosen from the device's time zone. Lumen
  does not request GPS/location permission or infer location from your public IP. The providers still receive
  ordinary connection information, including your IP address. See [Open-Meteo terms and privacy](https://open-meteo.com/en/terms).
- **NASA backgrounds:** scenic search terms and image requests go to NASA's public image library and asset
  hosts when NASA backgrounds are enabled. NASA receives ordinary connection information. No API key or
  streaming-account information is sent. See [NASA privacy](https://www.nasa.gov/privacy/).
- **App artwork:** Lumen may fetch program artwork from URLs supplied by installed apps. Those hosts receive
  the image request and its connection information. The installed apps and linked services have their own
  privacy policies. Their behavior is outside Lumen's control.
- **Source links:** opening an attribution link uses an available browser; the destination's policy applies.

Drawing a landscape or using a cached image needs no photo download. Turning Weather off stops new scheduled
weather requests; a request already in progress may finish. Photo caches remain until Android clears the app's
cache or you uninstall. Screenshot tooling is separate from the production APK and uses public sample content
in an emulator; it must never be pointed at a personal TV.

## Permissions

`INTERNET` loads weather and artwork. TV-listings permissions read other apps' published recommendations.
Lumen does not request microphone, camera, contacts or device location access.

For a privacy or security concern, follow [SECURITY.md](SECURITY.md). Please do not attach personal screenshots,
device addresses, watch history, credentials or unredacted logs to public issues.
