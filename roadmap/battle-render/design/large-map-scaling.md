# Large-Map Rendering Direction

Status: ACTIVE — current camera culling is shipped; view-resident GPU tiles remain a scale-triggered direction.

Written: 2026-08-23

## Direction

Map size is not capped merely because one presentation resource scales with the
whole world. The simulation's dense cell grid remains authoritative, while the
renderer should make expensive GPU residency proportional to the visible view.

Current camera culling already bounds dense command collection to a visible
cell rectangle. The remaining scale pressure is static ground being re-emitted
each frame and the standalone decal accumulator retaining one world-sized FBO.
Those are rendering representations, not reasons to replace cell-addressed
navigation, fog, walls, occupancy, or saves.

## Chosen extension

`dense-render-tiles.md` is the single future vehicle for both static-ground
baking and tiled decal residency. A tile is a view over a fixed block of cells
with an AABB, dirty state, and optional GPU backing. The camera admits tiles
with a margin; eviction drops only derived presentation state. Re-entry rebuilds
ground from cells and replays retained decal sources.

This makes resident memory a function of view area rather than total map area.
Ground and decals may use separate backing targets, but they share tile address,
visibility, invalidation, and eviction policy. Failure remains local: a tile
whose target cannot be created falls back to current per-cell drawing.

## Trigger for implementation

Do not contract the tile story from map-size ambition alone. Pick it up when
profiling shows ground collection/flush is again a material frame cost, when a
larger canonical map would make the world-sized decal target unsafe, or when
the bridge must project persistent decals.

## Boundaries

Pathfinding hierarchy and zone connectivity are simulation/navigation work.
Camera-Z or perspective is a separate projection decision. The combat bridge's
DECALS retarget remains tracked by `s3j-fx-fbo-retarget.md`; it may consume the
same residency mechanism without making the bridge own it.
