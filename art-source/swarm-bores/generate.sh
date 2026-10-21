#!/usr/bin/env bash
# Usage: art-source/swarm-bores/generate.sh <part> <variant> [extra-flags...]
#   part: bore | mother-bore | spore-colony-fissure | spore-colony-bore | spore-colony-maw
#   variant: 1..N (make_guide.VARIANTS)
# One paid ImageGen call through tools/codex_imagegen.py: the guide written by make_guide.py
# is the geometry and lighting authority and edit target, the Runner's retained body source is
# material reference only. Writes raw/<part>-<variant>.raw.png and its provenance; export.py
# cuts it mechanically.
set -euo pipefail
part="$1"; variant="$2"; shift 2
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
cd "$root"
python tools/codex_imagegen.py --prompt "art-source/swarm-bores/prompts/$part.prompt.txt" \
  --out "art-source/swarm-bores/raw/$part-$variant.raw.png" \
  --ref "art-source/templates/swarm-$part-$variant-guide.png=Geometry and lighting guide and edit target: exact silhouette, placement, scale and light-and-shadow authority; reskin it, never move, reshape, re-light or symmetrise it" \
  --ref "art-source/alien-modular-topdown/sources/alien-body-source.png=Material and style reference only (the existing swarm runner's body); match its chitin, colours and rendering, never its shape" \
  "$@"
