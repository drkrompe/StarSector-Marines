#!/usr/bin/env bash
# Usage: art-source/swarm-bores/generate.sh <part> [extra-flags...]
#   part: bore | mother-bore | spore-colony-fissure | spore-colony-bore | spore-colony-maw
# One paid ImageGen call through tools/codex_imagegen.py: the guide written by make_guide.py
# is the geometry authority and edit target, the Runner's retained body source is material
# reference only. Writes raw/<part>.raw.png and its provenance; export.py cuts it mechanically.
set -euo pipefail
part="$1"; shift
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
cd "$root"
python tools/codex_imagegen.py --prompt "art-source/swarm-bores/prompts/$part.prompt.txt" \
  --out "art-source/swarm-bores/raw/$part.raw.png" \
  --ref "art-source/templates/swarm-$part-guide.png=Geometry guide and edit target: exact silhouette, placement and scale authority; reskin it, never move or reshape it" \
  --ref "art-source/alien-modular-topdown/sources/alien-body-source.png=Material and style reference only (the existing swarm runner's body); match its chitin, colours and rendering, never its shape" \
  "$@"
