#!/usr/bin/env bash
# Replay the Borer surfacing except the Codex generations (generated/ and
# reference/finish-reference.png are retained). Runs the shared harness in
# ../../second-wind-surface with this folder as --hull. Needs Blender 5.2 and
# Python with numpy + Pillow. Bulky output goes to build/ (ignored).
#
# Steps 2a and 3a render (the reference guide and the part guides). In a session that may not
# render, run them as one render-queue light job instead (guides.job.json, SURF_GUIDE_SAMPLES=16)
# and copy its output back into this folder; BORER_SKIP_RENDERS=1 skips them here.
set -euo pipefail
cd "$(dirname "$0")"
HS=$(pwd -W 2>/dev/null || pwd)
. ../../../blender.sh
PYTHON=${PYTHON:-python}
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SURF|Error|Traceback" || true; }
GEOM=$(cd ../../fighters-geometry && (pwd -W 2>/dev/null || pwd))
cd ../../second-wind-surface

# 1. Clean model into build/geom (geometry imported, never edited; no render).
(cd "$GEOM" && "$BLENDER" --background --factory-startup --python build_fighter.py -- --fighter borer \
    --out "$HS/build/geom" --views none 2>&1 | grep -E "Error|Traceback" || true)
"$BLENDER" --background "$HS/build/geom/borer-geometry.blend" --python "$HS/../strip_review_props.py" 2>&1 | grep -E "SURF|Error|Traceback" || true
# 2. Finish reference: guide and class map from the reference camera, registration, palette.
[ -n "${BORER_SKIP_RENDERS:-}" ] || blend render_reference_classes.py -- --hull "$HS"            # 2a (renders)
"$PYTHON" register_reference.py guide --hull "$HS"
#   (Codex, not replayed) prompts/finish-reference.prompt.txt -> reference/finish-reference.png
"$PYTHON" register_reference.py --hull "$HS"
"$PYTHON" palette.py --hull "$HS"
# 3. Parts: face selection, guides, sheets; owned tone offsets.
[ -n "${BORER_SKIP_RENDERS:-}" ] || blend render_parts.py -- --hull "$HS"                        # 3a (renders)
"$PYTHON" sheets.py compose --hull "$HS"
"$PYTHON" variation.py --hull "$HS"
#   (Codex, not replayed) one call per sheet: prompts/sheet-<s>.prompt.txt -> generated/sheet-<s>.png
#   with --ref guide/sheet-<s>-guide.png and --ref reference/finish-reference.png
# 4. Register, check, de-light, grade; class swatches.
"$PYTHON" register.py --hull "$HS"
"$PYTHON" grade.py --hull "$HS"
"$PYTHON" swatches.py --hull "$HS"
# 5. Apply with light service wear (no render). Review renders go through the render queue.
blend apply_skin.py -- --hull "$HS" --wear service
