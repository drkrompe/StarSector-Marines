#!/usr/bin/env bash
# Replay the whole spike except the Codex generations (generated/ is retained).
# Needs Blender 5.2 and Python with numpy + Pillow. Bulky output goes to build/.
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SPIKE|Error|Traceback" || true; }

# 1. Clean model into build/geom (the geometry modules are imported, never edited).
blend ../second-wind-geometry/build_reference.py -- --out build/geom --views port-side --samples 8
# 2. Guides, masks, depth maps.
for v in port-side quarter30 part-deck part-turret part-pod part-bow; do blend render_guide.py -- --view $v; done
"$PYTHON" parts_sheet.py compose
# 3. Drift and registration.
"$PYTHON" measure_drift.py generated/reskin-v1.png --view port-side
"$PYTHON" measure_drift.py generated/reskin-quarter30.png --view quarter30
"$PYTHON" parts_sheet.py split generated/parts-sheet.png
for p in part-deck part-turret part-pod part-bow; do "$PYTHON" measure_drift.py build/parts/$p.png --view $p; done
# 4. Projection: one view, two views, the four parts alone, and all six.
P0="--proj port-side:generated/reskin-v1.png:registration/reskin-v1.json"
P1="--proj quarter30:generated/reskin-quarter30.png:registration/reskin-quarter30.json"
PP=""; for p in part-deck part-turret part-pod part-bow; do PP="$PP --proj $p:build/parts/$p.png:registration/$p.json"; done
blend project.py -- --tag one-view $P0
blend project.py -- --tag two-view $P0 $P1
blend project.py -- --tag parts $PP
blend project.py -- --tag all $P0 $P1 $PP
# 5. Swatch seam check and review sheets.
"$PYTHON" swatch_check.py generated/swatches.png
"$PYTHON" compose_review.py
