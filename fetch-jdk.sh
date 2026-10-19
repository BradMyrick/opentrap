#!/usr/bin/env bash
# Fetch a portable Temurin JDK into jdk/.
#
# The AppImage bundles a Java runtime built from this JDK (jlink + jpackage in
# package-appimage.sh), and the build scripts compile against it. Eclipse Temurin
# is built on an old glibc (2.17), so the runtime inside the AppImage starts on
# mainstream Linux, not only on the machine that built it. Building the runtime
# from a bleeding-edge system JDK (newer glibc) produces an AppImage that quits
# on older distributions before it can show a window.
#
# The pinned major version lives in tools/versions; the latest GA patch for that
# major is fetched. Re-run after bumping JDK_VERSION there.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

# shellcheck disable=SC1091
. tools/versions
FEATURE="${JDK_VERSION%%.*}"
URL="https://api.adoptium.net/v3/binary/latest/${FEATURE}/ga/linux/x64/jdk/hotspot/normal/eclipse"

if [ -x jdk/bin/javac ]; then
    echo "jdk/ already present: $(jdk/bin/java -version 2>&1 | head -1)"
    echo "Delete jdk/ and re-run to refresh."
    exit 0
fi

echo ">> downloading Temurin JDK ${FEATURE} (x64 linux)"
TMP="$(mktemp -d)"
curl -sSL -o "$TMP/jdk.tar.gz" "$URL"
tar -xzf "$TMP/jdk.tar.gz" -C "$TMP"
SRC="$(find "$TMP" -maxdepth 1 -type d -name 'jdk-*' | head -1)"
[ -n "$SRC" ] || { echo "could not find the extracted JDK directory"; rm -rf "$TMP"; exit 1; }
rm -rf jdk
mv "$SRC" jdk
rm -rf "$TMP"

echo "Installed jdk/: $(jdk/bin/java -version 2>&1 | head -1)"
