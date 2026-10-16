#!/usr/bin/env bash
# Usage: generate.sh <ship>   (venture | hound-pirates)
# Sends prompts/<ship>.prompt.txt plus art-source/templates/<ship>-vanilla-guide-8x.png (the installed vanilla
# sprite enlarged 8x with Lanczos, as the edit target and geometry guide) to the Codex CLI built-in image
# generation and retains the untouched result as raw/<ship>.raw.png. pack.py is the mechanical exporter.
set -euo pipefail
ship="$1"; shift
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
{
  echo "Use your built-in image generation tool (not code, not a script) to generate ONE image. The attached image is the authoritative geometry guide and edit target. The image prompt is everything between the markers."
  echo "=== PROMPT START ==="
  cat "$here/prompts/$ship.prompt.txt"
  echo "=== PROMPT END ==="
  echo "When the image is generated, copy the generated PNG byte-for-byte to art-source/ships/raw/$ship.raw.png (create the folder if needed). Do not edit, resize or recolour it. Do not stage or commit anything in git. Reply with only the final path, its pixel size and whether it has a real alpha channel."
} | codex exec -s workspace-write -C "$root" -i "$root/art-source/templates/$ship-vanilla-guide-8x.png"
