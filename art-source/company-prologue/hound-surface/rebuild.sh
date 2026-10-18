#!/usr/bin/env bash
# Replay the Hound (A) surfacing except the Codex generations (generated/ and
# reference/finish-reference.png are retained). Runs the shared harness in
# ../second-wind-surface with this folder as --hull. Needs Blender 5.2 and Python
# with numpy + Pillow. Bulky output goes to build/ (ignored).
set -euo pipefail
cd "$(dirname "$0")"
HS=$(pwd -W 2>/dev/null || pwd)
. ../../blender.sh
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SURF|Error|Traceback" || true; }
GEOM=../hound-geometry
cd ../second-wind-surface

# 1. Clean model into build/geom (geometry imported, never edited).
blend "$GEOM/build_hound.py" -- --out "$HS/build/geom" --views three-quarter --samples 8
# 2. Finish reference: guide and class map from the reference camera, registration, palette.
blend render_reference_classes.py -- --hull "$HS"
python register_reference.py guide --hull "$HS"
#   (Codex, not replayed) prompts/finish-reference.prompt.txt -> reference/finish-reference.png
python register_reference.py --hull "$HS"
python palette.py --hull "$HS"
# 3. Parts: face selection, guides, sheets; owned tone offsets.
blend render_parts.py -- --hull "$HS"
python sheets.py compose --hull "$HS"
python variation.py --hull "$HS"
#   (Codex, not replayed) one call per sheet: prompts/sheet-<s>.prompt.txt -> generated/sheet-<s>.png
#   with --ref guide/sheet-<s>-guide.png and --ref reference/finish-reference.png
# 4. Register, check, de-light, grade; class swatches.
python register.py --hull "$HS"
python grade.py --hull "$HS"
python swatches.py --hull "$HS"
# 5. Apply with light service wear, review.
blend apply_skin.py -- --hull "$HS" --wear service
blend apply_skin.py -- --hull "$HS" --out build/skinned-nowear.blend --manifest build/skin-manifest-nowear.json
blend review.py -- --hull "$HS" --samples 48
python compose_review.py --hull "$HS"
