#!/usr/bin/env bash
# Candidate dry-woodland generations for one direction (acacia | palm | scrub).
# Canopies reskin the shipped forest-canopies guide; undergrowth reskins the
# undergrowth-standing guide with the new canopies as palette reference; the
# damaged state reskins the tree-damaged guide with the new canopies as identity.
# Usage (from the repo root): bash art-source/dry-woodland/generate.sh <direction>
set -euo pipefail
d="$1"
here=art-source/dry-woodland
t=art-source/templates
s=art-source/tilesets
raw="$here/raw"
mkdir -p "$raw"

if [ ! -f "$raw/$d-canopies.raw.png" ]; then
python tools/codex_imagegen.py --prompt "$here/prompts/$d-canopies.prompt.txt" --out "$raw/$d-canopies.raw.png" \
  --ref "$t/forest-canopies.png=Geometry/layout authority: the six crown silhouettes, centers, sizes and gaps; reskin, never move" \
  --ref "$s/forest-canopies.raw.png=Material/style only: existing painted temperate canopies; match rendering and light, not species or palette"
fi

if [ ! -f "$raw/$d-undergrowth.raw.png" ]; then
python tools/codex_imagegen.py --prompt "$here/prompts/$d-undergrowth.prompt.txt" --out "$raw/$d-undergrowth.raw.png" \
  --ref "$t/undergrowth-standing.png=Geometry/layout authority: the six undergrowth silhouettes and their gaps; reskin, never move" \
  --ref "$s/forest-undergrowth.raw.png=Material/style only: existing painted forest undergrowth; match rendering and light, not plants or palette" \
  --ref "$raw/$d-canopies.raw.png=Palette reference only: the dry trees this scrub grows beside"
fi

if [ ! -f "$raw/$d-damaged.raw.png" ]; then
python tools/codex_imagegen.py --prompt "$here/prompts/$d-damaged.prompt.txt" --out "$raw/$d-damaged.raw.png" \
  --ref "$t/tree-damaged.png=Geometry/layout authority: broken crowns, branch spokes, stump centers; reskin, never move" \
  --ref "$raw/$d-canopies.raw.png=Identity/material reference only: the intact dry trees, slot for slot"
fi

# v2: the first canopies came back ~15% larger than the guide and fully closed. The
# sparse guide (make_sparse_guide.py) owns the sand gaps and branch skeleton; v1 is
# the species/palette identity reference.
if [ ! -f "$raw/$d-canopies-v2.raw.png" ]; then
python tools/codex_imagegen.py --prompt "$here/prompts/$d-canopies-v2.prompt.txt" --out "$raw/$d-canopies-v2.raw.png" \
  --ref "$here/guides/forest-canopies-sparse.png=Geometry/layout authority: crown outlines, internal transparent holes and brown branch skeleton; reskin, never move or enlarge" \
  --ref "$s/forest-canopies.raw.png=Material/style only: existing painted temperate canopies; match rendering and light, not species or palette" \
  --ref "$raw/$d-canopies.raw.png=Species and palette identity only, slot for slot; not its size or its closed crowns"
fi

# Shipped states (scrub direction): destroyed crowns and the undergrowth's damaged and trampled
# states, as the green set was made (forests README, "Combat damage art" and "Undergrowth").
if [ ! -f "$raw/$d-destroyed.raw.png" ]; then
python tools/codex_imagegen.py --prompt "$here/prompts/$d-destroyed.prompt.txt" --out "$raw/$d-destroyed.raw.png" \
  --ref "$t/tree-destroyed.png=Geometry/layout authority: stump centers, fallen limbs, gaps and envelope; reskin, never move or enlarge" \
  --ref "$raw/$d-canopies-v2.raw.png=Species/material identity only, slot for slot; not layout" \
  --ref "$raw/$d-damaged.raw.png=Bark and broken-wood material only; not layout" &
fi
if [ ! -f "$raw/$d-undergrowth-damaged.raw.png" ]; then
python tools/codex_imagegen.py --prompt "$here/prompts/$d-undergrowth-damaged.prompt.txt" --out "$raw/$d-undergrowth-damaged.raw.png" \
  --ref "$t/undergrowth-damaged.png=Geometry/layout authority: envelopes, centers, sizes and holes" \
  --ref "$raw/$d-undergrowth.raw.png=Species, material and palette identity only; not layout" &
fi
if [ ! -f "$raw/$d-undergrowth-trampled.raw.png" ]; then
python tools/codex_imagegen.py --prompt "$here/prompts/$d-undergrowth-trampled.prompt.txt" --out "$raw/$d-undergrowth-trampled.raw.png" \
  --ref "$t/undergrowth-trampled.png=Geometry/layout authority: where fragments lie, sparse coverage, open ground" \
  --ref "$raw/$d-undergrowth.raw.png=Species identity only (leaf shapes); not layout, not palette, not brightness" \
  --ref "$s/forest-undergrowth-destroyed.raw.png=Sparseness and quietness reference only; not palette, not layout" &
fi
wait
