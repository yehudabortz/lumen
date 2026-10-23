# NASA cache checks on Android

Build and install Lumen first, then run `sh tests/build-nasa-tests.sh`. The harness uses the built APK's real
`Nasa` implementation, an isolated shell-owned directory, generated JPEG
fixtures, and in-memory preferences. It disables the downloader: the seven checks require no network and never
read or change Lumen's private preferences or image cache.

Using an authorized Android device (substitute your existing TV wrapper for `adb` when needed):

```sh
adb push out/Lumen.apk /data/local/tmp/Lumen.apk
adb push out/nasa-tests.zip /data/local/tmp/lumen-nasa-tests.zip
adb shell mkdir -p /data/local/tmp/lumen-nasa-test
adb shell 'CLASSPATH=/data/local/tmp/Lumen.apk:/data/local/tmp/lumen-nasa-tests.zip app_process /system/bin dev.lumen.tv.NasaCacheTest /data/local/tmp/lumen-nasa-test'
```

Expected: `PASS: 7 NASA cache checks`. Checks cover empty-cache fallback, display-sized decoding, creator credits,
stable cached selection, offline retention, corruption recovery and rejection of oversized image dimensions.
Append `online` to the `app_process` command to perform a live NASA search, download one returned result into an
isolated cache, and verify its title, attribution, dimensions and byte limit. This optional check needs Internet access.
Both modes clean up their own cache directory after a successful run.

Also inspect actual photos and credits on Home and in the screensaver, and compare paced navigation with
`dumpsys gfxinfo` and `dumpsys meminfo`. Keep device screenshots and diagnostics outside the source repository.
