#!/usr/bin/env bash
# Usage: generate-fort-emplacement-state.sh <damaged|destroyed>
# Edits the accepted intact collar plate (fort-emplacement.raw.png) into the damaged or destroyed state with the Codex CLI
# built-in image generation, so the layout and every cell rect carry over unchanged, and retains the untouched result
# as fort-emplacement-<state>.raw.png. The prompt is fort-emplacement-<state>.prompt.txt.
set -euo pipefail
state="$1"
root="$(cd "$(dirname "$0")/../.." && pwd)"
{
  echo "Use your built-in image generation tool (not code, not a script) to generate ONE image. The attached image is the authoritative edit target. The image prompt is everything between the markers."
  echo "=== PROMPT START ==="
  cat "$root/art-source/tilesets/fort-emplacement-$state.prompt.txt"
  echo "=== PROMPT END ==="
  echo "When the image is generated, copy the generated PNG byte-for-byte to art-source/tilesets/fort-emplacement-$state.raw.png. Do not edit, resize or recolour it. Reply with only the final path, its pixel size and whether it has a real alpha channel."
} | codex exec -s workspace-write -C "$root" -i "$root/art-source/tilesets/fort-emplacement.raw.png"
