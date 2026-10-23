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

# Keep only the add-ons OpenTRAP actually drives (active + passive scan rules, the
# spider, and the required infrastructure) and drop the rest, so the bundled engine
# stays small. A clean checkout reproduces the exact engine the release ships.
KEEP="ascanrules pscan pscanrules spider spiderAjax selenium retire oast directorylistv1 commonlib network database callhome"
for f in "$DEST"/plugin/*.zap; do
    [ -e "$f" ] || continue
    name="$(basename "$f" | sed -E 's/-(release|beta|alpha)-.*//')"
    keep=0
    for k in $KEEP; do [ "$name" = "$k" ] && keep=1; done
    [ "$keep" = "0" ] && rm -f "$f"
done

# The AJAX spider (JavaScript-rendering crawl) and its Selenium backend are not
# in the Core package, so fetch them from the ZAP add-on marketplace. spiderAjax
# needs a newer commonlib than Core ships, so replace commonlib too. Versions are
# pinned in tools/versions and must stay compatible with ZAP_VERSION.
EXT="https://github.com/zaproxy/zap-extensions/releases/download"
fetch_addon() {  # <id> <version>
    local id="$1" ver="$2"
    rm -f "$DEST"/plugin/${id}-*.zap
    echo "Fetching add-on ${id} ${ver}..."
    curl -L --fail -o "$DEST/plugin/${id}-release-${ver}.zap" \
        "$EXT/${id}-v${ver}/${id}-release-${ver}.zap"
}
fetch_addon commonlib  "${ZAP_ADDON_COMMONLIB}"
fetch_addon selenium   "${ZAP_ADDON_SELENIUM}"
fetch_addon spiderAjax "${ZAP_ADDON_SPIDERAJAX}"

JAR="$(ls "$DEST"/zap-*.jar 2>/dev/null | head -1)"
[ -n "$JAR" ] || { echo "No zap jar after unpack" >&2; exit 1; }
echo "ZAP engine ready: $JAR"
echo "Add-ons: $(ls "$DEST"/plugin/*.zap 2>/dev/null | wc -l)"
