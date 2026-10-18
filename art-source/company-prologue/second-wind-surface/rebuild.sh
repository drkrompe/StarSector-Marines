#!/usr/bin/env bash
# Replay the whole-hull surfacing except the Codex generations (generated/ is retained).
# Needs Blender 5.2 and Python with numpy + Pillow. Bulky output goes to build/.
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SURF|Error|Traceback" || true; }

# 1. Clean model into build/geom, patched damage state into build/patched-geom
#    (geometry modules imported, never edited).
blend ../second-wind-geometry/build_reference.py -- --out "$PWD/build/geom" --views port-side --samples 4
blend ../second-wind-geometry/build_reference.py -- --state patched --out "$PWD/build/patched-geom" --views port-quarter --samples 4
blend ../second-wind-geometry/build_reference.py -- --state derelict --out "$PWD/build/derelict-geom" --views port-quarter --samples 4
# 2. Parts: face selection, part-list/<group>.json, guides; sheets; palette from the finish reference.
blend render_parts.py
python sheets.py compose
blend render_reference_classes.py
python palette.py
python variation.py
# 3. (Codex, not replayed) one call per sheet, prompts/sheet-<s>.prompt.txt -> generated/sheet-<s>.png:
#   python ../../../tools/codex_imagegen.py --prompt prompts/sheet-<s>.prompt.txt --out generated/sheet-<s>.png \
#     --ref guide/sheet-<s>-guide.png="INPUT 1: ..." --ref reference/finish-reference.png="INPUT 2: ..."
# 4. Register, check, de-light, grade; class swatches.
python register.py
python grade.py
python swatches.py
# 5. Apply to the clean and patched builds, review.
blend apply_skin.py
blend apply_skin.py -- --blend build/patched-geom/second-wind-geometry.blend --out build/skinned-patched.blend --manifest build/skin-manifest-patched.json --wear patched
blend apply_skin.py -- --blend build/derelict-geom/second-wind-geometry.blend --out build/skinned-derelict.blend --manifest build/skin-manifest-derelict.json --wear derelict
blend apply_skin.py -- --blend build/patched-geom/second-wind-geometry.blend --out build/skinned-patched-nowear.blend --manifest build/skin-manifest-nowear.json
blend review.py -- --samples 48
blend review.py -- --samples 48 --blend build/skinned-patched.blend --out build/review-patched --views port-quarter,starboard-quarter,port-flank-detail,stern-detail
blend review.py -- --samples 32 --blend build/skinned-patched-nowear.blend --out build/wear-nowear --views c1-starboard-quarter
blend review.py -- --samples 32 --blend build/skinned-patched.blend --out build/wear-patched --views c1-starboard-quarter,c5-starboard-stern,port-flank-detail,stern-detail,bow-detail
blend review.py -- --samples 32 --blend build/skinned-derelict.blend --out build/wear-derelict --views c1-starboard-quarter,c5-starboard-stern
python compose_review.py --patched build/review-patched
python compose_wear.py
