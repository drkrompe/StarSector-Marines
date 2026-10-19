#!/usr/bin/env bash
# Rebuild, verify and review the Colossus cinematic asset end to end.
#   ./finalize.sh [--skip-surface] [--turn N] [--samples S]
# The turnaround (--turn N, e.g. 96) is opt-in: it is most of the run time.
# Needs the surfacing package's Codex sources (committed); renders go to
# ../colossus-surface/build/final/ and review/ (both ignored).
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
SURF=$(cd ../colossus-surface && pwd)
SKIP=0; TURN=0; SAMPLES=32
while [ $# -gt 0 ]; do case $1 in
  --skip-surface) SKIP=1;; --turn) TURN=$2; shift;; --samples) SAMPLES=$2; shift;; esac; shift; done
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
if [ $SKIP = 0 ]; then
  # Geometry (rest pose: turrets at their slot angles) and the surfacing skin; part sources are the
  # committed Codex sheets. The geometry driver needs one real view: an empty --views hangs it.
  mkdir -p "$SURF/build"
  run ../colossus-geometry/build_colossus.py -- --out "$SURF/build/geom" --views three-quarter --samples 8 \
    > "$SURF/build/geom.log" 2>&1
  (cd "$SURF" && run ../second-wind-surface/apply_skin.py -- --hull . --wear service > build/apply.log 2>&1)
fi
run export_ship.py 2>&1 | grep -E "EXPORT|Error|Traceback"
run export_effects.py 2>&1 | grep -E "EFFECTS|Error|Traceback"
run verify_ship.py 2>&1 | grep -E "VERIFY|BAD|Error|Traceback"
R=(review_ship.py -- --samples "$SAMPLES")
run "${R[@]}" --views three-quarter,starboard-quarter,stern-quarter,under-quarter,turret-detail,engine-detail,top \
  | grep -E "REVIEW|Error" || true
run "${R[@]}" --trained --views turret-detail | grep -E "REVIEW|Error" || true
run "${R[@]}" --cruise --views stern-quarter | grep -E "REVIEW|Error" || true
run "${R[@]}" --effects --views three-quarter,lift-low | grep -E "REVIEW|Error" || true
run "${R[@]}" --lights-off --views three-quarter | grep -E "REVIEW|Error" || true
if [ "$TURN" -gt 0 ]; then run "${R[@]}" --views= --turn "$TURN" | grep -E "REVIEW|Error" || true; fi
python compose_review.py
