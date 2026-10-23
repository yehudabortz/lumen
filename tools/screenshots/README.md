# Native screenshot capture

These captures come from **Lumen's production Android activity and Canvas renderer**, running in a clean
1920 × 1080 Google TV emulator. The separate instrumentation APK supplies public sample content and drives
the same settings, menus, scrolling, focus and screensaver used by the installed app. It is never included in
`out/Lumen.apk`.

The app tiles use public artwork from the publishers' Google Play listings. YouTube uses the actual TV banner
from the app bundled with the emulator. Other tiles use demo TV banners composed from the publishers' icons,
preserving the logos' pixels and aspect ratios. These are sample app entries; the capture does not install,
sign into or play those services.
The recommendation titles and progress are fictional examples, illustrated with public NASA Earth imagery.
They do not claim that the depicted titles are available through a particular streaming service. The NASA
wallpaper and screensaver use photos selected from the fixture tool's live NASA searches, seeded into Lumen's
normal cache. Lumen's production decoding, cropping, attribution, shading and screensaver rendering apply.
Seeding the cache keeps the gallery repeatable and does not change production wallpaper selection.

No Google account, streaming account, personal device, watch history or location is needed. A fixed sample
weather report and 9:41 clock are supplied to Home. Day and dawn previews freeze the palette input while
using the production landscape, blur and legibility code. The screensaver uses the emulator's current clock.

## Create an isolated virtual TV

Install the [Android SDK tools](https://developer.android.com/tools/sdkmanager), JDK 21, build-tools 35 and
platform android-34 as described in the main README. On Apple Silicon:

```sh
sdkmanager "emulator" "platform-tools" "system-images;android-34;google-tv;arm64-v8a"
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_USER_HOME="${TMPDIR:-/tmp}/lumen-android-user"
export ANDROID_AVD_HOME="${TMPDIR:-/tmp}/lumen-avd"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"
printf 'no\n' | avdmanager create avd -n LumenScreenshotTV \
  -k "system-images;android-34;google-tv;arm64-v8a" -d tv_1080p
emulator -avd LumenScreenshotTV -no-window -no-snapshot -no-audio \
  -no-boot-anim -no-metrics -gpu swiftshader
```

For Intel hosts, choose a matching Google TV image/ABI from `sdkmanager --list`. The API 34 Google TV ARM
image includes YouTube and `com.android.vending`; the AVD's `PlayStore.enabled=no` metadata does not mean
that the Store package is absent. Capture needs neither an account nor the Store.

The emulator must have access to the host's virtualization and CPU information. A restricted macOS sandbox
can block `sysctl` and make Qt incorrectly report “Incompatible processor … neon,” or make headless startup
exit with an illegal instruction. Run the emulator with the necessary host permissions before changing images
or assuming that Apple Silicon is incompatible.

Wait until `adb -s emulator-5554 shell getprop sys.boot_completed` prints `1`. If the first-run Google TV setup
covers the app, finish the disposable emulator's provisioning flags without signing in:

```sh
adb -s emulator-5554 shell settings put global device_provisioned 1
adb -s emulator-5554 shell settings put secure user_setup_complete 1
```

## Capture and review

```sh
tools/screenshots/capture.sh emulator-5554
```

Optional second argument: an output directory. `ANDROID_HOME`, `ADB`, `JAVA_HOME` and
`LUMEN_CAPTURE_ASSETS` can override SDK paths and the temporary artwork cache. The script refuses physical
device serials and checks Android's emulator property before changing any settings or fixture data.

The command builds Lumen with `./build.sh`, builds and signs the separate instrumentation APK, installs both
only on the emulator, and records thirteen native framebuffer PNGs under `out/capture/screenshots/`. It checks
the framebuffer is 1920 × 1080 and fails if the sample NASA wallpaper never decodes. The screensaver capture
hosts the production `Screensaver.SaverView` in the capture activity and waits for its image to load; it does
not depend on the emulator's DreamManager policy or test DreamService lifecycle and remote handling.
It also writes an
`artwork.json` source manifest. Raw downloaded third-party artwork and APKs remain outside tracked source.

On a fresh artwork cache, the fetcher replays the checked-in `artwork.json` URLs and checks each file's SHA-256.
To deliberately select fresh
public artwork instead, run `python3 tools/screenshots/fetch-assets.py /path/to/artwork-cache --refresh`, then
set `LUMEN_CAPTURE_ASSETS` to that directory for capture. Review the new source manifest along with the images.

Review **every** capture before updating the gallery: the cards must have loaded, the menu must match its
caption, the screensaver must be visible, and no account or personal data should appear. A capture is a native
UI example with sample data, not a claim about subscriptions, service availability or measured TV performance.

The checked-in gallery uses JPEGs at quality 92 to keep the repository small. On macOS, after reviewing the PNGs:

```sh
for image in out/capture/screenshots/*.png; do
  name=$(basename "$image" .png)
  sips -s format jpeg -s formatOptions 92 "$image" --out "docs/design/screens/$name.jpg"
done
cp out/capture/screenshots/artwork.json tools/screenshots/artwork.json
```

Update the README and design gallery captions when screens change. Planned concepts from the HTML mockup
must be labeled as concepts and kept separate from native captures. Never add screenshots from a personal TV
to this gallery. Review `git diff --cached` before committing.

## Sources

- [Android TV virtual devices](https://developer.android.com/training/tv/get-started/create)
- [Android Emulator command line](https://developer.android.com/studio/run/emulator-commandline)
- [Android screenshots](https://developer.android.com/studio/run/emulator-take-screenshots)
- [Google Play](https://play.google.com/store/apps): publisher icon sources are recorded in `artwork.json`.
- [NASA Image and Video Library](https://images.nasa.gov): sample card image titles, IDs, credits and source
  URLs are recorded in `artwork.json`. See also [NASA photo notes](../../docs/NASA-PHOTOS.md) and
  [NASA media guidelines](https://www.nasa.gov/nasa-brand-center/images-and-media/).

App names and artwork belong to their respective owners. Their appearance identifies the apps shown by the
launcher; it does not imply endorsement. The fictional recommendation metadata and screenshot tooling are
part of the Lumen project. The production app continues to read recommendations only from installed apps.
