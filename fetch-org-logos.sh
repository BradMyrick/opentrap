#!/usr/bin/env bash
# One-off helper: download the official logos of the organisations credited in
# the "Supporting software freedom" section into docs/images/orgs/.
#
# EFF, Software Freedom Conservancy and the FSF are pulled straight from the
# organisations' own sites. The OSI keyhole and the FSFE dove are resolved
# through the Wikimedia Commons API, where each file's licence is documented.
#
# Run from the repo root:  bash fetch-org-logos.sh
set -u
DIR="docs/images/orgs"
UA="Mozilla/5.0 (X11; Linux x86_64)"
mkdir -p "$DIR"

ok=0; fail=0

valid() { # path -> is it a non-trivial image (svg/png), not an error page
    [ -s "$1" ] || return 1
    local head; head=$(head -c 6 "$1" | tr -d '\0')
    case "$head" in
        "<svg"*|"<?xml"*) return 0 ;;                 # SVG
        $'\x89PNG'*) return 0 ;;                       # PNG magic
    esac
    # PNG magic can be awkward to match in case; fall back to `file`
    file -b "$1" 2>/dev/null | grep -qiE "svg|png image" && return 0
    return 1
}

dl() { # url outfile
    curl -fsSL -A "$UA" "$1" -o "$2" 2>/dev/null
    if valid "$2"; then
        echo "  ok    $2  ($(wc -c < "$2") bytes)"; ok=$((ok+1))
    else
        echo "  FAIL  $2  <- $1"; rm -f "$2"; fail=$((fail+1))
    fi
}

commons() { # File:Title outfile
    local url
    url=$(curl -fsSL -A "$UA" \
        "https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url&titles=$1" \
        2>/dev/null | python3 -c "import sys,json
try:
    p=list(json.load(sys.stdin)['query']['pages'].values())[0]
    ii=p.get('imageinfo')
    print(ii[0]['url'].split('?')[0] if ii else '')
except Exception:
    print('')")
    if [ -n "$url" ]; then dl "$url" "$2"; else echo "  FAIL  $2  <- commons:$1"; fail=$((fail+1)); fi
}

echo "Fetching organisation logos into $DIR ..."
# Remove the placeholder monograms these replace.
rm -f "$DIR/eff.svg" "$DIR/sfc.svg" "$DIR/fsf.svg" "$DIR/osi.svg" "$DIR/fsfe.svg"

dl "https://www.eff.org/sites/all/modules/custom/eff_library/images/eff-logo-stack-black.svg" "$DIR/eff.svg"
dl "https://sfconservancy.org/static/img/conservancy-header-logo.322da8acaa91.svg"            "$DIR/sfc.svg"
dl "https://static.fsf.org/common/img/logo-new.png"                                           "$DIR/fsf.png"
commons "File:Open_Source_Initiative_keyhole.svg" "$DIR/osi.svg"
commons "File:Dove-logo-2012.svg"                  "$DIR/fsfe.svg"

echo
echo "Done: $ok fetched, $fail failed."
echo "Files now in $DIR:"
ls -la "$DIR"
[ "$fail" -gt 0 ] && echo "Any FAILs above: grab that logo by hand from the org's brand page and save it in $DIR."
[ "$fail" -gt 0 ] && exit 1
exit 0
