#!/usr/bin/env bash
# Rebuild, verify and review the Phaeton cinematic assets (intact and destroyed) end to end.
#   ./finalize.sh [--skip-surface] [--samples S] [--device GPU|CPU]
# Needs the surfacing package's Codex sources (committed); exports land here
# (ignored), renders in ../phaeton-surface/build/final/ (ignored).
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
SURF=$(cd ../phaeton-surface && pwd)
SKIP=0; SAMPLES=32; DEVICE=GPU
while [ $# -gt 0 ]; do case $1 in
  --skip-surface) SKIP=1;; --samples) SAMPLES=$2; shift;; --device) DEVICE=$2; shift;; esac; shift; done
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
if [ $SKIP = 0 ]; then
  # Geometry (rest pose: turrets at their slot angles), the surfacing skin, then the damage on the skin.
  mkdir -p "$SURF/build"
  run ../phaeton-geometry/build_phaeton.py -- --out "$SURF/build/geom" --views three-quarter --engine workbench \
    > "$SURF/build/geom.log" 2>&1
  (cd "$SURF" && run ../second-wind-surface/apply_skin.py -- --hull . --wear service > build/apply.log 2>&1)
  run apply_damage.py 2>&1 | grep -E "DAMAGE|Error|Traceback"
fi
for st in clean destroyed; do
  run export_ship.py -- --state $st 2>&1 | grep -E "EXPORT|Error|Traceback"
  run export_effects.py -- --state $st 2>&1 | grep -E "EFFECTS|Error|Traceback"
  run verify_ship.py -- --state $st 2>&1 | grep -E "VERIFY|BAD|Error|Traceback"
done
R=(review_ship.py -- --samples "$SAMPLES" --device "$DEVICE")
run "${R[@]}" --mode clean --views three-quarter,starboard-quarter,stern-quarter | grep -E "REVIEW|Error" || true
run "${R[@]}" --mode clean --effects --views three-quarter,stern-quarter,s6 | grep -E "REVIEW|Error" || true
# The wreck's fire volumes: on a shared, busy GPU a frame stalled for 20+ minutes, while
# the CPU renders it in a few. They always render on CPU, at 16 samples.
W=(review_ship.py -- --samples 16 --device CPU)
run "${W[@]}" --mode destroyed --effects --views three-quarter,s6 | grep -E "REVIEW|Error" || true
run "${W[@]}" --mode staged --views wide | grep -E "REVIEW|Error" || true
python compose_review.py
