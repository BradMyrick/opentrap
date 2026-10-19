#!/usr/bin/env bash
# Download the OWASP ZAP engine that OpenTRAP drives as its crawl + audit backend.
#
# OpenTRAP runs ZAP (Apache 2.0, by the ZAP dev team / Checkmarx) with NO user
# interface, purely as a headless scan engine on the loopback. Nothing of ZAP is
# shown to the user; its crawler and active scanner power OpenTRAP's own UI. See
# CREDITS.md for attribution.
#
# This fetches the pinned version into engine/. Updating to a new ZAP release is
# just: bump ZAP_VERSION in tools/versions, re-run this. The Core package is used
# (no bundled JRE) because OpenTRAP already runs on a JVM.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
DEST="$DIR/engine"
source "$DIR/tools/versions"

VER="${ZAP_VERSION}"
TAG="v${VER}"
URL="https://github.com/zaproxy/zaproxy/releases/download/${TAG}/ZAP_${VER}_Core.zip"

echo "Installing pinned ZAP engine ${TAG} (edit tools/versions to change)..."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
echo "Downloading $URL ..."
curl -L --fail -o "$tmp/zap.zip" "$URL"
unzip -q "$tmp/zap.zip" -d "$tmp"

# The zip contains a single top-level ZAP_<ver>/ directory; flatten into engine/.
inner="$(ls -d "$tmp"/ZAP_* 2>/dev/null | head -1)"
[ -n "$inner" ] || inner="$(ls -d "$tmp"/*/ 2>/dev/null | head -1)"
[ -n "$inner" ] || { echo "Unexpected archive layout" >&2; exit 1; }
rm -rf "$DEST"; mkdir -p "$DEST"
cp -a "$inner"/. "$DEST"/

JAR="$(ls "$DEST"/zap-*.jar 2>/dev/null | head -1)"
[ -n "$JAR" ] || { echo "No zap jar after unpack" >&2; exit 1; }
echo "ZAP engine ready: $JAR"
echo "Add-ons: $(ls "$DEST"/plugin/*.zap 2>/dev/null | wc -l)"
