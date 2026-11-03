#!/usr/bin/env bash
# Writes the Tatami add-on into a cleanyourtv checkout as addons/tatami/: the committed files of this repo without
# docs/, home/, tools/ and this repo's README (see .gitattributes), plus docs/addon-README.md as its README.md.
#   tools/export-addon.sh ../cleanyourtv
set -euo pipefail
cd "$(dirname "$0")/.."
[ $# -eq 1 ] && [ -d "$1/addons" ] || { echo "usage: tools/export-addon.sh <cleanyourtv checkout>" >&2; exit 2; }
dest="$1/addons/tatami"
mkdir -p "$dest"
git archive HEAD | tar -x -C "$dest"
git show HEAD:docs/addon-README.md > "$dest/README.md"
echo "exported $(git rev-parse --short HEAD) to $dest"
