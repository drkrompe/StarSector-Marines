#!/usr/bin/env bash
# Usage: generate.sh <part> [reference.png ...]
#   part: torso | left-shoulder | right-shoulder | head | foot, or pirate-<part>
# Sends prompts/<part>.prompt.txt plus art-source/templates/boarding-jack-<part>-guide.png (written by
# make_guide.py) to the Codex CLI built-in image generation and retains the untouched result as
# raw/<part>.raw.png. Extra images are material references only (an accepted earlier part).
# export_assets.py is the mechanical exporter.
set -euo pipefail
part="$1"; shift
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../.." && pwd)"
# A pirate-<part> pass edits the soot-painted part (export_assets.py --pirate-bases) rather than a guide.
if [[ "$part" == pirate-* ]]; then
  images=(-i "$here/pirate/${part#pirate-}-base.png")
else
  images=(-i "$root/art-source/templates/boarding-jack-$part-guide.png")
fi
for ref in "$@"; do images+=(-i "$ref"); done
{
  echo "Use your built-in image generation tool (not code, not a script) to generate ONE image. The first attached image is the authoritative geometry guide and edit target. Any further attached image is a material and finish reference only: match its paint, wear and rendering, never its shape. The image prompt is everything between the markers."
  echo "=== PROMPT START ==="
  cat "$here/prompts/$part.prompt.txt"
  echo "=== PROMPT END ==="
  echo "When the image is generated, copy the generated PNG byte-for-byte to art-source/marine-modular-topdown/boarding-jack/raw/$part.raw.png (create the folder if needed). Do not edit, resize or recolour it. Do not stage or commit anything in git. Reply with only the final path, its pixel size and whether it has a real alpha channel."
} | codex exec -s workspace-write -C "$root" "${images[@]}"
