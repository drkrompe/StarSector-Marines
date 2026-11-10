#!/usr/bin/env bash
# Every state of one living world's flora (jungle | taiga), as the dry scrub was made
# (art-source/dry-woodland/generate.sh): canopies reskin the shipped forest-canopies guide;
# undergrowth reskins the standing guide with the new canopies as palette reference; the
# damaged, destroyed and trampled states reskin their guides with the intact sheets as identity.
# Each call is one paid generation; an existing raw is never regenerated.
# Usage (from the repo root): bash art-source/living-worlds/generate.sh <direction>
set -euo pipefail
d="$1"
here=art-source/living-worlds
t=art-source/templates
s=art-source/tilesets
raw="$here/raw"
p="$here/prompts"
mkdir -p "$raw"
gen() { [ -f "$raw/$1.raw.png" ] || python tools/codex_imagegen.py --prompt "$p/$1.prompt.txt" --out "$raw/$1.raw.png" "${@:2}"; }

gen "$d-canopies" \
  --ref "$t/forest-canopies.png=Geometry/layout authority: the six crown silhouettes, centers, sizes and gaps; reskin, never move or enlarge" \
  --ref "$s/forest-canopies.raw.png=Material/style only: existing painted temperate canopies; match rendering and light, not species or palette"

gen "$d-undergrowth" \
  --ref "$t/undergrowth-standing.png=Geometry/layout authority: the six undergrowth silhouettes and their gaps; reskin, never move" \
  --ref "$s/forest-undergrowth.raw.png=Material/style only: existing painted forest undergrowth; match rendering and light, not plants or palette" \
  --ref "$raw/$d-canopies.raw.png=Palette reference only: the trees this growth stands beside" &
gen "$d-damaged" \
  --ref "$t/tree-damaged.png=Geometry/layout authority: broken crowns, branch spokes, stump centers; reskin, never move" \
  --ref "$raw/$d-canopies.raw.png=Identity/material reference only: the intact trees, slot for slot" &
wait

gen "$d-destroyed" \
  --ref "$t/tree-destroyed.png=Geometry/layout authority: stump centers, fallen limbs, gaps and envelope; reskin, never move or enlarge" \
  --ref "$raw/$d-canopies.raw.png=Species/material identity only, slot for slot; not layout" \
  --ref "$raw/$d-damaged.raw.png=Bark and broken-wood material only; not layout" &
gen "$d-undergrowth-damaged" \
  --ref "$t/undergrowth-damaged.png=Geometry/layout authority: envelopes, centers, sizes and holes" \
  --ref "$raw/$d-undergrowth.raw.png=Species, material and palette identity only; not layout" &
gen "$d-undergrowth-trampled" \
  --ref "$t/undergrowth-trampled.png=Geometry/layout authority: where fragments lie, sparse coverage, open ground" \
  --ref "$raw/$d-undergrowth.raw.png=Species identity only (leaf shapes); not layout, not palette, not brightness" \
  --ref "$s/forest-undergrowth-destroyed.raw.png=Sparseness and quietness reference only; not palette, not layout" &
wait
