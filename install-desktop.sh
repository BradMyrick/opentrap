#!/usr/bin/env bash
# Install the OpenTRAP launcher icon and menu entry for the current user.
#
# This is the piece that makes the icon work regardless of the user's theme.
# The icon is installed into the hicolor icon theme, which every desktop falls
# back to last, and the menu entry refers to it by the name "opentrap" rather
# than borrowing a theme icon that would look different on every machine.
#
# No root needed: everything goes under ~/.local/share. Pass --system to install
# for all users instead (needs sudo).
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"

SCOPE="user"
[ "$1" = "--system" ] && SCOPE="system"

if [ "$SCOPE" = "system" ]; then
    ICONROOT="/usr/share/icons/hicolor"
    APPDIR="/usr/share/applications"
else
    ICONROOT="$HOME/.local/share/icons/hicolor"
    APPDIR="$HOME/.local/share/applications"
fi

echo "Installing OpenTRAP desktop entry ($SCOPE scope)..."

# Scalable source, for themes and desktops that prefer SVG.
mkdir -p "$ICONROOT/scalable/apps"
cp "$DIR/icon/opentrap.svg" "$ICONROOT/scalable/apps/opentrap.svg"

# Raster sizes, rendered from the app itself so no SVG tool is required.
if [ -f "$DIR/dist/opentrap.jar" ]; then
    TMP="$(mktemp -d)"
    java -cp "$DIR/dist/opentrap.jar" opentrap.ui.AppIcon "$TMP" >/dev/null
    for size in 16 24 32 48 64 128 256; do
        mkdir -p "$ICONROOT/${size}x${size}/apps"
        cp "$TMP/opentrap_${size}.png" "$ICONROOT/${size}x${size}/apps/opentrap.png"
    done
    rm -rf "$TMP"
else
    echo "  note: dist/opentrap.jar not found, installed the SVG only (run ./build.sh for PNG sizes)"
fi

# Menu entry, with Exec pointing at this checkout's launcher.
mkdir -p "$APPDIR"
sed "s#^Icon=opentrap#Icon=opentrap#; \$aExec=$DIR/run.sh" "$DIR/icon/opentrap.desktop" \
    > "$APPDIR/opentrap.desktop"
chmod +x "$APPDIR/opentrap.desktop" 2>/dev/null || true

# Refresh the caches so the entry appears without a logout.
command -v gtk-update-icon-cache >/dev/null 2>&1 && \
    gtk-update-icon-cache -f -t "$ICONROOT" >/dev/null 2>&1 || true
command -v update-desktop-database >/dev/null 2>&1 && \
    update-desktop-database "$APPDIR" >/dev/null 2>&1 || true

echo "Installed:"
echo "  icon  -> $ICONROOT/{scalable,*x*}/apps/opentrap.*"
echo "  entry -> $APPDIR/opentrap.desktop"
echo "OpenTRAP should now appear in your application menu with its own icon."
