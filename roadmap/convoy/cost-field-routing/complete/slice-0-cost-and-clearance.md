# Slice 0 — Terrain cost field + vehicle clearance mask  ✅ SHIPPED

> The two pure inputs the router needs, built and tested in isolation before
> anything consumes them. De-risks slice 1.

## Shipped

Commit: _(this commit)_ — both classes pure + unit-tested (9 tests green). Not
wired into spawn (slice 2).

What actually landed vs. planned:

- **`TerrainCostField`** (`battle/vehicle`) — `from(CellTopology)` bakes a
  per-cell `float[]` cost via `costFor(GroundKind)`. Weights (named constants,
  multiplicative, road baseline 1.0): `COST_ROAD=1.0` (STREET/SIDEWALK/LZ_MARKER/
  STRIPED), `COST_HARDSCAPE=1.5` (COURTYARD/BRICK/STONE/TILE), `COST_OPEN_GROUND=3`
  (GRASS/DIRT/SAND/SNOW), `COST_RUBBLE=5`, `COST_AVOID=8` (INDOOR/WATER + OOB).
  All finite — the field is preference, not passability.
- **`VehicleClearance`** (`battle/vehicle`) — `erode(grid, radiusCells)` →
  boolean mask, a cell passable iff its `(2r+1)²` Chebyshev block is in-bounds +
  walkable. `radiusForWidth(visualWidth)` = `round(width/2)` (HEAVY_APC 1.4 → r1).
  `radius 0` reproduces raw walkability.
- **Storage decision:** `float[]` for the cost field, `boolean[]` for the mask —
  clarity over compactness; the value count is one-per-cell and the slice-1 A\*
  reads each once per edge. Revisit a scaled `byte[]` only if perf flags it
  (slice 5).

**Flag for slice 2 (perimeter erosion):** because `NavigationGrid.isWalkable`
reads false out-of-bounds, erosion at radius ≥1 excludes every cell within `r`
of the map border. The convoy entry/exit are *perimeter* cells, so they'll be
eroded out — slice 2's endpoint selection must snap the route start/goal to the
nearest in-mask cell (or relax clearance at the endpoints) rather than feeding a
border cell straight in. The off-map staging/exit waypoints are handled
separately by the controller's `exitingOffMap` gate, so this is purely about
where the *on-grid* route terminates.

---

## Goal

From a `CellTopology` + `NavigationGrid`, produce (a) a per-cell traversal
**cost** that makes roads cheap and open ground progressively dearer, and (b) a
per-footprint-radius **clearance mask** of cells where a vehicle actually fits.
Both pure, both unit-tested. Nothing wired into motion or spawn.

## What lands

- **`TerrainCostField`** — built from `CellTopology.GroundKind`. A lookup table
  → per-cell cost, stored compactly (lean `byte[]` of scaled cost, or `float[]`
  — decide here and record). Starting weights (multiplicative, baseline 1.0):
  - `STREET`, `SIDEWALK`, `LZ_MARKER`, `STRIPED` → 1.0 (drive-on infrastructure)
  - `COURTYARD`, `BRICK`, `STONE`, `TILE` → ~1.5 (hardscape, fine to cross)
  - `GRASS`, `DIRT`, `SAND`, `SNOW` → ~3.0 (open ground, allowed but dear)
  - `RUBBLE` → ~5.0 (passable, ugly)
  - `INDOOR` → high or excluded (vehicles don't drive through building interiors)
  - `WATER` → N/A (already non-walkable on the grid)
  These are slice-0 starting values; tuned in slice 4.
- **`VehicleClearance`** — eroded walkable mask for a given footprint radius. A
  cell is vehicle-passable iff every walkable-grid cell within the erosion
  radius is walkable (start: footprint half-width, so the truck can align its
  length down a corridor). Cache one mask per distinct radius. Build is a simple
  morphological erosion / distance check over the walkable set.

## Out of scope

- The A\* / string-pull (slice 1).
- Wiring into spawn (slice 2).
- Rebake-on-breach (deferred — macro route is spawn-time; the local planner
  handles live terrain).

## Acceptance

Unit tests on hand-built `CellTopology`/`NavigationGrid` fixtures:
- Cost field: a STREET cell reads ~1.0, a GRASS cell reads the open-ground cost,
  RUBBLE the rubble cost; non-walkable/water reads "blocked / N/A" sanely.
- Clearance mask: a cell one off a wall is excluded at radius ≥1; the center of
  an open field is included; a 1-cell crack between two walls is excluded for a
  vehicle radius that can't fit, included for radius 0.
- Erosion never marks a non-walkable cell passable.

## Notes

- Keep both pure: `(topology, grid) → cost field` and `(grid, radius) → mask`.
  No `Vehicle` / `GroundSystem` coupling — testable and reusable for tanks /
  player vehicles.
- `[[zone_graph_ignores_edges]]`: the clearance mask is a walkability erosion,
  not a zone flood — don't reach for ZoneGraph here.
- Record the storage decision (`byte[]` vs `float[]`) in the shipped doc.
