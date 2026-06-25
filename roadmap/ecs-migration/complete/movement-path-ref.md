# Movement path ref + cursor into MOVEMENT — Entity carries no movement state (path-ref fold-in)

**Shipped 2026-06-25** — foundation `5ee1090`, cutover `688a7568`. Suite green
at 768.

Completes the `MOVEMENT` component (step 3e left it holding only `moveProgress`,
with the path ref deferred). The per-unit path (`int[] path`) + cursor
(`int pathIdx`) left the `Entity` POJO for the entity world's `MOVEMENT`
component. `MOVEMENT` is now `{float moveProgress, int[] path (OBJECT), int
pathIdx}`. **An `Entity` object now holds no mutable movement state at all** —
cell/hp/combat/movement are all id-keyed in the world.

## Why it was the big one

The path convenience API (`pathCellCount/pathCellX/pathCellY/pathEmpty`) lived
*on* `Entity`, which since `335cce8` has no world handle. So folding the path
into the world forced relocating that API off `Entity` — which broke ~40 reader
sites across 23 files that had to convert to a fetch-once-by-id idiom. The path
is also a mutable `int[]` reassigned on every repath and a cursor incremented
every cell-step, threaded through hot nav/render loops.

## Two-commit shape

- **Foundation (`5ee1090`)** — introduced `battle.nav.Paths`, pure stateless
  reads over a flat `int[]` (`cellCount/cellX/cellY/isEmpty/destX/destY`), and
  pointed `Entity`'s convenience methods + `NavigationService.pathDestX/Y` at it.
  Pure refactor, green, proved the helper.
- **Cutover (`688a7568`)** — moved the storage, deleted the `Entity` API,
  converted the readers.

## What landed (cutover)

- **Component**: `MOVEMENT` gains `MOVEMENT_PATH` (OBJECT, field 1) +
  `MOVEMENT_PATH_IDX` (INT, field 2). `register(8, "Movement", FLOAT, OBJECT,
  INT)`.
- **Seed**: `allocate` writes `GridPathfinder.EMPTY_PATH` into the path column —
  an OBJECT column appends as **null** and every `Paths` read dereferences it, so
  the sentinel must be seeded (the second non-zero default, after the AI_STATE
  fall-back cell). `pathIdx` + `moveProgress` zero-init.
- **Entity**: `path`/`pathIdx` fields + `pathCellCount/pathCellX/pathCellY/
  pathEmpty` **deleted**; `advanceAlongPath` reads path+pathIdx by id and steps
  the cursor via `setPathIdxById` in all three branches.
- **Adapters**: registry `pathById`/`setPathRefById`/`pathIdxById`/
  `setPathIdxById`; `World` `path`/`setPathRef`/`pathIdx`/`setPathIdx`.
- **NavigationService**: `setPath` reads the OLD path by id (for the occupancy
  oldDest) *before* writing the new ref + resetting the cursor; `pathDestX/Y`
  became instance (by-id) methods; `rebuildOccupancyMap` fetches the path once
  per unit.
- **Reader sweep (23 files, ~40 sites)** — fanned to 4 Sonnet agents on disjoint
  buckets (infantry postures / infantry zone-actions / mech / decision+render+
  index), compiler-backstop pattern. Per-site: fetch the path int[] once via the
  in-scope handle (`sim.world().path(id)` in behaviors/postures/actions;
  `registry.pathById(id)` in `TacticalScoring`/`UnitRenderService`/
  `UnitDestinationSpatialIndex`) then `Paths.cellCount/cellX/cellY/isEmpty`. The
  `moveToward` loops **re-fetch path+pathIdx after a `setPath`** (it resets the
  cursor — a stale local would misread "arrived"). `UnitDestinationSpatialIndex
  .gather` gained a `registry` param; `FleeBehavior.cellsTraveled` now takes the
  resolved `int pathIdx`; `SquadStateDumper`'s `!= null` guard dropped (the path
  is never null now).
- **Tests**: 8 test files converted (same fetch-once idiom) + two new
  `UnitRegistryTest` parity tests (path seeds to EMPTY_PATH / pathIdx 0;
  undisturbed by dense tail-swap). Main-thread.

## Perf

Path/pathIdx reads are now world location-probes; hot loops fetch the path int[]
once per unit/iteration (the accepted bounded-N step-back,
[[feedback_storage_foundation_build_right]]). No reader double-fetches in a hot
block.

## Follow-ups

- **MOVEMENT membership-narrowing** — restrict `MOVEMENT` to actual movers
  (turrets/hubs don't path); "has a path capability" now genuinely defines a
  mover. Pairs with the AI_STATE membership-narrowing.
- **Step 4 — dissolve `UnitRegistry`**: fold the `Crashing`/`MechLoadout`
  component stores into archetype membership, then hop id-mint + the dense
  `Entity[]` to the world / sim.
