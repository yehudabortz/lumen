#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
[ -x /opt/homebrew/opt/openjdk@21/bin/javac ] && export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
mkdir -p out/scenic-tests
javac -d out/scenic-tests app/src/dev/lumen/tv/NasaScenic.java tests/NasaScenicTest.java
java -cp out/scenic-tests dev.lumen.tv.NasaScenicTest
