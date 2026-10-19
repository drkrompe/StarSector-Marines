#!/usr/bin/env bash
# Rebuild, verify and review the Wolf (P) cinematic asset end to end.
#   ./finalize.sh [--samples S] [--no-review] [--variant pirate|merchant]
# Input is the textured build ../wolf-surface/build/skinned.blend (from
# ../wolf-surface/rebuild.sh; run that first if it is missing). Writes
# build/export/ (the packaged textured build), wolf-p.blend, manifest.json and
# validation.json here, renders to build/review/ and sheets to review/ (the
# .blend, build/ and review/ are ignored).
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
SAMPLES=32; REVIEW=1; VARIANT=pirate
while [ $# -gt 0 ]; do case $1 in
  --samples) SAMPLES=$2; shift;; --no-review) REVIEW=0;; --variant) VARIANT=$2; shift;; esac; shift; done
SKINNED=../wolf-surface/build/skinned.blend; EXPORT=build/export
if [ $VARIANT = merchant ]; then SKINNED=../wolf-merchant-surface/build/skinned.blend; EXPORT=build/export-merchant; fi
[ -f "$SKINNED" ] || { echo "missing $SKINNED: run ../wolf-surface/rebuild.sh first" >&2; exit 1; }
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
mkdir -p build
# 1. The textured build packaged as the appendable asset (one collection, packed images).
"$BLENDER" --background "$SKINNED" --python-exit-code 1 --python ../wolf-geometry/export_wolf.py \
  -- --out $EXPORT --variant $VARIANT > build/export.log 2>&1 || { tail -20 build/export.log; exit 1; }
# 2. The SWFX effects collection -> wolf-p.blend + manifest.json.
run export_effects.py -- --variant $VARIANT 2>&1 | grep -E "EFFECTS|Error|Traceback"
# 3. Append into an empty scene and check -> validation.json (exit 1 on failure).
run verify_ship.py -- --variant $VARIANT 2>&1 | grep -E "VERIFY|BAD|Error|Traceback"
[ $REVIEW = 1 ] || exit 0
[ $VARIANT = pirate ] || { echo 'review: use review_ship.py -- --variant merchant'; exit 0; }
# 4. Renders (Cycles, 1920x1080 at 100%) and the composed review.
R=(review_ship.py -- --samples "$SAMPLES")
run "${R[@]}" --views three-quarter,stern-quarter | grep -E "REVIEW|Error" || true
run "${R[@]}" --effects --views three-quarter,stern-quarter,engine-detail,asharu-s9 | grep -E "REVIEW|Error" || true
run "${R[@]}" --fire 30 --views three-quarter,bow-fire,asharu-s9 | grep -E "REVIEW|Error" || true
run "${R[@]}" --effects --lights-off --views stern-quarter | grep -E "REVIEW|Error" || true
for PY in "$PYTHON" /usr/bin/python3; do "$PY" -c 'import PIL' 2>/dev/null && break; done
"$PY" compose_review.py
