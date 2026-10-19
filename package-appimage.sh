#!/usr/bin/env bash
# Build a single-file, self-contained OpenTRAP AppImage for Linux.
#
# The result (dist/opentrap-x86_64.appimage) bundles a Java runtime and the scan
# engine, so a user downloads one file and runs it, with nothing installed, no
# Java, no terminal. It works across distributions.
#
# Needs: a JDK (for jpackage + jdeps), and internet the first time (to fetch
# appimagetool). Produces the AppImage from the self-contained release jar.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo ">> building the self-contained release jar"
./release.sh
JAR="$(ls -t dist/opentrap_*.jar | head -1)"
JARNAME="$(basename "$JAR")"

AIT=/tmp/appimagetool
if [ ! -x "$AIT" ]; then
    echo ">> fetching appimagetool"
    curl -sSL -o "$AIT" "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
    chmod +x "$AIT"
fi

echo ">> jpackage: self-contained app image (bundled JRE + native launcher)"
rm -rf build/otin build/appimage AppDir
mkdir -p build/otin
cp "$JAR" build/otin/
MODS="$(jdeps --multi-release 25 --print-module-deps --ignore-missing-deps "$JAR" 2>/dev/null)"
MODS="${MODS},java.management,java.naming,java.security.jgss,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.unsupported,java.scripting"
jpackage --type app-image --name OpenTRAP \
    --input build/otin --main-jar "$JARNAME" --main-class opentrap.ui.Main \
    --icon src/opentrap/ui/icons/opentrap_256.png \
    --add-modules "$MODS" --java-options "-Xmx1024m" \
    --dest build/appimage

echo ">> assembling AppDir (with a scale-probing launcher)"
rm -rf AppDir
mkdir -p AppDir/usr
cp -a build/appimage/OpenTRAP/. AppDir/usr/
cp src/opentrap/ui/icons/opentrap_256.png AppDir/opentrap.png
# The launcher picks a HiDPI scale with a quick headless probe and sets it before
# the real JVM starts. This matters inside an AppImage: the in-app relaunch the
# plain jar uses to apply the scale would tear down the AppImage mount and kill
# the app, so here the scale is chosen up front instead.
cat > AppDir/AppRun <<'RUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
SCALE="$("$HERE/usr/bin/OpenTRAP" --print-scale 2>/dev/null | tr -d '[:space:]')"
case "$SCALE" in
  1.25|1.5|2|2.0|3|3.0) export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Dsun.java2d.uiScale=$SCALE -Dopentrap.scaled=$SCALE" ;;
esac
exec "$HERE/usr/bin/OpenTRAP" "$@"
RUN
chmod +x AppDir/AppRun
cat > AppDir/OpenTRAP.desktop <<'DESK'
[Desktop Entry]
Type=Application
Name=OpenTRAP
Comment=Open web security testing proxy
Exec=OpenTRAP
Icon=opentrap
Categories=Development;Network;Security;
Terminal=false
DESK

echo ">> packing the AppImage"
ARCH=x86_64 "$AIT" --appimage-extract-and-run AppDir dist/opentrap-x86_64.appimage

echo
echo "Built dist/opentrap-x86_64.appimage"
echo "Run it with:  chmod +x dist/opentrap-x86_64.appimage && ./dist/opentrap-x86_64.appimage"
