#!/usr/bin/env bash
# Replay the Colossus surfacing except the Codex generations (generated/ and
# reference/finish-reference.png are retained). Runs the shared harness in
# ../second-wind-surface with this folder as --hull. Needs Blender 5.2 and Python
# with numpy + Pillow. Bulky output goes to build/ (ignored).
set -euo pipefail
cd "$(dirname "$0")"
HS=$(pwd -W 2>/dev/null || pwd)
. ../../blender.sh
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SURF|Error|Traceback" || true; }
GEOM=../colossus-geometry
cd ../second-wind-surface

# 1. Clean model into build/geom (geometry imported, never edited).
blend "$GEOM/build_colossus.py" -- --out "$HS/build/geom" --views three-quarter --samples 8
# 2. Finish reference: build/sprite-livery.png is colossus.png from the install, turned
#    bow-right and scaled 4x nearest on the guide blue (never committed). Guide and class
#    map from the reference camera, registration, palette.
"$PYTHON" -c "
from PIL import Image; import os, sys
p = os.environ.get('COLOSSUS_SPRITE', r'C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core/graphics/ships/colossus/colossus.png')
s = Image.open(p).convert('RGBA'); s = s.rotate(-90, expand=True).resize((s.height*4, s.width*4), Image.NEAREST)
bg = Image.new('RGBA', s.size, (70, 110, 170, 255)); bg.alpha_composite(s); bg.convert('RGB').save('$HS/build/sprite-livery.png')"
blend render_reference_classes.py -- --hull "$HS"
"$PYTHON" register_reference.py guide --hull "$HS"
#   (Codex, not replayed) prompts/finish-reference.prompt.txt -> reference/finish-reference.png
#   with --ref guide/reference-guide.png and --ref build/sprite-livery.png
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
# 5. Apply with service wear, review.
blend apply_skin.py -- --hull "$HS" --wear service
blend review.py -- --hull "$HS" --samples 48
"$PYTHON" compose_review.py --hull "$HS"
