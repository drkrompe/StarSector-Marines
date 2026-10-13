#!/usr/bin/env bash
# Usage: generate-fort-earthworks.sh
# Sends art-source/tilesets/fort-earthworks.prompt.txt plus art-source/templates/fort-earthworks-guide.png to the Codex
# CLI built-in image generation and retains the untouched result as fort-earthworks.raw.png beside it.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
{
  echo "Use your built-in image generation tool (not code, not a script) to generate ONE image. The attached image is the authoritative geometry guide and edit target. The image prompt is everything between the markers."
  echo "=== PROMPT START ==="
  cat "$root/art-source/tilesets/fort-earthworks.prompt.txt"
  echo "=== PROMPT END ==="
  echo "When the image is generated, copy the generated PNG byte-for-byte to art-source/tilesets/fort-earthworks.raw.png. Do not edit, resize or recolour it. Reply with only the final path, its pixel size and whether it has a real alpha channel."
} | codex exec -s workspace-write -C "$root" -i "$root/art-source/templates/fort-earthworks-guide.png"
