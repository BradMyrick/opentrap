#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
# Start from an empty build/ so nothing left by an earlier build or by the
# AppImage packaging (its runtime and staging directories live under build/)
# is packed into the jar.
rm -rf build && mkdir -p build dist
echo "Compiling..."
javac -d build -cp "lib/*:build" $(find src -name '*.java')
echo "Copying resources..."
mkdir -p build/opentrap/ui/icons
cp src/opentrap/ui/icons/*.png build/opentrap/ui/icons/ 2>/dev/null || true
echo "Unpacking BouncyCastle into the jar..."
cd build
for j in ../lib/*.jar; do jar xf "$j"; done
rm -rf META-INF/*.SF META-INF/*.DSA META-INF/*.RSA 2>/dev/null || true
cd ..
echo "Main-Class: opentrap.ui.Main" > /tmp/opentrap-manifest.txt
jar cfm dist/opentrap.jar /tmp/opentrap-manifest.txt -C build .
echo "Built dist/opentrap.jar ($(du -h dist/opentrap.jar | cut -f1))"
