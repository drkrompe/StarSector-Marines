#!/usr/bin/env bash
# Usage: generate.sh <asset>   e.g. pd-pack-pack   (pd-pack-pack | pd-pack-icon)
# Sends art-source/pd-pack/<asset>.prompt.txt plus the matching guide in
# art-source/templates/<asset>-guide.png to the Codex CLI built-in image
# generation and retains the untouched result as raw/<asset>.raw.png.
# Guides come from art-source/templates/pd-pack-guide.py; export.py is the mechanical exporter.
set -euo pipefail
asset="$1"
root="$(cd "$(dirname "$0")/../.." && pwd)"
{
  echo "Use your built-in image generation tool (not code, not a script) to generate ONE image. The attached image is the authoritative geometry guide and edit target. The image prompt is everything between the markers."
  echo "=== PROMPT START ==="
  cat "$root/art-source/pd-pack/$asset.prompt.txt"
  echo "=== PROMPT END ==="
  echo "When the image is generated, copy the generated PNG byte-for-byte to art-source/pd-pack/raw/$asset.raw.png (create the folder if needed). Do not edit, resize or recolour it. Reply with only the final path, its pixel size and whether it has a real alpha channel."
} | codex exec -s workspace-write -C "$root" -i "$root/art-source/templates/$asset-guide.png"
