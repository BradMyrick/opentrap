#!/usr/bin/env bash
# Build a self-contained, timestamped release jar for Linux.
#
# Unlike build.sh (a lean ~10 MB dev jar), this embeds the trimmed OWASP ZAP scan
# engine inside the jar as a resource. On first run the app extracts it once to
# ~/.opentrap/engine and runs it headless, so the single jar is self-contained:
# download one file, `java -jar opentrap_<timestamp>.jar`, and scanning works.
#
# The build is identified by a UTC timestamp (YYYYMMDD-HHMMSS) used for both the
# filename and the jar's Implementation-Version, which the app shows in its title.
#
# The testing browser is NOT embedded (it is ~680 MB); it is offered as an
# optional in-app download the first time the user opens a browser.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

TS="$(date -u +%Y%m%d-%H%M%S)"
echo "Release build $TS (UTC)"

rm -rf build/opentrap
mkdir -p build dist

echo "Compiling..."
javac -d build -cp "lib/*:build" $(find src -name '*.java')

echo "Copying resources..."
mkdir -p build/opentrap/ui/icons
cp src/opentrap/ui/icons/*.png build/opentrap/ui/icons/ 2>/dev/null || true

echo "Unpacking vendored jars..."
( cd build && for j in ../lib/*.jar; do jar xf "$j"; done
  rm -rf META-INF/*.SF META-INF/*.DSA META-INF/*.RSA 2>/dev/null || true )

if ls engine/zap-*.jar >/dev/null 2>&1; then
    echo "Embedding scan engine..."
    mkdir -p build/opentrap/engine
    ( cd engine && zip -qr -1 "$DIR/build/opentrap/engine/engine.zip" . )
    ZV="$(ls engine/zap-*.jar | head -1 | sed -E 's#.*/zap-([0-9.]+)\.jar#\1#')"
    printf '%s' "$ZV" > build/opentrap/engine/VERSION
    echo "  engine $ZV embedded ($(du -h build/opentrap/engine/engine.zip | cut -f1))"
else
    echo "WARNING: no engine/ present. Run ./fetch-zap.sh first, or the release jar"
    echo "         will fall back to a system ZAP at runtime if one is installed."
fi

printf 'Main-Class: opentrap.ui.Main\nImplementation-Version: %s\n' "$TS" > /tmp/opentrap-manifest.txt
OUT="dist/opentrap_${TS}.jar"
jar cfm "$OUT" /tmp/opentrap-manifest.txt -C build .

echo
echo "Built $OUT ($(du -h "$OUT" | cut -f1))"
echo "Run it with:  java -jar $OUT"
