#!/usr/bin/env bash
# Usage: generate-fort-emplacement.sh
# Sends fort-emplacement.prompt.txt, the one-plate collar guide (edit target) and the fort's own material crop
# (reference only) to the Codex CLI built-in image generation; retains the untouched result as fort-emplacement.raw.png.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
{
  echo "Use your built-in image generation tool (not code, not a script) to generate ONE image. The first attached image is the authoritative geometry guide and edit target; the second is a material and lighting reference only. The image prompt is everything between the markers."
  echo "=== PROMPT START ==="
  cat "$root/art-source/tilesets/fort-emplacement.prompt.txt"
  echo "=== PROMPT END ==="
  echo "When the image is generated, copy the generated PNG byte-for-byte to art-source/tilesets/fort-emplacement.raw.png. Do not edit, resize or recolour it. Reply with only the final path, its pixel size and whether it has a real alpha channel."
} | codex exec -s workspace-write -C "$root" -i "$root/art-source/templates/fort-emplacement-guide.png" -i "$root/art-source/templates/fort-material-reference.png"