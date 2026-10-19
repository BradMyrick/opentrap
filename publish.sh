#!/usr/bin/env bash
# Publish a release to GitHub from the artefacts in dist/.
#
# Run this only after the AppImage in dist/ has been tested by hand. Both
# assets are uploaded under fixed names, so the permanent links
#   https://github.com/opentrap/opentrap/releases/latest/download/opentrap-x86_64.appimage
#   https://github.com/opentrap/opentrap/releases/latest/download/opentrap.jar
# always serve the newest release. The version is the release tag, and the
# app shows it in its title bar.
#
# Usage: ./publish.sh <timestamp> <notes-file>
#   timestamp   the build to publish, e.g. 20261019-085441 (dist/opentrap_<timestamp>.jar)
#   notes-file  markdown release notes
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

TS="${1:?usage: ./publish.sh <timestamp> <notes-file>}"
NOTES="${2:?usage: ./publish.sh <timestamp> <notes-file>}"
REPO="opentrap/opentrap"
JAR="dist/opentrap_${TS}.jar"
APP="dist/opentrap-x86_64.appimage"

[ -f "$JAR" ] || { echo "missing $JAR"; exit 1; }
[ -f "$APP" ] || { echo "missing $APP"; exit 1; }
[ -f "$NOTES" ] || { echo "missing notes file $NOTES"; exit 1; }

# The AppImage must have been built from this jar.
STAGE="$(mktemp -d)"
( cd "$STAGE" && "$DIR/$APP" --appimage-extract >/dev/null 2>&1 )
if [ ! -f "$STAGE/squashfs-root/usr/lib/app/opentrap_${TS}.jar" ]; then
    echo "the AppImage does not contain opentrap_${TS}.jar; rebuild it with ./package-appimage.sh"
    rm -rf "$STAGE"; exit 1
fi
rm -rf "$STAGE/squashfs-root"

# Fixed asset names.
cp "$JAR" "$STAGE/opentrap.jar"
cp "$APP" "$STAGE/opentrap-x86_64.appimage"

echo ">> publishing $TS"
gh release create "$TS" "$STAGE/opentrap-x86_64.appimage" "$STAGE/opentrap.jar" \
    --repo "$REPO" --title "OpenTRAP $TS" --notes-file "$NOTES" --latest

echo ">> verifying"
for name in opentrap-x86_64.appimage opentrap.jar; do
    local_sum="$(sha256sum "$STAGE/$name" | cut -d' ' -f1)"
    remote_sum="$(gh release view "$TS" --repo "$REPO" --json assets \
        --jq ".assets[] | select(.name==\"$name\") | .digest" | sed 's/^sha256://')"
    target="$(curl -sI "https://github.com/$REPO/releases/latest/download/$name" \
        | grep -i '^location' | grep -o 'releases/download/[^/]*' || true)"
    if [ "$local_sum" = "$remote_sum" ] && [ "$target" = "releases/download/$TS" ]; then
        echo "   ok  $name  (checksum matches, latest link -> $TS)"
    else
        echo "   FAIL $name  local=$local_sum remote=$remote_sum latest->$target"
        rm -rf "$STAGE"; exit 1
    fi
done
rm -rf "$STAGE"
echo "Published https://github.com/$REPO/releases/tag/$TS"
