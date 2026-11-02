#!/usr/bin/env bash
# Renders the floor plan to PNG on the PC, with the same Java classes the TV runs (no lens effects).
#   tools/shot.sh OUT_DIR W H T [T...]        e.g. tools/shot.sh shots/pc 1920 1080 0 37.5 1793500000
#   tools/shot.sh --stats [W H [FRAMES [T0]]] prints the layout scorecard (tools/PlanStats.java)
# Needs a JDK 17 (javac on PATH, or JAVA_HOME). Compiles into build/plantools.
set -euo pipefail
cd "$(dirname "$0")/.."
bin=${JAVA_HOME:+$JAVA_HOME/bin/}
out=build/plantools
src=app/src/main/java/io/github/pbanddev/tatamihome
rm -rf "$out"
mkdir -p "$out"
# only the classes without Android in them
"${bin}javac" -nowarn -d "$out" $(grep -L 'import android' "$src"/*.java) tools/PlanShot.java tools/PlanStats.java
main=PlanShot
if [ "${1:-}" = "--stats" ]; then
    main=PlanStats
    shift
fi
"${bin}java" -cp "$out" "io.github.pbanddev.tatamihome.$main" "$@"
