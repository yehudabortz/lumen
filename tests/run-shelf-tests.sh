#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
[ -x /opt/homebrew/opt/openjdk@21/bin/javac ] && export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
mkdir -p out/shelf-tests
javac -d out/shelf-tests app/src/dev/lumen/tv/ShelfRows.java tests/ShelfRowsTest.java
java -cp out/shelf-tests dev.lumen.tv.ShelfRowsTest
