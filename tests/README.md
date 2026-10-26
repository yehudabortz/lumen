# Tests

Build with `./build.sh`, then run the fast selection regressions:

```sh
sh tests/run-scenic-tests.sh
```

## Android cache checks

Install Lumen on an emulator or authorized Android device first. These tests use an isolated shell-owned cache and never modify Lumen's private settings.

```sh
sh tests/build-nasa-tests.sh
adb push out/Lumen.apk /data/local/tmp/Lumen.apk
adb push out/nasa-tests.zip /data/local/tmp/lumen-nasa-tests.zip
adb shell mkdir -p /data/local/tmp/lumen-nasa-test
adb shell 'CLASSPATH=/data/local/tmp/Lumen.apk:/data/local/tmp/lumen-nasa-tests.zip app_process /system/bin dev.lumen.tv.NasaCacheTest /data/local/tmp/lumen-nasa-test'
```

Expect `PASS: 7 NASA cache checks`. Append `online` to the final command to check a live search/download; this needs internet access. Successful runs clean up their test cache.

Also check real D-pad navigation, Home/Back and photo loading on Android. Use [emulator capture](../tools/screenshots/README.md) for shareable images; keep personal TV captures and diagnostics outside the repo.
