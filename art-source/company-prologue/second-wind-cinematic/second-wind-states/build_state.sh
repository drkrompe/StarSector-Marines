#!/usr/bin/env bash
# Rebuild one condition state end to end: geometry -> base skin + base wear -> export (+ state overlay).
#   ./build_state.sh <derelict|patched|refit|restored> [--no-save] [--skip-geom]
# Scratch builds go to ../../second-wind-surface/build/ (ignored). No Codex calls.
set -euo pipefail
cd "$(dirname "$0")"
STATE=$1; shift
. ../../../blender.sh
SURF=$(cd ../../second-wind-surface && pwd)
EXTRA=(); SKIP_GEOM=0
for a in "$@"; do case $a in --skip-geom) SKIP_GEOM=1;; *) EXTRA+=("$a");; esac; done
mkdir -p "$SURF/build/logs"
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
if [ $SKIP_GEOM = 0 ]; then
  run ../../second-wind-geometry/build_reference.py -- --state "$STATE" --out "$SURF/build/$STATE-geom" --views= \
    > "$SURF/build/logs/geom-$STATE.log" 2>&1
  run "$SURF/apply_skin.py" -- --blend "build/$STATE-geom/second-wind-geometry.blend" \
    --out "build/skinned-$STATE.blend" --manifest "build/skin-manifest-$STATE.json" --wear "$STATE" \
    > "$SURF/build/logs/skin-$STATE.log" 2>&1
fi
run export_state.py -- --state "$STATE" "${EXTRA[@]}" 2>&1 | tee "$SURF/build/logs/export-$STATE.log" | grep -E "EXPORT|Error|Traceback|line [0-9]"
