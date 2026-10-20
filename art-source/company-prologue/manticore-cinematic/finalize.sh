#!/usr/bin/env bash
# Rebuild and verify the Manticore (P) cinematic asset end to end. No renders.
#   ./finalize.sh [--skip-surface] [--in PATH --state clean|battle]
# Without --skip-surface it replays the surfacing tail (clean geometry, the skin with
# the battle wear, the sprite's markings) into ../manticore-surface/build/; the Codex
# sheets are committed and not regenerated. --in exports another build instead (and
# skips the surface). build/export/manticore-p.blend is built, not committed.
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
SURF=$(cd ../manticore-surface && (pwd -W 2>/dev/null || pwd))
SKIP=0; IN="$SURF/build/skinned.blend"; STATE=battle
while [ $# -gt 0 ]; do case $1 in
  --skip-surface) SKIP=1;; --in) IN=$2; SKIP=1; shift;; --state) STATE=$2; shift;; esac; shift; done
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
if [ $SKIP = 0 ]; then
  mkdir -p "$SURF/build"
  (cd ../manticore-geometry && run build_manticore.py -- --views none --keep-studio --out "$SURF/build/geom" \
    > "$SURF/build/geom.log" 2>&1)
  (cd ../second-wind-surface && run apply_skin.py -- --hull "$SURF" --wear battle > "$SURF/build/apply.log" 2>&1)
  (cd "$SURF" && run livery_decal.py -- --hull "$SURF" > build/decal.log 2>&1)
fi
run export_ship.py -- --in "$IN" --state "$STATE" 2>&1 | grep -E "EXPORT|Error|Traceback"
run export_effects.py 2>&1 | grep -E "EFFECTS|Error|Traceback"
run verify_ship.py 2>&1 | grep -E "VERIFY|BAD|Error|Traceback"
