#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
# Prefer the pinned portable JDK (fetch-jdk.sh) when present, so the dev build
# matches the toolchain the release and AppImage use. Falls back to the system
# JDK if jdk/ is absent.
if [ -x "$DIR/jdk/bin/javac" ]; then
    export JAVA_HOME="$DIR/jdk"
    export PATH="$DIR/jdk/bin:$PATH"
fi
# Start from an empty build/ so nothing left by an earlier build or by the
# AppImage packaging (its runtime and staging directories live under build/)
# is packed into the jar.
rm -rf build && mkdir -p build dist
echo "Compiling..."
mapfile -t SRCS < <(find src -name '*.java')
javac -d build -cp "lib/*:build" "${SRCS[@]}"
echo "Copying resources..."
mkdir -p build/opentrap/ui/icons
cp src/opentrap/ui/icons/*.png build/opentrap/ui/icons/ 2>/dev/null || true
echo "Unpacking BouncyCastle into the jar..."
cd build
for j in ../lib/*.jar; do jar xf "$j"; done
rm -rf META-INF/*.SF META-INF/*.DSA META-INF/*.RSA 2>/dev/null || true
cd ..
MANIFEST="$(mktemp)"
trap 'rm -f "$MANIFEST"' EXIT
echo "Main-Class: opentrap.ui.Main" > "$MANIFEST"
jar cfm dist/opentrap.jar "$MANIFEST" -C build .
echo "Built dist/opentrap.jar ($(du -h dist/opentrap.jar | cut -f1))"
