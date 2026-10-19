#!/usr/bin/env bash
# Replay the Falcon (P) surfacing except the Codex generations (generated/ and
# reference/finish-reference.png are retained). Runs the shared harness in
# ../second-wind-surface with this folder as --hull. Needs Blender 5.2 and Python
# with numpy + Pillow. Bulky output goes to build/ (ignored).
set -euo pipefail
cd "$(dirname "$0")"
HS=$(pwd -W 2>/dev/null || pwd)
. ../../blender.sh
PYTHON=${PYTHON:-python}
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SURF|Error|Traceback" || true; }
GEOM=../falcon-geometry
cd ../second-wind-surface

# 1. Clean model into build/geom (geometry imported, never edited): falcon-p-geometry.blend
#    (review scene with the build's cameras and studio) and falcon-p.blend.
blend "$GEOM/build_falcon.py" -- --out "$HS/build/geom" --views three-quarter --samples 8
# 2. Finish reference: build/sprite-livery.png is falcon_p.png from the install, turned
#    bow-right and scaled 4x nearest on the guide blue (never committed).
"$PYTHON" -c "
import sys; sys.path.insert(0, '../..')
from PIL import Image
from starsector_path import GAME_CORE
s = Image.open(GAME_CORE / 'graphics/ships/falcon/falcon_p.png').convert('RGBA')
s = s.rotate(-90, expand=True).resize((s.height*4, s.width*4), Image.NEAREST)
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
# 5. Apply with pirate wear, review.
blend apply_skin.py -- --hull "$HS" --wear pirate
blend review.py -- --hull "$HS" --samples 48
"$PYTHON" compose_review.py --hull "$HS"
# 6. The appendable asset with every image packed: build/export/falcon-p.blend.
"$BLENDER" --background "$HS/build/skinned.blend" --python "$GEOM/export_falcon.py" -- --out "$HS/build/export" 2>&1 | grep -E "exported|Error|Traceback" || true
# 7. Effects (the shared VFX kit, falcon-geometry/vfx_hull.py) and the append check.
"$BLENDER" --background --factory-startup --python "$GEOM/export_effects.py" -- "$HS/build/export/falcon-p.blend" 2>&1 | grep -E "EFFECTS|Error|Traceback" || true
"$BLENDER" --background --factory-startup --python "$GEOM/verify_asset.py" -- "$HS/build/export/falcon-p.blend" 2>&1 | grep -E "^OK|Error|Traceback" || true
