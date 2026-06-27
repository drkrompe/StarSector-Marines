# Large-map scaling — gut-check + the "be smarter with the data" plan

> Triggered by the combat-bridge densification (`WORLD_UNITS_PER_CELL` 20→7): the
> ground battle now reads as a small patch under the fleet, so we want **bigger
> maps**. Before scaling the cell count up, this doc records what actually breaks
> and the plan to fix it by **being smarter with the data** (tiling + view-residency)
> rather than capping map size. Direction call (user, 2026-06-27): *push the size,
> scale back only against genuine hard limits, but expect we can get clever first.*

## Current sizes

`MapScale` tiers (cells): SMALL 112×64 = 7,168 · MEDIUM 144×80 = 11,520 ·
LARGE 240×160 = 38,400. The bridge now runs its **own** decoupled grid —
`S0BattleCreationPlugin.BRIDGE_GRID_W/H = 480×320 = 153,600` (2× LARGE linear),
via the explicit-dimensions `BattleSetup.createConquestBuild` overload, so it does
not enlarge (or pay the decal cost of) standalone HIGH-risk battles.

## Gut-check verdict (two read-only audits, 2026-06-27)

### Memory — the decal FBO is the wall, and it's world-sized

`render2d/DecalAccumulator` allocates its FBO at **world resolution**, not screen:

```
ensureFbo:  fboPxW = gridW × DECAL_FBO_PX_PER_CELL,  fboPxH = gridH × …   (= 32)
→ memory = cellCount × 32 × 32 × 4 = 4 KB per cell   (linear in cells; quadratic in map width)
```

| | LARGE (38.4k) | 2× linear (153.6k) | 4× linear (614k) |
|---|---|---|---|
| Decal FBO @32px/cell | 157 MB | 630 MB | 2.5 GB |
| + `LightAccumulator` twin | +157 MB | +630 MB | +2.5 GB |

- The decal **source list** is a non-issue: `EffectsService.decals` is an `ArrayDeque`
  capped at `DECAL_SOURCE_CAP = 25,000` with FIFO eviction (~1 MB). The **FBO** is the cost.
- `LightAccumulator` is an identical world-sized twin — **killing LIGHTING (already
  planned) immediately frees the whole twin**, halving the standalone's big-FBO footprint
  today. (Keep LoS-shadow + DECALS; see backlog "Deprecate the LIGHTING layer".)
- **The FBO is lazy** (`ensureFbo` on first decal draw). The **bridge renders no decals**
  today (`DEFAULT_SCENE_LAYERS` omits DECALS — S3j), so it pays **$0** of this. The
  standalone pays it now; the bridge will pay it the moment DECALS comes over on a big map.
- Per-cell CPU arrays (`NavigationGrid` 17 B + `CellTopology` 16 B + `VisionService` 3 B +
  occupancy/cost ≈ ~50 B/cell total) are trivial: ~2 MB at LARGE, ~8 MB at 2×, ~32 MB at 4×.

**Bottom line:** the only memory wall is the **world-sized decal/light FBO**. It is O(map area)
*because it's one monolithic surface*. That's the thing to redesign, not a reason to cap maps.

### CPU — flat A* and an O(W×H) zone rebuild

- `GridPathfinder` is **flat octile A*, no hierarchy** (no HPA*/JPS/flow-field). Per-search
  ThreadLocal workspace ~24 B/cell, *reused*. Cost is O(expanded nodes); paths grow linearly
  with map width. No path cache; GOAP replans per-squad on a ~2 s cadence (not per-unit-per-tick),
  which keeps frequency sane.
- **The spike: `ZoneDetector.detect` is O(W×H)** and runs on **every wall destruction** (BFS
  flood + a 2×W×H temp alloc). LARGE ≈ 1.5 ms; 2× ≈ 3–4 ms; 4× ≈ 6–8 ms — a hitch per breach.
- Occupancy-map + spatial-index rebuilds are **O(units), not O(cells)** — map-size-independent.

**Bottom line:** both suspects are **linear** — fine to ~2× linear (knob-flip), risky at 4×.

## The plan — be smarter with the data (not cap the map)

### 1. Tiled, view-resident decal FBO (the headline; user's idea)

