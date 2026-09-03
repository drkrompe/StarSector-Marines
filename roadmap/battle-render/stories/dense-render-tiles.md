# Dense Render Tiles

Status: PARKED — contract only after a measured scale trigger.

Written: 2026-08-23

Updated: 2026-09-02 — static ground is out of scope: it was measured against
this answer and is a resident vertex mesh instead (`battle-render-nouns.md`, law
20). What is left here is decal residency.

Read `battle-render-nouns.md` before reviving this story.

## Current substrate

Persistent decals use one world-sized FBO, whose resolution already steps down
against a pixel budget and the driver's maximum texture size. `renderEvidence`
prices the whole DECALS layer at a hundredth of a millisecond a frame — one
blit — so nothing about the current canonical map justifies replacing it.

Static ground is no longer this story's business. It was the case that made the
scale argument, and when the profile finally arrived it named a different
answer: base terrain is one quad per cell and cells do not overlap, so it lives
in vertex buffers keyed by sheet and is patched per changed cell. A tile costs
fill and VRAM per view and needs residency, eviction and anti-thrash policy; the
mesh is one upload and no per-frame CPU at all.

## Goal

Add a view-resident render-tile layer that can retain persistent decals without
changing the simulation's cell grid.

## Decisions to settle

- Choose the GPU tile size and per-host pixel density from measurements.
- Choose one FBO per tile versus a resident atlas.
- Define camera margin, eviction, and anti-thrash policy.
- Define the cell-change invalidation API, including autotile edge halos.
- Keep decal backing separate from the resident ground mesh, which owns its own
  addressing and invalidation.

## Acceptance

- Visible clean tiles draw as bounded blits instead of per-cell ground commands.
- Wall, rubble, roof, fixture, and relevant neighbor changes dirty exactly the
  required tiles.
- Evicted ground rebuilds from cells; evicted decals replay retained sources.
- Resident GPU memory is bounded by the view rather than total map area.
- A failed or unavailable tile target falls back locally to the present cell
  renderer with unchanged paint order.
- Standalone and combat-bridge hosts consume the same residency mechanism with
  host-appropriate projection and pixel density.

## Out of scope

- Replacing navigation, fog, occupancy, LoS, wall health, or saves with tiles.
- Pathfinding hierarchy.
- Camera-Z or perspective.
- Sparse entity indexing.
