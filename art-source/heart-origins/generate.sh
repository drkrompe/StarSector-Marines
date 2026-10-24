#!/usr/bin/env bash
# Usage: art-source/heart-origins/generate.sh <kit> [extra-flags...]
#   kit: a key of make_guide.KITS (workings-shaft, workings-yard, freight-split, freight-boxes, freight-crane,
#        lab-annex, lab-kit, park-bandstand, park-play)
# One paid ImageGen call through tools/codex_imagegen.py. The brief is prompts/common.txt followed by
# prompts/<kit>.txt (assembled under build/, the exact text sent is in the raw's provenance). The kit's guide is the
# geometry authority and edit target; the parked-vehicles atlas is prop material reference and the first mother
# bore's raw is the growth's material reference. Writes art-source/tilesets/origin-<kit>.raw.png; export.py cuts it mechanically.
set -euo pipefail
kit="$1"; shift
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
cd "$root"
brief="build/heart-origins/prompts/$kit.prompt.txt"
mkdir -p "$(dirname "$brief")"
{ cat art-source/heart-origins/prompts/common.txt; echo; cat "art-source/heart-origins/prompts/$kit.txt"; } > "$brief"
python tools/codex_imagegen.py --prompt "$brief" \
  --out "art-source/tilesets/origin-$kit.raw.png" \
  --ref "art-source/templates/heart-$kit-guide.png=Geometry guide and edit target: exact slots, silhouettes, footprints, growth extent and transparent gaps; reskin it, never move, resize or drop a sprite" \
  --ref "art-source/tilesets/parked-vehicles.raw.png=Material and style reference only for painted overhead props; not layout" \
  --ref "art-source/swarm-bores/raw/bore-1.raw.png=Material reference for the alien growth only (chitin, sinew, ivory, amber); never its shape or layout" \
  "$@"
