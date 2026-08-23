# Cross-leaf footprint planning

Status: PROPOSED

Written: 2026-08-23

Read `mapgen-nouns.md` before implementing this story.

## Goal

Let one authored building or compound claim a coherent footprint across
adjacent BSP leaves before roads and per-leaf fillers make that claim
impossible. This is one shared planning seam for civic, commercial, industrial,
medical, residential, and dense-city content—not a special case per filler.

## Acceptance

- A planning stage claims an explicit connected set of eligible leaves before
  conflicting fill dispatch.
- Internal road edges can be reserved, suppressed, or replanned without
  corrupting the published road graph or external parcel access.
- The resulting structure owns circulation, entrances, room purposes, and its
  fallback when no valid footprint exists.
- Deterministic validation covers road connectivity, battle-space connectivity,
  spawn deployment, and the unchanged single-leaf fallback.
