#!/usr/bin/env bash
# Rebuild, verify and review the Legion (XIV) cinematic asset end to end.
#   ./finalize.sh [--skip-surface] [--turn N] [--samples S]
# The turnaround (--turn N, e.g. 96) is opt-in: it is most of the run time.
# Needs the surfacing package's graded textures (../legion-surface/textures/,
# made by ../legion-surface/rebuild.sh from the committed Codex sheets); renders
# go to ../legion-surface/build/final/ and review/ (both ignored).
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
SURF=$(cd ../legion-surface && (pwd -W 2>/dev/null || pwd))
SKIP=0; TURN=0; SAMPLES=32
while [ $# -gt 0 ]; do case $1 in
  --skip-surface) SKIP=1;; --turn) TURN=$2; shift;; --samples) SAMPLES=$2; shift;; esac; shift; done
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
if [ $SKIP = 0 ]; then
  [ -d ../legion-surface/textures ] || { echo "no graded textures: run ../legion-surface/rebuild.sh first"; exit 1; }
  mkdir -p "$SURF/build"
  # Geometry (rest pose: turrets at their slot angles, every keyable part stowed), cut along the
  # tile lines into the surfacing build, then the skin; part sources are the committed Codex sheets.
  run ../legion-geometry/build_legion.py -- --out "$SURF/build/geom" --views= > "$SURF/build/geom.log" 2>&1
  (cd ../legion-surface && "$BLENDER" --background --python-exit-code 1 "$SURF/build/geom/legion-geometry.blend" \
     --python make_groups.py > build/groups.log 2>&1)
  (cd ../legion-surface && run ../second-wind-surface/apply_skin.py -- --hull . --wear service > build/apply.log 2>&1)
fi
run export_ship.py 2>&1 | grep -E "EXPORT|Error|Traceback"
run export_effects.py 2>&1 | grep -E "EFFECTS|Error|Traceback"
run verify_ship.py 2>&1 | grep -E "VERIFY|BAD|Error|Traceback"
run ../second-wind-vfx/check_kit.py -- --blend legion.blend --state clean --hull . --fire 2>&1 | grep -E "CHECK|Error|Traceback"
R=(review_ship.py -- --samples "$SAMPLES")
run "${R[@]}" --views three-quarter,starboard-quarter,stern-quarter,under-quarter,turret-detail,launcher-detail,flight-deck-detail,engine-detail | grep -E "REVIEW|Error" || true
run "${R[@]}" --pose ops --views three-quarter,turret-detail,flight-deck-detail,launcher-detail | grep -E "REVIEW|Error" || true
run "${R[@]}" --effects --views stern-quarter,engine-detail | grep -E "REVIEW|Error" || true
# Firing: dozens of lit smoke volumes; half the samples (denoised) keeps a still near two minutes.
run review_ship.py -- --samples $((SAMPLES / 2)) --fire 30 --views three-quarter,turret-detail,launcher-detail,bow-quarter | grep -E "REVIEW|Error" || true
run "${R[@]}" --lights-off --views three-quarter,bow-quarter | grep -E "REVIEW|Error" || true
if [ "$TURN" -gt 0 ]; then run "${R[@]}" --views= --turn "$TURN" | grep -E "REVIEW|Error" || true; fi
"$PYTHON" compose_review.py
