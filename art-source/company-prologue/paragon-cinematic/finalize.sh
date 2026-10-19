#!/usr/bin/env bash
# Rebuild, verify and review the Paragon cinematic asset end to end.
#   ./finalize.sh [--samples S] [--no-review]
# Input is the textured build ../paragon-surface/build/skinned.blend (from
# ../paragon-surface/rebuild.sh; run that first if it is missing). Writes
# build/export/ (the geometry export of the skinned build), paragon.blend,
# manifest.json and validation.json here, renders to build/review/ and sheets to
# review/ (the .blend, build/ and review/ are ignored).
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
SAMPLES=32; REVIEW=1
while [ $# -gt 0 ]; do case $1 in
  --samples) SAMPLES=$2; shift;; --no-review) REVIEW=0;; esac; shift; done
SKINNED=../paragon-surface/build/skinned.blend
[ -f "$SKINNED" ] || { echo "missing $SKINNED: run ../paragon-surface/rebuild.sh first" >&2; exit 1; }
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
mkdir -p build
# 1. The geometry package's own export, run on the textured build (one collection, packed images).
"$BLENDER" --background "$SKINNED" --python-exit-code 1 --python ../paragon-geometry/export_paragon.py \
  -- --out build/export > build/export.log 2>&1 || { tail -20 build/export.log; exit 1; }
# 2. Engine and kit gun anchors, the SWFX effects collection (with beams) -> paragon.blend + manifest.json.
run export_effects.py 2>&1 | grep -E "EFFECTS|Error|Traceback"
# 3. Append into an empty scene and check -> validation.json (exit 1 on failure).
run verify_ship.py 2>&1 | grep -E "VERIFY|BAD|Error|Traceback"
[ $REVIEW = 1 ] || exit 0
# 4. Renders (Cycles, 1920x1080 at 100%) and the composed review.
R=(review_ship.py -- --samples "$SAMPLES")
run "${R[@]}" --views three-quarter,stern-quarter | grep -E "REVIEW|Error" || true
run "${R[@]}" --effects --views three-quarter,stern-quarter,engine-detail,formation-1.5km | grep -E "REVIEW|Error" || true
run "${R[@]}" --effects --lights-off --views engine-detail,stern-quarter | grep -E "REVIEW|Error" || true
run "${R[@]}" --effects --beams --point-defense --lance-sequence --views= | grep -E "REVIEW|Error" || true
# compose_review.py needs Pillow: the first of $PYTHON and /usr/bin/python3 that has it.
for PY in "${PYTHON:-python3}" /usr/bin/python3; do "$PY" -c 'import PIL' 2>/dev/null && break; done
"$PY" compose_review.py
