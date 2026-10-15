# Cross-leaf footprint planning

Status: IN PROGRESS — bounded industrial template parcels are supported; general compound programs remain open.

Written: 2026-08-23

Updated: 2026-10-15 — industrial buildings and working yards have a bounded prior
claim; broader cross-family programs and nonrectangular compound footprints remain.

Read `compound-programs.md` and `mapgen-nouns.md` before implementing this
story.

## Goal

Let one authored building or compound claim a coherent footprint across
adjacent BSP leaves before roads and per-leaf fillers make that claim
impossible. This is one shared planning seam for civic, commercial, industrial,
medical, residential, and dense-city content—not a special case per filler.

## Acceptance

The industrial case is defined in `mapgen-nouns.md`: whole industrial leaves,
internal minor streets, protected trunk roads and a complete exterior street ring.
Remaining work is a shared program-driven claim for the other families and whole
compound layouts, including nonrectangular ownership and declared vehicle gates.

- A planning stage claims an explicit connected set of eligible leaves before
  conflicting fill dispatch.
- Internal road edges can be reserved, suppressed, or replanned without
  corrupting the published road graph or external parcel access.
- The resulting structure owns circulation, entrances, room purposes, and its
  fallback when no valid footprint exists.
- Deterministic validation covers road connectivity, battle-space connectivity,
  spawn deployment, and the unchanged single-leaf fallback.
