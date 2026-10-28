#!/usr/bin/env bash
# Report whether the tools OpenTRAP bundles have newer upstream releases.
#
# For developers, run before a release cycle. It does not change anything; it
# prints the pinned version, the latest upstream version, and whether an
# update is available, so you can decide what to bump in tools/versions and
# re-fetch. Deliberately not set -e: one failed upstream lookup must not stop
# the rest of the report.
set -u
DIR="$(cd "$(dirname "$0")/.." && pwd)"
source "$DIR/tools/versions"

row() { printf '  %-14s pinned %-14s latest %-14s %s\n' "$1" "$2" "$3" "$4"; }
flag() {
    if [ -z "$3" ]; then echo "lookup failed"
    elif [ "$2" != "$3" ]; then echo "UPDATE AVAILABLE"
    else echo "up to date"; fi
}

echo "OpenTRAP bundled tool versions:"
echo

# ZAP engine: latest GitHub release tag.
ZAP_LATEST=$(curl -fsS --max-time 20 https://api.github.com/repos/zaproxy/zaproxy/releases/latest 2>/dev/null \
    | grep -oE '"tag_name": *"[^"]*"' | head -1 | cut -d'"' -f4 | sed 's/^v//')
row "zap-engine" "${ZAP_VERSION:-?}" "${ZAP_LATEST:-?}" "$(flag zap "${ZAP_VERSION:-}" "$ZAP_LATEST")"

# Chromium: the latest snapshot revision for Linux x64.
CHROME_LATEST=$(curl -fsS --max-time 20 "https://commondatastorage.googleapis.com/chromium-browser-snapshots/Linux_x64/LAST_CHANGE" 2>/dev/null)
row "chromium" "$CHROMIUM_REVISION" "${CHROME_LATEST:-?}" "$(flag chromium "$CHROMIUM_REVISION" "$CHROME_LATEST")"

# BouncyCastle: latest bcprov-jdk18on on Maven Central.
BC_LATEST=$(curl -fsS --max-time 20 "https://search.maven.org/solrsearch/select?q=g:org.bouncycastle+AND+a:bcprov-jdk18on&rows=1&wt=json" 2>/dev/null \
    | grep -oE '"latestVersion":"[^"]*"' | head -1 | cut -d'"' -f4)
row "bouncycastle" "$BOUNCYCASTLE_VERSION" "${BC_LATEST:-?}" "$(flag bc "$BOUNCYCASTLE_VERSION" "$BC_LATEST")"

# Brotli decoder: latest org.brotli:dec on Maven Central.
BR_LATEST=$(curl -fsS --max-time 20 "https://search.maven.org/solrsearch/select?q=g:org.brotli+AND+a:dec&rows=1&wt=json" 2>/dev/null \
    | grep -oE '"latestVersion":"[^"]*"' | head -1 | cut -d'"' -f4)
row "brotli-dec" "$BROTLI_DEC_VERSION" "${BR_LATEST:-?}" "$(flag br "$BROTLI_DEC_VERSION" "$BR_LATEST")"

echo
echo "To update: edit tools/versions (a version and its SHA256 together),"
echo "then run the matching fetch script (./fetch-zap.sh, ./fetch-browser.sh)"
echo "or replace the jar in lib/."
