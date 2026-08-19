# Dense render tiles — n×m cell chunks as residency, not a new sim grid

> **Status: design-stage / not queued.** Captured so the camera-cull follow-on
> and the large-map FBO plan have one place to point. Do not pick this up until
> it is explicitly contracted.

## Premise

The world is a dense cell grid. GROUND (and height/normal) still emit one
command per visible cell every frame. Camera view culling
([`../complete/camera-view-cull.md`](../complete/camera-view-cull.md)) already
range-loops `BattleCamera.visibleCells`, so a zoomed fight only walks the
slice. That does not change the fact that a visible slice is **static
geometry re-emitted every frame**, and at zoom 1.0 — or on the combat-bridge
plate, whose `BattleCamera` viewport *is* the whole grid — the slice is the
entire map.

| Map | Cells | 16-cell tiles | 64-cell tiles |
|-----|------:|-------------:|-------------:|
| SMALL 112×64 | 7,168 | 28 | 4 |
| MEDIUM 144×80 | 11,520 | 45 | 6 |
| LARGE 240×160 | 38,400 | 150 | 10 |
| Bridge 480×320 | 153,600 | **600** | 40 |

Six hundred tile AABBs vs the camera is a cheap broadphase. The architecture
question is what a tile *is*.

## What a dense tile is

A **dense tile** is an `n×m` block of cells with its own AABB. It always
contains `n×m` cells — this is not a sparse occupancy list. Camera (or a
quantized `VisibleCellRect`) decides whether the tile is in view; missed
tiles are not visited.

That is a different object from:

- **`visibleCells` (shipped)** — tight cell AABB. Best CPU cull for a
  grid-aligned ortho camera. Still one quad per cell inside the rect.
- **Sparse 16-cell buckets** (`UnitSpatialIndex`, doodads-if-we-ever-index
  them) — each bucket holds a *list* of occupants. Right for units/shots.
  Wrong for ground: every cell paints.
- **One world-sized ground FBO**
  ([`perf-ground-fbo-cache.md`](perf-ground-fbo-cache.md)) — GROUND → 1 quad,
  but VRAM is O(map area). The map-size wall in
  [`../large-map-scaling.md`](../large-map-scaling.md).

A dense tile only beats the cell rect if **a hit tile is cheap to draw**.
Unbaked, a 16×16 hit is 256 cell emits plus a ring of off-screen cells —
strictly looser than `visibleCells`. Baked, a hit tile is one (or a few)
blits. That is the load-bearing choice.

```
tile hits camera  →  dirty? rebake from cells  →  blit resident FBO/atlas
tile misses       →  do not iterate its cells; LRU/free the GPU backing
```

Zoom 8 on LARGE: ~4–9 blits instead of ~600 cell quads. Zoom 1: 150 blits
instead of 38k. Bridge spectator: test tile AABBs in world units against the
vanilla viewport, so the 480×320 plate stops emitting 153k ground quads.

## Keep cells as sim truth

Do **not** replace `NavigationGrid`, `CellTopology`, fog, occupancy, A*, or
wall HP with tiles as the address space. Those are ~50 B/cell (LARGE ~2 MB,
bridge ~8 MB). Path, LoS, autotile neighbors, building interiors, and
destructible walls are cell-indexed and should stay that way.

A tile is a **view of `n×m` cells**, not a new coordinate system. Sim
writes a cell (wall break, rubble, roof cave-in); the owning tile (and an
edge neighbor if autotile halo requires it) goes dirty.

## `n×m` sizing

Two sizes are allowed; do not retile the sim to match either.

- **16** — same as `UnitSpatialIndex.BUCKET`. ~600 tiles on the bridge.
  Good CPU occupancy board. Unbaked 16×16 is still the wrong GROUND path.
- **32 or 64** — GPU residency. [`../large-map-scaling.md`](../large-map-scaling.md)
  already wanted 64×64 for view-resident decal FBOs. Bridge ~40 tiles; a
  zoomed fight is 1–4 blits. Unbaked 64×64 is 4096 cell visits per hit —
  64 only makes sense baked.

## What this folds

This story is the bake vehicle for two already-written plans. Do not
implement those as a single world-sized surface.

1. **Ground FBO cache** ([`perf-ground-fbo-cache.md`](perf-ground-fbo-cache.md))
   — same “stop re-emitting static ground” goal; per-tile instead of one
   map-sized FBO, so memory is O(view) and a wall break dirties one chunk.
2. **Tiled decal FBO** ([`../large-map-scaling.md`](../large-map-scaling.md) §1)
   — same tile grid, same residency/LRU, same spectator cull. Ground and
   decals should share the board even if they keep separate FBO backing.
3. **Bridge spectator cull** — `GroundSceneBackdrop`’s `BattleCamera` covers
   the whole plate, so `visibleCells` is a no-op there. Tile AABBs in world
   units (or a spectator-derived cell rect) are the consumer that actually
   cuts 153k.

Not folded: the MoonLight linked-list rewrite in
[`../../ecs-migration/spatial-index-options.md`](../../ecs-migration/spatial-index-options.md).
That is a gather-hot-path change for *units* at N ≳ 500. Dense ground tiles
are a different structure on the same 16-cell pitch.

## Invalidation (the hard part)

Dirties a tile:

- Map load / generator stamp (all tiles dirty once).
- Wall break, rubble, roof cave-in, fixture change in the tile.
- Autotile neighbor change on a shared edge → dirty both tiles (1-cell halo
  at bake time, same as today’s N/S/E/W reads).

Does **not** dirty a tile:

- Camera pan/zoom (blit under the current transform).
- Fog (separate overlay; already view-culled).
- Units, shots, doodads (not in the ground bake).
- Decals, if they stay a sibling FBO on the same tile grid rather than
  being composited into the ground bake.

Evict-then-revisit: cells remain source of truth, so dropping a tile FBO is
free; re-entry rebakes from cells (and replays that tile’s decals from the
capped source list, same as the large-map plan).

## Draw-list fit

Baked tiles are a genuine own-GL blit — `Custom` (or a dedicated command)
like `DecalAccumulator` today. Per-frame GROUND cell emits go away for
resident tiles. Paint order stays `GROUND → DECALS → …`; fog/units still
collect on top. Fail-soft: if a tile FBO is incomplete, fall back to the
existing `visibleCells` cell emit for that tile only.

## Out of scope

- Replacing the cell grid in sim, gen, or save.
- Putting doodads/units into the dense tile (they stay sparse lists /
  `UnitSpatialIndex`).
- Hierarchical pathfinding.
- Camera-Z / perspective ([`../overview.md`](../overview.md) “Future”).
- Changing `MIN_ZOOM = 1.0`.

## Why not now

Cell-rect culling is the common zoomed-in path and just shipped. This story
is the map-size / zoom-1 / bridge lever, and it is an invalidation + VRAM
design, not a tight loop. Contract it when GROUND collect/flush is a
measured ceiling again, or when the bridge needs DECALS on the 480×320
plate.

## Open questions (when contracted)

- GPU tile size: 32 vs 64; px/cell (standalone 32, bridge maybe 8).
- One FBO per tile vs an atlas of resident tiles.
- Whether height/normal targets chunk the same way as color (they are
  already viewport-sized today; tiling them is optional).
- LRU vs distance eviction; margin ring so panning does not thrash.
- Exact dirty API from wall-break / rubble onto tile indices.
