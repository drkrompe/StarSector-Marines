#!/usr/bin/env bash
# Usage: art-source/swarm-bores/generate.sh <part> <variant> [state] [extra-flags...]
#   part: bore | mother-bore | spore-colony-fissure | spore-colony-bore | spore-colony-maw | ground-colony
#         | brood-spire | nest-clutch | nest-pods | nest-husk (nest clutter: intact only)
#   variant: 1..N (make_guide.VARIANTS)
#   state: intact (default) | wounded | remains (make_guide.STATES)
# One paid ImageGen call through tools/codex_imagegen.py: the guide written by make_guide.py
# is the geometry and lighting authority and edit target, the Runner's retained body source is
# material reference only. A damage state's brief is the part's prompt followed by the state's
# (prompts/state-<state>.prompt.txt), and the variant's intact raw rides along as its identity
# reference, so the damaged painting is the same structure. Writes raw/<part>-<variant>[-<state>].raw.png
# and its provenance; export.py cuts it mechanically.
set -euo pipefail
part="$1"; variant="$2"; shift 2
state="intact"
if [ $# -gt 0 ] && [[ "$1" != -* ]]; then state="$1"; shift; fi
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
cd "$root"
if [ "$state" = "intact" ]; then
  python tools/codex_imagegen.py --prompt "art-source/swarm-bores/prompts/$part.prompt.txt" \
    --out "art-source/swarm-bores/raw/$part-$variant.raw.png" \
    --ref "art-source/templates/swarm-$part-$variant-guide.png=Geometry and lighting guide and edit target: exact silhouette, placement, scale and light-and-shadow authority; reskin it, never move, reshape, re-light or symmetrise it" \
    --ref "art-source/alien-modular-topdown/sources/alien-body-source.png=Material and style reference only (the existing swarm runner's body); match its chitin, colours and rendering, never its shape" \
    "$@"
else
  brief="build/swarm-bores/prompts/$part-$state.prompt.txt"
  mkdir -p "$(dirname "$brief")"
  { cat "art-source/swarm-bores/prompts/$part.prompt.txt"; echo; cat "art-source/swarm-bores/prompts/state-$state.prompt.txt"; } > "$brief"
  python tools/codex_imagegen.py --prompt "$brief" \
    --out "art-source/swarm-bores/raw/$part-$variant-$state.raw.png" \
    --ref "art-source/templates/swarm-$part-$variant-$state-guide.png=Geometry and lighting guide and edit target for this damage state: exact silhouette, placement, scale and light-and-shadow authority, including every torn socket, gash, burst sac and snapped spine it shows; reskin it, never move, reshape, re-light or symmetrise it" \
    --ref "art-source/swarm-bores/raw/$part-$variant.raw.png=Identity reference: the same structure undamaged, as already painted; keep its painting, colours and rendering, never its undamaged shape where the guide shows damage" \
    --ref "art-source/alien-modular-topdown/sources/alien-body-source.png=Material and style reference only (the existing swarm runner's body); match its chitin, colours and rendering, never its shape" \
    "$@"
fi
