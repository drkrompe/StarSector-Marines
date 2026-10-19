#!/usr/bin/env bash
# Replay the Hound's independent livery except the Codex generations (generated/
# and reference/finish-reference.png are retained). Needs the Hound's clean build:
# run ../hound-surface/rebuild.sh first (its step 1 writes build/geom). Shared
# harness in ../second-wind-surface with this folder as --hull.
set -euo pipefail
cd "$(dirname "$0")"
HS=$(pwd -W 2>/dev/null || pwd)
. ../../blender.sh
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SURF|Error|Traceback" || true; }
cd ../second-wind-surface
# 1. Finish reference guide, registration, palette (wear colours are owned in palette.json).
blend render_reference_classes.py -- --hull "$HS"
"$PYTHON" register_reference.py guide --hull "$HS"
#   (Codex, not replayed) prompts/finish-reference.prompt.txt -> reference/finish-reference.png
#   with --ref guide/reference-guide.png and --ref reference/livery-key.png (the independent Lasher)
"$PYTHON" register_reference.py --hull "$HS"
"$PYTHON" palette.py --hull "$HS"
# 2. Parts: the Hound's own tables and faces; guides, sheets, owned tone offsets.
blend render_parts.py -- --hull "$HS"
"$PYTHON" sheets.py compose --hull "$HS"
"$PYTHON" swatches.py guide --hull "$HS"
"$PYTHON" variation.py --hull "$HS"
#   (Codex, not replayed) prompts/sheet-<s>.prompt.txt -> generated/sheet-<s>.png
#   with --ref guide/sheet-<s>-guide.png and --ref reference/finish-reference.png
# 3. Register, check, de-light, grade; class swatches; apply with service wear; review.
"$PYTHON" register.py --hull "$HS"
"$PYTHON" grade.py --hull "$HS"
"$PYTHON" swatches.py --hull "$HS"
blend apply_skin.py -- --hull "$HS" --wear service
blend review.py -- --hull "$HS" --samples 48
"$PYTHON" compose_review.py --hull "$HS"
# Export: ../hound-export/export_hound.py -- --livery independent
