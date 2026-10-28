#!/usr/bin/env bash
# Download a standalone, redistributable Chromium into the product so the
# embedded browser works on machines with no browser installed.
# Plain open-source Chromium (BSD-licensed), not Chrome for Testing, so there
# is no "automated testing" banner and it can be shipped freely.
#
# The snapshot revision and the sha256 of both archives are pinned in
# tools/versions, so every machine installs identical, verified bytes rather
# than whatever snapshot is latest that day. Google's snapshot storage does
# not publish a sha256, so the pin is the hash of the downloaded archive;
# tools/check-updates.sh reports when a newer snapshot exists.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
DEST="$DIR/browser"
mkdir -p "$DEST"
source "$DIR/tools/versions"

BASE="https://commondatastorage.googleapis.com/chromium-browser-snapshots/Linux_x64"
REV="$CHROMIUM_REVISION"
# Downloads land in a scratch directory and only move into browser/ once
# complete and verified, so an interrupted or tampered run never leaves a
# half-extracted or unverified tree behind.
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

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

echo "Installing pinned Chromium snapshot $REV (edit tools/versions to change)..."
echo "Downloading Chromium..."
curl -fSL -o "$TMP/chrome.zip" "$BASE/$REV/chrome-linux.zip"
check "$TMP/chrome.zip" "$CHROMIUM_CHROME_SHA256"
echo "Extracting..."
unzip -q "$TMP/chrome.zip" -d "$TMP"
rm -rf "$DEST/chrome-linux"
mv -f "$TMP/chrome-linux" "$DEST/chrome-linux"
chmod +x "$DEST/chrome-linux/chrome" 2>/dev/null || true
echo "$REV" > "$DEST/chrome-linux/REVISION"
echo "Bundled Chromium ready at $DEST/chrome-linux/chrome"
"$DEST/chrome-linux/chrome" --version 2>/dev/null || true

# The matching chromedriver from the same snapshot, so ZAP's AJAX spider can
# drive this exact Chromium headlessly. Versions must match, so it comes from
# the same revision as the browser above.
echo "Downloading matching chromedriver..."
curl -fSL -o "$TMP/chromedriver.zip" "$BASE/$REV/chromedriver_linux64.zip"
check "$TMP/chromedriver.zip" "$CHROMIUM_DRIVER_SHA256"
unzip -q "$TMP/chromedriver.zip" -d "$TMP"
# Flatten to browser/chromedriver, where EmbeddedBrowser.findDriver looks first.
rm -rf "$DEST/chromedriver" "$DEST/chromedriver_linux64"
mv -f "$TMP/chromedriver_linux64/chromedriver" "$DEST/chromedriver"
chmod +x "$DEST/chromedriver" 2>/dev/null || true
echo "Bundled chromedriver ready at $DEST/chromedriver"
"$DEST/chromedriver" --version 2>/dev/null || true
