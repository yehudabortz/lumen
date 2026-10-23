#!/bin/sh
# Run ./build.sh first. Produces a small app_process test dex; does not modify the APK.
set -e
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-34/android.jar"
[ -x /opt/homebrew/opt/openjdk@21/bin/javac ] && export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
mkdir -p out/nasa-tests
javac -source 8 -target 8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -cp out/classes -d out/nasa-tests tests/NasaCacheTest.java
jar cf out/nasa-tests-classes.jar -C out/nasa-tests .
"$BT/d8" --min-api 26 --lib "$JAR" --classpath out/classes.jar --output out/nasa-tests.zip out/nasa-tests-classes.jar
