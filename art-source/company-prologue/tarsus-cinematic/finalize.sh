#!/usr/bin/env bash
# Rebuild, verify and review both Tarsus cinematic states end to end.
#   ./finalize.sh [--skip-surface] [--samples S]
# Needs the surfacing package's committed Codex sources. Builds <state>/tarsus.blend
# (ignored) and the tracked <state>/manifest.json and validation.json; review stills go
# to ../tarsus-surface/build/final/ (ignored). Renders stay on the CPU: the shared GPU
# is usually saturated by other sessions.
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
SURF=$(cd ../tarsus-surface && pwd)
SKIP=0; SAMPLES=16
while [ $# -gt 0 ]; do case $1 in
  --skip-surface) SKIP=1;; --samples) SAMPLES=$2; shift;; esac; shift; done
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
if [ $SKIP = 0 ]; then
  # Clean geometry (turrets at their slot angles), the surfacing skin from the committed
  # Codex sheets, then the boarded scars on that skin (tarsus-geometry/ts_damage.py).
  mkdir -p "$SURF/build"
  run ../tarsus-geometry/build_tarsus.py -- --out "$SURF/build/geom" --views "" --cpu > "$SURF/build/geom.log" 2>&1
  (cd "$SURF" && run ../second-wind-surface/apply_skin.py -- --hull . --wear service > build/apply.log 2>&1)
  run ../tarsus-geometry/damage_blend.py -- "$SURF/build/skinned.blend" "$SURF/build/boarded/skinned-boarded.blend" \
    2>&1 | grep -E "SCAR|Error|Traceback"
fi
for st in clean boarded; do
  run export_ship.py -- --state $st 2>&1 | grep -E "EXPORT|Error|Traceback"
  run export_effects.py -- --state $st 2>&1 | grep -E "EFFECTS|Error|Traceback"
  run verify_ship.py -- --state $st 2>&1 | grep -E "VERIFY|BAD|Error|Traceback"
  run review_ship.py -- --state $st --effects --views s7-astern --samples "$SAMPLES" | grep -E "REVIEW|Error" || true
done
