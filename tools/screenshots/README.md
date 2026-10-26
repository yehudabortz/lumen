# Native screenshots

Capture Lumen's real Android views in a clean **1920 × 1080 Google TV emulator**, using public sample content. The separate instrumentation APK is not part of the shipped app. No streaming account or personal TV is needed.

## Set up the emulator

Install the [build prerequisites](../../docs/BUILDING.md), then on Apple Silicon:

```sh
sdkmanager 'emulator' 'platform-tools' 'system-images;android-34;google-tv;arm64-v8a'
export ANDROID_USER_HOME="${TMPDIR:-/tmp}/lumen-android-user"
export ANDROID_AVD_HOME="${TMPDIR:-/tmp}/lumen-avd"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"
printf 'no\n' | avdmanager create avd -n LumenScreenshotTV \
  -k 'system-images;android-34;google-tv;arm64-v8a' -d tv_1080p
emulator -avd LumenScreenshotTV -no-window -no-snapshot -no-audio \
  -no-boot-anim -no-metrics -gpu swiftshader
```

Intel hosts need a matching ABI/image from `sdkmanager --list`. Allow the emulator access to host virtualization and CPU information: a macOS sandbox can otherwise cause a misleading “neon”/illegal-instruction failure.

Wait until `adb -s emulator-5554 shell getprop sys.boot_completed` returns `1`. If the first-run setup blocks Lumen, complete the disposable emulator's flags without signing in:

```sh
adb -s emulator-5554 shell settings put global device_provisioned 1
adb -s emulator-5554 shell settings put secure user_setup_complete 1
```

## Capture

```sh
tools/screenshots/capture.sh emulator-5554
```

The script refuses physical TVs, checks restored focus, and captures sixteen native PNGs in `out/capture/screenshots/`. Optional second argument: another output directory. SDK paths follow `ANDROID_HOME`/`JAVA_HOME`; `ADB` and `LUMEN_CAPTURE_ASSETS` can override the tool and artwork cache.

The artwork manifest pins public source URLs and SHA-256 hashes. YouTube uses its bundled TV banner; other sample banners use publishers' icons. Recommendations and progress are fictional. NASA fixtures seed the normal photo cache; production decoding, cropping, shading and motion still apply. Home uses a sample clock/weather report. The saver captures its production view, not DreamService lifecycle behavior. These are not app subscriptions or performance benchmarks.

## Review and update the gallery

Check every image for loaded artwork, correct state, readable text and absence of personal data. Then, on macOS:

```sh
for image in out/capture/screenshots/*.png; do
  name=$(basename "$image" .png)
  sips -s format jpeg -s formatOptions 92 "$image" --out "docs/design/screens/$name.jpg"
done
cp out/capture/screenshots/artwork.json tools/screenshots/artwork.json
```

Update captions and review staged files before committing. Raw artwork and APKs stay outside tracked source. To refresh the artwork, run `python3 tools/screenshots/fetch-assets.py /path/to/cache --refresh`, inspect the results, then capture with `LUMEN_CAPTURE_ASSETS` pointing there.

[Artwork sources](artwork.json) · [Third-party notices](../../NOTICE.md) · [Release checks](../../docs/MAINTAINING.md)
