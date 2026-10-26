#!/usr/bin/env bash
# Launch OpenTRAP.
#
# Interface scale is worked out automatically from your screen, so this normally
# needs no configuration: a 4K display gets 200%, a 2.5K display 150%, anything
# smaller 100%. Whatever you pick from the app's view menu is remembered and
# overrides the detected value from then on.
#
# To force a scale for one launch:
#   ./run.sh 2                  (pass the scale as the first argument)
#   OPENTRAP_SCALE=1.5 ./run.sh (or an environment variable)
#
# Set SCALE below if you want a fixed scale on every launch. Leaving it empty
# is recommended: it lets detection and your view menu choice work.
SCALE=""

DIR="$(cd "$(dirname "$0")" && pwd)"

# Prefer the pinned portable JDK (fetch-jdk.sh) when present, so the app runs
# on the same runtime the release and AppImage use. Falls back to a system JDK
# if jdk/ is absent. Without this, run.sh fails with "java: not found" on a
# machine that has no system Java even though fetch-jdk.sh installed one.
if [ -x "$DIR/jdk/bin/java" ]; then
    export JAVA_HOME="$DIR/jdk"
    export PATH="$DIR/jdk/bin:$PATH"
fi

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

# First numeric argument, or the env var, overrides the default above.
if [[ "$1" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then SCALE="$1"; shift; fi
SCALE="${OPENTRAP_SCALE:-$SCALE}"

ARGS=()
if [[ -n "$SCALE" ]]; then
    # Setting the real HiDPI property here means the first JVM already has the
    # right scale, so the app does not have to relaunch itself to apply it.
    ARGS+=("-Dsun.java2d.uiScale=$SCALE" "-Dopentrap.scaled=$SCALE")
fi

# Size the max heap as a share of the machine's RAM rather than a fixed cap, so
# the app scales with the host. 50% leaves room for the scan engine and the
# testing browser, which run as their own processes outside this JVM.
exec java -XX:MaxRAMPercentage=50 -XX:+UseG1GC -XX:G1PeriodicGCInterval=300000 "${ARGS[@]}" -jar "$DIR/dist/opentrap.jar" "$@"
