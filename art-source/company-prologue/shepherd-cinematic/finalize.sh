#!/usr/bin/env bash
# Rebuild, verify and review the Shepherd cinematic asset end to end.
#   ./finalize.sh [--skip-surface] [--turn N] [--samples S]
# Needs the surfacing package's Codex sources (committed); renders go to
# ../shepherd-surface/build/final/ and review/ (both ignored).
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
SURF=$(cd ../shepherd-surface && pwd)
SKIP=0; TURN=96; SAMPLES=32
while [ $# -gt 0 ]; do case $1 in
  --skip-surface) SKIP=1;; --turn) TURN=$2; shift;; --samples) SAMPLES=$2; shift;; esac; shift; done
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
if [ $SKIP = 0 ]; then
  # Geometry (rest pose: turret at its slot angle) and the surfacing skin; part sources are the committed Codex sheets.
  mkdir -p "$SURF/build"
  run ../shepherd-geometry/build_shepherd.py -- --out "$SURF/build/geom" --views= > "$SURF/build/geom.log" 2>&1
  (cd "$SURF" && run ../second-wind-surface/apply_skin.py -- --hull . --wear service > build/apply.log 2>&1)
fi
run export_ship.py 2>&1 | grep -E "EXPORT|Error|Traceback"
run export_effects.py 2>&1 | grep -E "EFFECTS|Error|Traceback"
run verify_ship.py 2>&1 | grep -E "VERIFY|BAD|Error|Traceback"
R=(review_ship.py -- --samples "$SAMPLES")
run "${R[@]}" --views three-quarter,starboard-quarter,stern-quarter,under-quarter,turret-detail,dronebay-detail,top | grep -E "REVIEW|Error" || true
run "${R[@]}" --trained --views turret-detail | grep -E "REVIEW|Error" || true
run "${R[@]}" --effects --views three-quarter,stern-quarter,bow-quarter | grep -E "REVIEW|Error" || true
run "${R[@]}" --lights-off --views three-quarter | grep -E "REVIEW|Error" || true
if [ "$TURN" -gt 0 ]; then run "${R[@]}" --views= --turn "$TURN" | grep -E "REVIEW|Error" || true; fi
python compose_review.py
