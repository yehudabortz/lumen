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

The first slice works: the landscape scene with time-of-day lighting, weather and clock, the glass dock, the Top Shelf
from your apps' Watch Next and channels, All Apps, and moving tiles (long-press OK on a Top Row tile; long-press in All
Apps pins an app). Still to come: the tile menu, settings, cinematic mode, the screensaver and the Inter typeface.
