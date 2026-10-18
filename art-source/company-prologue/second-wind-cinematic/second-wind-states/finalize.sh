#!/usr/bin/env bash
# Rebuild, verify and review every state; compose the cross-state sheets.
#   ./finalize.sh [--skip-geom] [--turn N (default 0: stills only)] [--samples S] [states...]
# Raw renders go to ../../second-wind-surface/build/final/<state>/ (ignored);
# committed review images go to <state>/review/ and review/.
set -euo pipefail
cd "$(dirname "$0")"
. ../../../blender.sh
FINAL=$(cd ../../second-wind-surface && pwd)/build/final
SKIP=; TURN=0; SAMPLES=32; STATES=()
while [ $# -gt 0 ]; do case $1 in
  --skip-geom) SKIP=--skip-geom;; --turn) TURN=$2; shift;; --samples) SAMPLES=$2; shift;;
  *) STATES+=("$1");; esac; shift; done
[ ${#STATES[@]} -eq 0 ] && STATES=(derelict patched refit restored)
run() { "$BLENDER" --background --factory-startup --python-exit-code 1 --python "$@"; }
for s in "${STATES[@]}"; do
  ./build_state.sh "$s" $SKIP
  run export_effects.py -- --state "$s" 2>&1 | grep -E "EFFECTS|Error|Traceback"
  run verify_state.py -- --state "$s" 2>&1 | grep -E "VERIFY|Error|Traceback"
  run review_state.py -- --state "$s" --out "$FINAL/$s" --samples "$SAMPLES" --sites \
    --views port-quarter,starboard-quarter,top,stern,bow-quarter,stern-detail,port-flank-detail 2>&1 | grep -E "Error|Traceback" || true
  run review_state.py -- --state "$s" --out "$FINAL/$s" --samples 16 --lights-off --views port-quarter 2>&1 | grep -E "Error|Traceback" || true
  if [ "$TURN" -gt 0 ]; then
    run review_state.py -- --state "$s" --out "$FINAL/$s" --samples "$SAMPLES" --views= --turn "$TURN" 2>&1 | grep -E "REVIEW turn|Error|Traceback" || true
    "$PYTHON" encode_turn.py "$s"
  fi
  "$PYTHON" - "$s" "$FINAL/$s" <<'EOF'
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
s, src = sys.argv[1], Path(sys.argv[2])
out = Path(s) / 'review'; out.mkdir(parents=True, exist_ok=True)
f = ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf', 22)
for name, views in (('views', ['port-quarter', 'starboard-quarter', 'top', 'stern']),
                    ('close', ['bow-quarter', 'port-flank-detail', 'stern-detail', 'port-quarter-dark'])):
    W = Image.new('RGB', (1920, 1080)); d = ImageDraw.Draw(W)
    for k, v in enumerate(views):
        W.paste(Image.open(src / f'{v}.png').convert('RGB').resize((960, 540), Image.LANCZOS), ((k % 2) * 960, (k // 2) * 540))
        d.rectangle(((k % 2) * 960, (k // 2) * 540, (k % 2) * 960 + 960, (k // 2) * 540 + 34), fill=(0, 0, 0))
        d.text(((k % 2) * 960 + 12, (k // 2) * 540 + 5), f'{s}  ·  {v}' + ('  (studio off: ship emissives only)' if v.endswith('-dark') else ''), font=f, fill=(235, 235, 235))
    W.save(out / f'{name}.png')
print('SHEETS', s)
EOF
done
if [ ${#STATES[@]} -eq 4 ]; then "$PYTHON" review_sheet.py --renders "$FINAL"; fi
