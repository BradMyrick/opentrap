#!/usr/bin/env bash
# Download a standalone, redistributable Chromium into the product so the
# embedded browser works on machines with no browser installed.
# Plain open-source Chromium (BSD-licensed), not Chrome for Testing, so there
# is no "automated testing" banner and it can be shipped freely.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
DEST="$DIR/browser"
mkdir -p "$DEST"

BASE="https://commondatastorage.googleapis.com/chromium-browser-snapshots/Linux_x64"
echo "Finding the latest Chromium snapshot..."
REV=$(curl -s "$BASE/LAST_CHANGE")
echo "Revision $REV"
echo "Downloading Chromium..."
curl -L --fail -o "$DEST/chrome.zip" "$BASE/$REV/chrome-linux.zip"
echo "Extracting..."
rm -rf "$DEST/chrome-linux"
unzip -q -o "$DEST/chrome.zip" -d "$DEST"
rm -f "$DEST/chrome.zip"
chmod +x "$DEST/chrome-linux/chrome" 2>/dev/null || true
echo "$REV" > "$DEST/chrome-linux/REVISION"
echo "Bundled Chromium ready at $DEST/chrome-linux/chrome"
"$DEST/chrome-linux/chrome" --version 2>/dev/null || true
