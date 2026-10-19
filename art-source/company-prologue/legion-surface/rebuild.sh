#!/usr/bin/env bash
# Replay the Legion surfacing except the Codex generations (generated/ and
# reference/finish-reference.png are retained). Runs the shared harness in
# ../second-wind-surface with this folder as --hull. Needs Blender 5.2 and Python
# with numpy + Pillow. Bulky output goes to build/ (ignored).
set -euo pipefail
cd "$(dirname "$0")"
HS=$(pwd -W 2>/dev/null || pwd)
. ../../blender.sh
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SURF|Error|Traceback" || true; }
GEOM=../legion-geometry

# 1. Clean model into build/geom (geometry imported, never edited); box-tile part tables from it.
blend "$GEOM/build_legion.py" -- --out "$HS/build/geom" --views three-quarter --samples 8
"$BLENDER" --background "$HS/build/geom/legion-geometry.blend" --python make_groups.py 2>&1 | grep -E "SURF|Error|Traceback" || true
cd ../second-wind-surface
# 2. Finish reference (reference/sprite-livery.png is legion_xiv.png scaled 2x, nearest): guide and class map from the reference camera, registration, palette.
blend render_reference_classes.py -- --hull "$HS"
"$PYTHON" register_reference.py guide --hull "$HS"
#   (Codex, not replayed) prompts/finish-reference.prompt.txt -> reference/finish-reference.png
"$PYTHON" register_reference.py --hull "$HS"
"$PYTHON" palette.py --hull "$HS"
# 3. Parts: face selection, guides, sheets; owned tone offsets; swatch guide.
blend render_parts.py -- --hull "$HS"
"$PYTHON" sheets.py compose --hull "$HS"
"$PYTHON" variation.py --hull "$HS"
"$PYTHON" swatches.py guide --hull "$HS"
#   (Codex, not replayed) one call per sheet: prompts/sheet-<s>.prompt.txt -> generated/sheet-<s>.png
#   with --ref guide/sheet-<s>-guide.png and --ref reference/finish-reference.png
# 4. Register, check, de-light, grade; class swatches.
"$PYTHON" register.py --hull "$HS"
"$PYTHON" grade.py --hull "$HS"
"$PYTHON" swatches.py --hull "$HS"
# 5. Apply with light service wear, review.
blend apply_skin.py -- --hull "$HS" --wear service
blend apply_skin.py -- --hull "$HS" --out build/skinned-nowear.blend --manifest build/skin-manifest-nowear.json
blend review.py -- --hull "$HS" --samples 48
"$PYTHON" compose_review.py --hull "$HS"
