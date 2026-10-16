#!/usr/bin/env bash
# Usage: generate-fort-emplacement-corners.sh
# Sends fort-emplacement-corners.prompt.txt, the corners guide (edit target) and the intact patch (material reference only)
# to the Codex CLI built-in image generation; retains the untouched result as fort-emplacement-corners.raw.png.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
{
  echo "Use your built-in image generation tool (not code, not a script) to generate ONE image. The first attached image is the authoritative geometry guide and edit target; the second is a material reference only. The image prompt is everything between the markers."
  echo "=== PROMPT START ==="
  cat "$root/art-source/tilesets/fort-emplacement-corners.prompt.txt"
  echo "=== PROMPT END ==="
  echo "When the image is generated, copy the generated PNG byte-for-byte to art-source/tilesets/fort-emplacement-corners.raw.png. Do not edit, resize or recolour it. Reply with only the final path, its pixel size and whether it has a real alpha channel."
} | codex exec -s workspace-write -C "$root" -i "$root/art-source/templates/fort-emplacement-corners-guide.png" -i "$root/art-source/templates/fort-emplacement-intact-reference.png"
