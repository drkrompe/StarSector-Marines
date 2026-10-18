#!/usr/bin/env bash
# Export the Hound (A) from its skinned build, verify the append, render the review.
# Needs hound-surface/build/skinned.blend (hound-surface/rebuild.sh), Blender 5.2,
# Python with Pillow, ffmpeg.
set -euo pipefail
cd "$(dirname "$0")"
. ../../blender.sh
blend() { "$BLENDER" --background --factory-startup --python "$@" 2>&1 | grep -E "EXPORT|VERIFY|REVIEW|Error|Traceback" || true; }
blend export_hound.py
"$BLENDER" --background --factory-startup --python verify_hound.py 2>&1 | grep -E "VERIFY|Error|Traceback"
blend review_hound.py -- --samples 32
blend review_hound.py -- --effects --samples 32 --views port-quarter,stern-quarter --turn 72
"$PYTHON" encode_turn.py
