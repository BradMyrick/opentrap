#!/usr/bin/env bash
# Build a single-file, self-contained OpenTRAP AppImage for Linux.
#
# The result (dist/opentrap-x86_64.appimage) bundles a Java runtime and the scan
# engine, so a user downloads one file and runs it, with nothing installed, no
# Java, no terminal. It works across distributions.
#
# Needs: a JDK (for jpackage + jdeps), and internet the first time (to fetch
# appimagetool). Produces the AppImage from the self-contained release jar.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
source "$DIR/tools/versions"

# The AppImage runtime must be built from the pinned portable JDK (old glibc),
# or the packaged app only starts on machines as new as the build host (it quits
# before showing a window on older distributions). Fetch it if missing, then use
# it for the release build below and for jlink, jpackage and the module list.
[ -x "$DIR/jdk/bin/javac" ] || ./fetch-jdk.sh
export JAVA_HOME="$DIR/jdk"
export PATH="$DIR/jdk/bin:$PATH"

echo ">> building the self-contained release jar"
./release.sh
JAR="$(ls -t dist/opentrap_*.jar | head -1)"
JARNAME="$(basename "$JAR")"

# appimagetool is executed as part of this build, so it is fetched pinned to
# the version and sha256 in tools/versions and cached per version under
# ~/.cache/opentrap. An unverified download is never run.
AIT="$HOME/.cache/opentrap/appimagetool-x86_64-${APPIMAGETOOL_VERSION}.AppImage"
if [ ! -x "$AIT" ]; then
    echo ">> fetching appimagetool ${APPIMAGETOOL_VERSION}"
    mkdir -p "$(dirname "$AIT")"
    curl -fsSL -o "$AIT" "https://github.com/AppImage/appimagetool/releases/download/${APPIMAGETOOL_VERSION}/appimagetool-x86_64.AppImage"
    got="$(sha256sum "$AIT" | cut -d' ' -f1)"
    if [ "$got" != "$APPIMAGETOOL_SHA256" ]; then
        echo "appimagetool sha256 mismatch:" >&2
        echo "  expected $APPIMAGETOOL_SHA256 (update the pin in tools/versions?)" >&2
        echo "  got      $got" >&2
        rm -f "$AIT"
        exit 1
    fi
    chmod +x "$AIT"
fi

echo ">> assembling a portable Java runtime from the bundled JDK"
rm -rf build/otin build/appimage build/runtime AppDir
mkdir -p build/otin
cp "$JAR" build/otin/
# The runtime must keep bin/java and a full module set: the bundled scan engine
# runs as its own Java process (launched with java.home/bin/java), and jpackage's
# default runtime strips bin/java and trims modules, which breaks it. The portable
# Temurin JDK (old glibc) ships without jmods, so jlink cannot repackage it; copy
# it as the runtime image instead and drop only parts not needed at run time.
cp -a jdk build/runtime
rm -rf build/runtime/lib/src.zip build/runtime/include build/runtime/man \
       build/runtime/demo build/runtime/sample build/runtime/jmods

echo ">> jpackage: self-contained app image using that runtime"
jpackage --type app-image --name OpenTRAP \
    --input build/otin --main-jar "$JARNAME" --main-class opentrap.ui.Main \
    --icon src/opentrap/ui/icons/opentrap_256.png \
    --runtime-image build/runtime --java-options "-XX:MaxRAMPercentage=50 -XX:+UseG1GC -XX:G1PeriodicGCInterval=300000" \
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
# Java's X11 toolkit assumes a reparenting window manager. Under the tiling
# and wlroots-family compositors below, Swing windows paint blank white unless
# this hint is set. Set it only when the caller has not set it themselves; it
# is harmless where it is not needed.
if [ -z "${_JAVA_AWT_WM_NONREPARENTING+x}" ]; then
  for d in $(printf '%s' "${XDG_CURRENT_DESKTOP:-}" | tr ':' ' ' | tr '[:upper:]' '[:lower:]'); do
    case "$d" in
      sway|i3|hyprland|river|dwl|bspwm|labwc) export _JAVA_AWT_WM_NONREPARENTING=1; break ;;
    esac
  done
fi
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