Replace the single world-sized FBO with a **grid of fixed cell-tiles** (e.g. 64×64-cell tiles),
each backing a **small FBO allocated on demand** when the tile enters the camera view (+margin)
and **freed (LRU/distance-evicted) when it leaves**. Decals stamp into the tile(s) they overlap;
render blits only the resident tiles intersecting the viewport.

```
resident memory = (visible tiles) × (tile FBO bytes)  ≈ O(view area / zoom²),  NOT O(map area)
```

So memory becomes **bounded by the camera view, independent of map size** — a 1000×1000-cell map
costs the same resident FBO as a 240×160 one. Paired with **#2 (camera-height cap)** the working
set has a hard ceiling.

This is exactly the **S3j "decals need projection-retarget, can't just join the EnumSet"** work
the bridge deferred — the gut-check confirms S3j is the *enabler* for big-map decals, not optional.

Open design points: tile size vs FBO-count overhead; re-stamp vs persist on evict-then-revisit
(persist decals in the capped source list and re-rasterize a tile's decals on re-entry — the
source list is already the source of truth, so eviction is free and revisit just replays); margin
ring to avoid thrash at the view edge; whether the bridge wants a *much* lower `px/cell` (a cell is
~7–20 screen px there, so 32 px/cell is wild oversampling — 8 would do).

### 2. Camera "height" cap + position-driven FBO residency (user's idea)

Bound max zoom-out (camera "height") so the visible world area — and thus the resident tile count —
has a ceiling. Allocate/deallocate tile FBOs as the camera moves (the residency policy for #1).
In the bridge this also pairs with the spectator free-cam's `visibleWidth` clamp
(`SpectatorCanvasPlugin.MAX_VISIBLE_WIDTH`).

### 3. Pathfinding ceilings (separate, sim-side)

- ✅ **Incremental zone rebuild — SHIPPED.** Wall breaches / structure demolitions only ever make
  cells walkable, so zones only **merge, never split** → `ZoneGraph.applyCellsOpened` folds each
  opened cell in (weighted union of the interior zones it bridges + portal re-detect over a cached
  doorway list), dropping the per-breach cost from O(W×H) to O(smaller-merged-zone + doorways). The
  full `rebuild()` stays the initial build + oracle + kill-switch fallback
  (`DevConfig.ZONE_INCREMENTAL_REBUILD`); equivalence (zone partition + portal reachability == full
  rebuild) is asserted by `ZoneGraphIncrementalTest`. This removes the ~6–8 ms wall-break hitch at 4×.
- **Hierarchical pathfinding** (HPA*/portal graph over the existing zone/portal structures, or
  flow-fields for many-units-one-goal) — still flat A* today; only needed once paths routinely span
  very large maps. Fine to ~2× linear. The remaining sim-side ceiling for going bigger.

## Shipped / status

- ✅ **Bridge map → 480×320** (2× LARGE), decoupled from `MapScale` via the explicit-dims
  `createConquestBuild` overload. Cheap today (no bridge decals). `BRIDGE_GRID_W/H` dialable.
  Playtest watch-items: does the BSP generator behave past its 240×160 test size? is HIGH-risk
  defender density too sparse spread over 4× area? (both → scale back or tune if bad).
- ⏳ **Tiled decal FBO + camera residency** — the #1/#2 plan above; lands with/unblocks S3j
  (DECALS in the bridge) and removes the standalone's world-sized FBO ceiling.
- ✅ **Incremental zone rebuild** — shipped (see §3); kills the per-wall-break O(W×H) spike.
- ⏳ **Pathfinding hierarchy** — sim-side, the remaining ceiling before ~4× linear (flat A* today).

## Pointers
- `render2d/DecalAccumulator`, `render2d/LightAccumulator` — the world-sized FBOs.
- `DevConfig.DECAL_FBO_PX_PER_CELL` (32), `DECAL_SOURCE_CAP` (25k).
- `battle/nav/GridPathfinder` (flat A*), `battle/.../ZoneDetector` (O(W×H) rebuild).
- `roadmap/vanilla-combat-bridge/stories/s3j-fx-fbo-retarget.md` — the deferred DECALS retarget.
- `roadmap/backlog.md` — "Deprecate the LIGHTING layer" (frees the twin FBO).
