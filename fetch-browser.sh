#!/usr/bin/env bash
# Download a standalone, redistributable Chromium into the product so the
# embedded browser works on machines with no browser installed.
# Plain open-source Chromium (BSD-licensed), not Chrome for Testing, so there
# is no "automated testing" banner and it can be shipped freely.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
DEST="$DIR/browser"
mkdir -p "$DEST"

BASE="https://commondatastorage.googleapis.com/chromium-browser-snapshots/Linux_x64"
# Downloads land in a scratch directory and only move into browser/ once
# complete, so an interrupted run never leaves a half-extracted tree behind.
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Finding the latest Chromium snapshot..."
REV="$(curl -fsS "$BASE/LAST_CHANGE")"
echo "Revision $REV"
echo "Downloading Chromium..."
curl -fSL -o "$TMP/chrome.zip" "$BASE/$REV/chrome-linux.zip"
echo "Extracting..."
rm -rf "$DEST/chrome-linux"
unzip -q "$TMP/chrome.zip" -d "$DEST"
chmod +x "$DEST/chrome-linux/chrome" 2>/dev/null || true
echo "$REV" > "$DEST/chrome-linux/REVISION"
echo "Bundled Chromium ready at $DEST/chrome-linux/chrome"
"$DEST/chrome-linux/chrome" --version 2>/dev/null || true

# The matching chromedriver from the same snapshot, so ZAP's AJAX spider can
# drive this exact Chromium headlessly. Versions must match, so it comes from
# the same revision as the browser above.
echo "Downloading matching chromedriver..."
curl -fSL -o "$TMP/chromedriver.zip" "$BASE/$REV/chromedriver_linux64.zip"
unzip -q "$TMP/chromedriver.zip" -d "$TMP"
# Flatten to browser/chromedriver, where EmbeddedBrowser.findDriver looks first.
rm -rf "$DEST/chromedriver" "$DEST/chromedriver_linux64"
mv -f "$TMP/chromedriver_linux64/chromedriver" "$DEST/chromedriver"
chmod +x "$DEST/chromedriver" 2>/dev/null || true
echo "Bundled chromedriver ready at $DEST/chromedriver"
"$DEST/chromedriver" --version 2>/dev/null || true
