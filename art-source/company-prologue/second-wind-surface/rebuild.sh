#!/usr/bin/env bash
# Replay the surfacing pilot except the Codex generations (generated/ is retained).
# Needs Blender 5.2 and Python with numpy + Pillow. Bulky output goes to build/.
set -euo pipefail
cd "$(dirname "$0")"
BLENDER=${BLENDER:-"/c/Program Files/Blender Foundation/Blender 5.2/blender.exe"}
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SURF|Error|Traceback" || true; }

# 1. Clean model into build/geom (geometry modules imported, never edited).
blend ../second-wind-geometry/build_reference.py -- --out build/geom --views port-side --samples 4
# 2. Parts: face selection, part-list.json, guides; sheets; palette from the finish reference.
blend render_parts.py
python sheets.py compose
blend render_reference_classes.py
python palette.py
# 3. (Codex, not replayed) one call per sheet:
#   python ../../../tools/codex_imagegen.py --prompt prompts/sheet-<s>.prompt.txt --out generated/sheet-<s>.png \
#     --ref guide/sheet-<s>-guide.png="INPUT 1: ..." --ref reference/finish-reference.png="INPUT 2: ..."
# 4. Register, check, de-light, grade.
python register.py
python grade.py
# 5. Apply to the clean build, review.
blend apply_skin.py
blend review.py -- --samples 48
python compose_review.py
