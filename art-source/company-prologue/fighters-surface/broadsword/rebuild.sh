#!/usr/bin/env bash
# Replay the Broadsword surfacing except the Codex generations (generated/ and
# reference/finish-reference.png are retained). Runs the shared harness in
# ../../second-wind-surface with this folder as --hull. Needs Blender 5.2 and
# Python with numpy + Pillow. Bulky output goes to build/ (ignored).
set -euo pipefail
cd "$(dirname "$0")"
HS=$(pwd -W 2>/dev/null || pwd)
. ../../../blender.sh
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SURF|Error|Traceback" || true; }
GEOM=$(cd ../../broadsword-geometry && (pwd -W 2>/dev/null || pwd))
cd ../../second-wind-surface

# 1. Clean model into build/geom, gear up (geometry imported, never edited).
(cd "$GEOM" && "$BLENDER" --background --factory-startup --python build_broadsword.py -- --gear up \
    --out "$HS/build/geom" --views three-quarter --samples 8 2>&1 | grep -E "Error|Traceback" || true)
"$BLENDER" --background "$HS/build/geom/broadsword-geometry.blend" --python "$HS/../strip_review_props.py" 2>&1 | grep -E "SURF|Error|Traceback" || true
# 2. Finish reference: guide and class map from the reference camera, registration, palette.
blend render_reference_classes.py -- --hull "$HS"
"$PYTHON" register_reference.py guide --hull "$HS"
#   (Codex, not replayed) prompts/finish-reference.prompt.txt -> reference/finish-reference.png
"$PYTHON" register_reference.py --hull "$HS"
"$PYTHON" palette.py --hull "$HS"
# 3. Parts: face selection, guides, sheets; owned tone offsets.
blend render_parts.py -- --hull "$HS"
"$PYTHON" sheets.py compose --hull "$HS"
"$PYTHON" variation.py --hull "$HS"
#   (Codex, not replayed) one call per sheet: prompts/sheet-<s>.prompt.txt -> generated/sheet-<s>.png
#   with --ref guide/sheet-<s>-guide.png and --ref reference/finish-reference.png
# 4. Register, check, de-light, grade; class swatches.
"$PYTHON" register.py --hull "$HS"
"$PYTHON" grade.py --hull "$HS"
"$PYTHON" swatches.py --hull "$HS"
# 5. Apply with light service wear, review.
blend apply_skin.py -- --hull "$HS" --wear service
blend review.py -- --hull "$HS" --samples 48
"$PYTHON" compose_review.py --hull "$HS"
