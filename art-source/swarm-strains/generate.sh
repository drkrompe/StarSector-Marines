#!/usr/bin/env bash
# Usage: art-source/swarm-strains/generate.sh <part> [extra-flags...]
#   part: brute-body | brute-head | brute-foot | brute-fore-claw | spitter-body | spitter-head
# One paid ImageGen call through tools/codex_imagegen.py: the guide written by make_guide.py
# is the geometry authority and edit target, the Runner's matching retained source is material
# reference only. Writes raw/<part>.raw.png and its provenance; export.py cuts it mechanically.
set -euo pipefail
part="$1"; shift
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
case "$part" in
  *-body) ref=alien-body-source.png ;;
  *-head) ref=alien-head-source.png ;;
  *-foot) ref=alien-foot-source.png ;;
  *-fore-claw) ref=alien-fore-claw-source.png ;;
  *) echo "unknown part $part" >&2; exit 2 ;;
esac
cd "$root"
python tools/codex_imagegen.py --prompt "art-source/swarm-strains/prompts/$part.prompt.txt" \
  --out "art-source/swarm-strains/raw/$part.raw.png" \
  --ref "art-source/templates/swarm-$part-guide.png=Geometry guide and edit target: exact silhouette, placement and scale authority; reskin it, never move or reshape it" \
  --ref "art-source/alien-modular-topdown/sources/$ref=Material and style reference only (the existing swarm runner's matching part); match its chitin, colours and rendering, never its shape" \
  "$@"
