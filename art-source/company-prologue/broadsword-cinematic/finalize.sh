#!/usr/bin/env bash
# Rebuild, verify and review the Broadsword cinematic asset end to end.
#   ./finalize.sh [--skip-surface] [--turn N] [--samples S]
# Needs the surfacing package's Codex sources (committed); renders go to
# ../fighters-surface/broadsword/build/final/ and review/ (both ignored).
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
SURF=$(cd ../fighters-surface/broadsword && (pwd -W 2>/dev/null || pwd))
SKIP=0; TURN=96; SAMPLES=32
while [ $# -gt 0 ]; do case $1 in
  --skip-surface) SKIP=1;; --turn) TURN=$2; shift;; --samples) SAMPLES=$2; shift;; esac; shift; done
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
if [ $SKIP = 0 ]; then
  # Geometry (rest pose: gear up, doors shut) and the surfacing skin; part sources are the committed Codex sheets.
  (cd ../broadsword-geometry && run build_broadsword.py -- --gear up --out "$SURF/build/geom" --views none > "$SURF/build/geom.log" 2>&1)
  "$BLENDER" --background "$SURF/build/geom/broadsword-geometry.blend" --python "$SURF/../strip_review_props.py" 2>&1 | grep -E "SURF|Error" || true
  (cd ../second-wind-surface && run apply_skin.py -- --hull "$SURF" --wear service > "$SURF/build/apply.log" 2>&1)
fi
run export_ship.py 2>&1 | grep -E "EXPORT|Error|Traceback"
run export_effects.py 2>&1 | grep -E "EFFECTS|Error|Traceback"
run verify_ship.py 2>&1 | grep -E "VERIFY|BAD|Error|Traceback"
R=(review_ship.py -- --samples "$SAMPLES")
run "${R[@]}" --views port-quarter,starboard-quarter,stern-quarter,under-quarter,gun-detail,engine-detail | grep -E "REVIEW|Error" || true
run "${R[@]}" --pose landed --views port-quarter | grep -E "REVIEW|Error" || true
run "${R[@]}" --pose rest --views gear-detail | grep -E "REVIEW|Error" || true
run "${R[@]}" --effects --views port-quarter,stern-quarter,bow-quarter | grep -E "REVIEW|Error" || true
run "${R[@]}" --lights-off --views port-quarter | grep -E "REVIEW|Error" || true
if [ "$TURN" -gt 0 ]; then run "${R[@]}" --views= --turn "$TURN" | grep -E "REVIEW|Error" || true; fi
"$PYTHON" compose_review.py
