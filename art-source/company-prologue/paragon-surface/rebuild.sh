#!/usr/bin/env bash
# Replay the Paragon surfacing except the Codex generations (generated/ and
# reference/finish-reference.png are retained). Runs the shared harness in
# ../second-wind-surface with this folder as --hull. Needs Blender 5.2 and Python
# with numpy + Pillow. Bulky output goes to build/ (ignored).
set -euo pipefail
cd "$(dirname "$0")"
HS=$(pwd -W 2>/dev/null || pwd)
. ../../blender.sh
PY=${PYTHON:-python3}
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SURF|Error|Traceback" || true; }
GEOM=../paragon-geometry

# 1. Clean model into build/geom (imported, never edited), with the hardpoint close-up camera.
(cd "$GEOM" && "$BLENDER" --background --factory-startup --python build_paragon.py -- --out "$HS/build/geom" \
    --views three-quarter --cam hp-close:250,-95,55:180,0,4:60 --samples 8 2>&1 | grep -E "Error|Traceback" || true)
cd ../second-wind-surface
# 2. Finish reference (build/sprite-livery.png: paragon.png turned bow-right, 3x, nearest, on the guide blue):
#    guide and class map from the reference camera, registration, palette.
blend render_reference_classes.py -- --hull "$HS"
"$PY" register_reference.py guide --hull "$HS"
#   (Codex, not replayed) prompts/finish-reference.prompt.txt -> reference/finish-reference.png
#   with --ref guide/reference-guide.png and --ref build/sprite-livery.png
"$PY" register_reference.py --hull "$HS"
"$PY" palette.py --hull "$HS"
# 3. Finish-only class swatches: guide, (Codex, not replayed) prompts/sheet-swatch.prompt.txt ->
#    generated/sheet-swatch.png with --ref guide/sheet-swatch-guide.png and --ref reference/finish-reference.png; grade.
"$PY" swatches.py guide --hull "$HS"
"$PY" swatches.py --hull "$HS"
# 4. Apply as an overlay over the hex ceramic (surface_hull.OVERLAY) with light service wear; review.
blend apply_skin.py -- --hull "$HS" --wear service
blend review.py -- --hull "$HS" --samples 48
"$PY" compose_review.py --hull "$HS"
