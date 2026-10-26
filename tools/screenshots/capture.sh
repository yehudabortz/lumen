#!/bin/sh
# Capture the production Android UI with an isolated instrumentation APK and public sample data.
set -eu
cd "$(dirname "$0")/../.."

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-34/android.jar"
ADB="${ADB:-$SDK/platform-tools/adb}"
SERIAL="${1:-emulator-5554}"
ASSETS="${LUMEN_CAPTURE_ASSETS:-${TMPDIR:-/tmp}/lumen-screenshot-assets}"
CAPTURE=out/capture
DEST="${2:-$CAPTURE/screenshots}"
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
[ -x /opt/homebrew/opt/openjdk@21/bin/javac ] && export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"

# Never seed demo data or change settings on a physical TV.
case "$SERIAL" in emulator-[0-9]*) ;; *) echo "Use an emulator serial, not a physical TV." >&2; exit 1;; esac
[ "$("$ADB" -s "$SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] || { echo "Target is not an emulator." >&2; exit 1; }

./build.sh
mkdir -p "$CAPTURE/classes" "$CAPTURE/gen" "$DEST"
if [ ! -f "$ASSETS/fixtures.json" ]; then python3 tools/screenshots/fetch-assets.py "$ASSETS"; fi

"$BT/aapt2" link -I "$JAR" --manifest tools/screenshots/AndroidManifest.xml -A "$ASSETS" \
    --java "$CAPTURE/gen" --min-sdk-version 26 --target-sdk-version 34 -o "$CAPTURE/unsigned.apk"
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" \
    -cp out/classes -d "$CAPTURE/classes" tools/screenshots/ScreenshotCapture.java
(cd "$CAPTURE/classes" && jar cf ../classes.jar .)
"$BT/d8" --release --min-api 26 --lib "$JAR" --classpath out/classes.jar --output "$CAPTURE" "$CAPTURE/classes.jar"
zip -q -j "$CAPTURE/unsigned.apk" "$CAPTURE/classes.dex"
"$BT/zipalign" -f 4 "$CAPTURE/unsigned.apk" "$CAPTURE/aligned.apk"
PASS=$(sed -n 's/^keystore.password=//p' local.properties)
"$BT/apksigner" sign --ks lumen.keystore --ks-pass "pass:$PASS" --ks-key-alias lumen \
    --out "$CAPTURE/Capture.apk" "$CAPTURE/aligned.apk"

"$ADB" -s "$SERIAL" install -r out/Lumen.apk
"$ADB" -s "$SERIAL" install -r "$CAPTURE/Capture.apk"
"$ADB" -s "$SERIAL" shell pm grant dev.lumen.tv android.permission.READ_TV_LISTINGS
"$ADB" -s "$SERIAL" shell am force-stop dev.lumen.tv
"$ADB" -s "$SERIAL" shell am instrument -w dev.lumen.tv.capture/dev.lumen.tv.ScreenshotCapture | tee "$CAPTURE/run.log"
grep -q 'Captured 21 native Lumen screenshots' "$CAPTURE/run.log" || { echo "Capture failed; see $CAPTURE/run.log" >&2; exit 1; }
"$ADB" -s "$SERIAL" pull /sdcard/Android/data/dev.lumen.tv/files/screenshots/. "$DEST/"
cp "$ASSETS/fixtures.json" "$DEST/artwork.json"
echo "Review the native screenshots in $DEST before copying them into docs/design/screens/."
