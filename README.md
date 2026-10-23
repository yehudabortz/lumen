# Lumen

A calm, clean and minimal home screen for Android TV and Google TV: your apps on a glass dock, what you're watching
from those apps, the time and the weather, over a landscape that follows the time of day. No ads, ever: everything on
screen comes from apps already on your TV.

![Lumen home screen (design mockup)](docs/design/screens/home.jpg)

**Design:** see [`docs/design/DESIGN.md`](docs/design/DESIGN.md) for the layout, the screens, where the
content comes from and the full list of options, and open [`docs/design/mockup.html`](docs/design/mockup.html) in a
browser for an interactive mockup (arrow keys and Enter act as the remote).

## Building

No Gradle, no libraries: [`build.sh`](build.sh) runs the Android SDK tools directly and makes `out/Lumen.apk`.

You need a JDK (17 or newer) and the Android SDK's `build-tools;35.0.0` and `platforms;android-34`. On a Mac:

```sh
brew install openjdk@21 android-commandlinetools
sdkmanager "build-tools;35.0.0" "platforms;android-34"
./build.sh
```

**NASA photos need no setup:** choose **Settings → Appearance → Background → NASA photos**. The screensaver
also offers NASA photos. Lumen searches NASA's live image library at each rotation and downloads a result into a
small shared cache, with title and attribution on screen. Downloads and decoding happen in the background; no key
or account is needed. The last good image stays visible offline.
See [photo credits and sources](docs/NASA-PHOTOS.md).

Optional: to let the background and screensaver use Unsplash photos, create a free app at
[unsplash.com/developers](https://unsplash.com/developers) and add its access key to `local.properties`
(git-ignored) before building:

```sh
echo "unsplash.access_key=YOUR_ACCESS_KEY" >> local.properties
```

The first build creates a signing key, `lumen.keystore`, and stores its password in `local.properties`. Both are
git-ignored. Keep them: updates must be signed with the same key.

## Installing on a TV

Turn on Developer options and USB or wireless debugging on the TV, then:

```sh
adb connect <tv-ip>:5555
adb install -r out/Lumen.apk
adb shell cmd package set-home-activity dev.lumen.tv/.HomeActivity
```

On Google TV the stock home screen outranks any other launcher, so `set-home-activity` alone may not stick. See
[`docs/design/DESIGN.md`](docs/design/DESIGN.md) for the details. The first time Lumen opens it asks for permission
to read TV listings: that's what fills the Top Shelf with your apps' "continue watching" and recommendations.

## Status

Working on a real TV: landscapes with time-of-day lighting (or Unsplash photos), weather and clock, the glass dock,
Top Picks for You mixing Watch Next and recommendations across apps, All Apps, the tile menu (long-press OK),
settings as a dropdown from the status bar (Up to the clock, then OK), and the screensaver (Back from the home dock).
To make Lumen the screensaver on Google TV, where
third-party screensavers are hidden from Settings:

```sh
adb shell settings put secure screensaver_components dev.lumen.tv/.Screensaver
```

For local weather, open **Settings → Clock & Weather → Location**, search for a city or postal code, and
choose the matching city. Home and the screensaver use the same saved location. Until you choose one, weather
uses an approximate city from the TV’s time zone. No location permission or API key is required.

Still to come: cinematic mode, tile sizes and the Inter typeface.
