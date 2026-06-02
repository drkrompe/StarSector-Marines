# Slice 0 — Terrain cost field + vehicle clearance mask

> The two pure inputs the router needs, built and tested in isolation before
> anything consumes them. De-risks slice 1.

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
