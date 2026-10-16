#!/usr/bin/env bash
# Usage: generate.sh <part>
#   part: interior-frames | interior-core | torn-edges
# Sends prompts/<part>.prompt.txt plus art-source/templates/hull-<part>-guide.png (written by
# make_guide.py) to the Codex CLI built-in image generation, with a vanilla hull sprite as the
# material reference, and retains the untouched result as raw/<part>.raw.png.
# export_assets.py is the mechanical exporter into mod/graphics/warship-damage/.
set -euo pipefail
part="$1"; shift
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
guide="$root/art-source/templates/hull-$part-guide.png"
images=(-i "$guide")
for ref in "$@"; do images+=(-i "$ref"); done
{
  echo "Use your built-in image generation tool (not code, not a script) to generate ONE image. The first attached image is the authoritative geometry guide and edit target. Any further attached image is a material and finish reference only: match its paint, wear and rendering, never its shape. The image prompt is everything between the markers."
  echo "=== PROMPT START ==="
  cat "$here/prompts/$part.prompt.txt"
  echo "=== PROMPT END ==="
  echo "When the image is generated, copy the generated PNG byte-for-byte to art-source/warship-hull-damage/raw/$part.raw.png (create the folder if needed). Do not edit, resize or recolour it. Do not stage or commit anything in git. Reply with only the final path, its pixel size and whether it has a real alpha channel."
} | codex exec -s workspace-write -C "$root" "${images[@]}"
