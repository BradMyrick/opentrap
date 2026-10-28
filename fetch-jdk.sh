#!/usr/bin/env bash
# Fetch the pinned portable Temurin JDK into jdk/.
#
# The AppImage bundles a Java runtime built from this JDK (jlink + jpackage in
# package-appimage.sh), and the build scripts compile against it. Eclipse Temurin
# is built on an old glibc (2.17), so the runtime inside the AppImage starts on
# mainstream Linux, not only on the machine that built it. Building the runtime
# from a bleeding-edge system JDK (newer glibc) produces an AppImage that quits
# on older distributions before it can show a window.
#
# The exact release and its sha256 are pinned in tools/versions, so every
# machine fetches identical bytes. Re-run after bumping JDK_VERSION and
# JDK_SHA256 there.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

# shellcheck disable=SC1091
. tools/versions

# Temurin release assets follow a fixed naming scheme:
#   tag      jdk-25.0.5+8   (the "+" is %2B in the URL)
#   file     OpenJDK25U-jdk_x64_linux_hotspot_25_0_5_8.tar.gz
MAJOR="${JDK_VERSION%%.*}"
FV="$(printf '%s' "$JDK_VERSION" | tr '.+' '__')"
URL="https://github.com/adoptium/temurin${MAJOR}-binaries/releases/download/jdk-${JDK_VERSION//+/%2B}/OpenJDK${MAJOR}U-jdk_x64_linux_hotspot_${FV}.tar.gz"

check() { # <file> <expected sha256>
    local got
    got="$(sha256sum "$1" | cut -d' ' -f1)"
    if [ "$got" != "$2" ]; then
        echo "sha256 mismatch for $1:" >&2
        echo "  expected $2 (update the pin in tools/versions?)" >&2
        echo "  got      $got" >&2
        exit 1
    fi
}

if [ -x jdk/bin/javac ]; then
    echo "jdk/ already present: $(jdk/bin/java -version 2>&1 | head -1)"
    echo "Delete jdk/ and re-run to refresh."
    exit 0
fi

echo ">> downloading Temurin JDK ${JDK_VERSION} (x64 linux)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
curl -fsSL -o "$TMP/jdk.tar.gz" "$URL"
check "$TMP/jdk.tar.gz" "$JDK_SHA256"
tar -xzf "$TMP/jdk.tar.gz" -C "$TMP"
SRC="$(find "$TMP" -maxdepth 1 -type d -name 'jdk-*' | head -1)"
[ -n "$SRC" ] || { echo "could not find the extracted JDK directory" >&2; exit 1; }
rm -rf jdk
mv "$SRC" jdk

echo "Installed jdk/: $(jdk/bin/java -version 2>&1 | head -1)"
