#!/usr/bin/env bash
# Rebuild, verify and review the Kite (P) cinematic asset end to end.
#   ./finalize.sh [--skip-surface] [--samples S]
# Needs the surfacing package's Codex sources (committed); renders go to
# ../kite-surface/build/final/ (ignored). kite-p.blend is built, not committed.
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
SURF=$(cd ../kite-surface && pwd)
SKIP=0; SAMPLES=16
while [ $# -gt 0 ]; do case $1 in
  --skip-surface) SKIP=1;; --samples) SAMPLES=$2; shift;; esac; shift; done
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
if [ $SKIP = 0 ]; then
  # Geometry, the surfacing skin with the pirate wear, then the claw-slash decal.
  mkdir -p "$SURF/build"
  run ../kite-geometry/build_kite.py -- --out "$SURF/build/geom" --views= > "$SURF/build/geom.log" 2>&1
  (cd ../second-wind-surface && run apply_skin.py -- --hull "$SURF" --wear pirate > "$SURF/build/apply.log" 2>&1)
  run "$SURF/livery_decal.py" -- --hull "$SURF" | grep -E "SURF|Error|Traceback"
fi
run export_ship.py 2>&1 | grep -E "EXPORT|Error|Traceback"
run export_effects.py 2>&1 | grep -E "EFFECTS|Error|Traceback"
run verify_ship.py 2>&1 | grep -E "VERIFY|BAD|Error|Traceback"
run review_ship.py -- --samples "$SAMPLES" --effects --views stern-quarter | grep -E "REVIEW|Error" || true
run review_ship.py -- --samples "$SAMPLES" --shot | grep -E "REVIEW|Error" || true
