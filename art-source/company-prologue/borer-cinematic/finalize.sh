#!/usr/bin/env bash
# Rebuild, export and check the Borer cinematic asset end to end. Nothing here renders.
#   ./finalize.sh [--skip-surface] [--publish]
# Needs the surfacing package's Codex sources (committed) and a local copy of the accepted
# Shepherd build at build/ships/shepherd/shepherd.blend (from Drive builds/shepherd/<commit>/)
# for the cradle check. --publish copies the asset to Drive builds/borer/<commit>/.
# Review renders go through the render queue (review.job.json), never this shell.
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
PYTHON=${PYTHON:-python}
HERE=$(pwd -W 2>/dev/null || pwd)
SURF=$(cd ../fighters-surface/borer && (pwd -W 2>/dev/null || pwd))
REPO=$(cd ../../.. && (pwd -W 2>/dev/null || pwd))
SKIP=0; PUBLISH=0
while [ $# -gt 0 ]; do case $1 in --skip-surface) SKIP=1;; --publish) PUBLISH=1;; esac; shift; done
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
if [ $SKIP = 0 ]; then
  (cd ../fighters-geometry && run build_fighter.py -- --fighter borer --out "$SURF/build/geom" --views none > "$SURF/build/geom.log" 2>&1)
  "$BLENDER" --background "$SURF/build/geom/borer-geometry.blend" --python "$SURF/../strip_review_props.py" 2>&1 | grep -E "SURF|Error" || true
  (cd ../second-wind-surface && run apply_skin.py -- --hull "$SURF" --wear service > "$SURF/build/apply.log" 2>&1)
fi
run export_ship.py 2>&1 | grep -E "EXPORT|Error|Traceback"
run export_effects.py 2>&1 | grep -E "EFFECTS|Error|Traceback"
run verify_ship.py 2>&1 | grep -E "VERIFY|BAD|Error|Traceback"
(cd ../fighters-geometry && run borer_cradle.py -- --shepherd "$REPO/build/ships/shepherd/shepherd.blend" \
    --borer "$HERE/borer.blend" --collection BORER --out "$HERE/cradle-check.json" 2>&1 | grep -E "CRADLE .*(all|FAIL|BAD)|Error|Traceback")
if [ $PUBLISH = 1 ]; then
  "$PYTHON" publish_build.py
fi
