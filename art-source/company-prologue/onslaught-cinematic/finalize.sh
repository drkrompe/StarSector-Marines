#!/usr/bin/env bash
# Rebuild, verify and review the Onslaught cinematic asset end to end.
#   ./finalize.sh [--turn N] [--samples S] [--no-review]
# Input is the textured build ../onslaught-surface/build/skinned.blend (from
# ../onslaught-surface/rebuild.sh; run that first if it is missing). Writes
# build/export/ (the geometry export of the skinned build), onslaught.blend,
# manifest.json and validation.json here, renders to build/review/ and sheets and
# the turn movie to review/ (the .blend, build/ and review/ are ignored).
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
TURN=72; SAMPLES=32; REVIEW=1
while [ $# -gt 0 ]; do case $1 in
  --turn) TURN=$2; shift;; --samples) SAMPLES=$2; shift;; --no-review) REVIEW=0;; esac; shift; done
SKINNED=../onslaught-surface/build/skinned.blend
[ -f "$SKINNED" ] || { echo "missing $SKINNED: run ../onslaught-surface/rebuild.sh first" >&2; exit 1; }
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
mkdir -p build
# 1. The geometry package's own export, run on the textured build (one collection, packed images).
"$BLENDER" --background "$SKINNED" --python-exit-code 1 --python ../onslaught-geometry/export_onslaught.py \
  -- --out build/export > build/export.log 2>&1 || { tail -20 build/export.log; exit 1; }
# 2. Kit gun anchors and the SWFX effects collection -> onslaught.blend + manifest.json.
run export_effects.py 2>&1 | grep -E "EFFECTS|Error|Traceback"
# 3. Append into an empty scene and check -> validation.json (exit 1 on failure).
run verify_ship.py 2>&1 | grep -E "VERIFY|BAD|Error|Traceback"
[ $REVIEW = 1 ] || exit 0
# 4. Renders (Cycles, 1920x1080 at 100%) and the composed review.
R=(review_ship.py -- --samples "$SAMPLES")
run "${R[@]}" --views three-quarter,stern-quarter | grep -E "REVIEW|Error" || true
run "${R[@]}" --effects --views three-quarter,stern-quarter,engine-detail,formation-1.5km | grep -E "REVIEW|Error" || true
run "${R[@]}" --effects --lights-off --views engine-detail,stern-quarter | grep -E "REVIEW|Error" || true
if [ "$TURN" -gt 0 ]; then run "${R[@]}" --effects --views= --turn "$TURN" | grep -E "REVIEW|Error" || true; fi
# compose_review.py needs Pillow: the first of $PYTHON and /usr/bin/python3 that has it.
for PY in "$PYTHON" /usr/bin/python3; do "$PY" -c 'import PIL' 2>/dev/null && break; done
"$PY" compose_review.py
