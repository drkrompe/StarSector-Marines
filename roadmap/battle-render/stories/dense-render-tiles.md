# Dense Render Tiles

Status: PARKED — contract only after a measured scale trigger.

Written: 2026-08-23

Updated: 2026-08-24 — the durable residency boundary now lives entirely in
`battle-render-nouns.md`.

Read `battle-render-nouns.md` before reviving this story.

## Current substrate

Dense terrain already range-loops the camera's visible cell rectangle. It still
emits one command per visible cell, while persistent decals use one world-sized
FBO. The current canonical map does not justify replacing those paths yet.

## Goal

Add a view-resident render-tile layer that can bake static ground and retain
persistent decals without changing the simulation's cell grid.

## Decisions to settle

- Choose the GPU tile size and per-host pixel density from measurements.
- Choose one FBO per tile versus a resident atlas.
- Define camera margin, eviction, and anti-thrash policy.
- Define the cell-change invalidation API, including autotile edge halos.
- Keep ground and decal backing separate while sharing tile addressing and
  residency.

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
