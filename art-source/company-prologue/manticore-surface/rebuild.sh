#!/usr/bin/env bash
# Replay the Manticore (P) surfacing except the Codex generations (generated/ and
# reference/finish-reference.png are retained). Runs the shared harness in
# ../second-wind-surface with this folder as --hull. Needs Blender 5.2 and Python
# with numpy + Pillow ($PYTHON). Bulky output goes to build/ (ignored).
set -euo pipefail
cd "$(dirname "$0")"
HS=$(pwd -W 2>/dev/null || pwd)
. ../../blender.sh
PYTHON=${PYTHON:-python}
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SURF|Error|Traceback" || true; }
GEOM=../manticore-geometry
cd ../second-wind-surface

# 1. Clean model into build/geom (geometry imported, never edited; no render).
blend "$GEOM/build_manticore.py" -- --views none --keep-studio --out "$HS/build/geom"
# 2. Finish reference: build/sprite-livery.png is manticore_pirate.png from the install, turned
#    bow-right and scaled 4x nearest on the guide blue (never committed). Guide and class
#    map from the reference camera, registration, palette.
"$PYTHON" -c "
from PIL import Image; import os
p = os.environ.get('MANTICORE_SPRITE', r'C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core/graphics/ships/manticore/manticore_pirate.png')
s = Image.open(p).convert('RGBA'); s = s.rotate(-90, expand=True).resize((s.height*4, s.width*4), Image.NEAREST)
bg = Image.new('RGBA', s.size, (70, 110, 170, 255)); bg.alpha_composite(s); bg.convert('RGB').save(r'$HS/build/sprite-livery.png')"
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
# 5. Apply with battle wear, then the sprite's markings (skull, flame flashes); review.
blend apply_skin.py -- --hull "$HS" --wear battle
cd "$HS"; blend livery_decal.py -- --hull "$HS"; cd ../second-wind-surface
blend review.py -- --hull "$HS" --samples 16
"$PYTHON" compose_review.py --hull "$HS"
