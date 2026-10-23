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

# The matching chromedriver from the same snapshot, so ZAP's AJAX spider can
# drive this exact Chromium headlessly. Versions must match, so it comes from
# the same revision as the browser above.
echo "Downloading matching chromedriver..."
curl -L --fail -o "$DEST/chromedriver.zip" "$BASE/$REV/chromedriver_linux64.zip"
rm -rf "$DEST/chromedriver_linux64"
unzip -q -o "$DEST/chromedriver.zip" -d "$DEST"
rm -f "$DEST/chromedriver.zip"
# Flatten to browser/chromedriver, where EmbeddedBrowser.findDriver looks first.
mv -f "$DEST/chromedriver_linux64/chromedriver" "$DEST/chromedriver" 2>/dev/null || true
rmdir "$DEST/chromedriver_linux64" 2>/dev/null || true
chmod +x "$DEST/chromedriver" 2>/dev/null || true
echo "Bundled chromedriver ready at $DEST/chromedriver"
"$DEST/chromedriver" --version 2>/dev/null || true
