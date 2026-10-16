#!/usr/bin/env bash
# Replay the whole spike except the Codex generations (generated/ is retained).
# Needs Blender 5.2 and Python with numpy + Pillow. Bulky output goes to build/.
set -euo pipefail
cd "$(dirname "$0")"
BLENDER=${BLENDER:-"/c/Program Files/Blender Foundation/Blender 5.2/blender.exe"}
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "SPIKE|Error|Traceback" || true; }

# 1. Clean model into build/geom (the geometry modules are imported, never edited).
blend ../second-wind-geometry/build_reference.py -- --out build/geom --views port-side --samples 8
# 2. Guides, masks, depth maps.
for v in port-side quarter30; do blend render_guide.py -- --view $v; done
# 3. Drift and registration.
python measure_drift.py generated/reskin-v1.png --view port-side
python measure_drift.py generated/reskin-quarter30.png --view quarter30
# 4. Projection: one view, then two views blended.
blend project.py -- --tag one-view --proj port-side:generated/reskin-v1.png:registration/reskin-v1.json
blend project.py -- --tag two-view --proj port-side:generated/reskin-v1.png:registration/reskin-v1.json \
  --proj quarter30:generated/reskin-quarter30.png:registration/reskin-quarter30.json
# 5. Swatch seam check and review sheets.
python swatch_check.py generated/swatches.png
python compose_review.py
