# Slice 1 — Cost-weighted route planner + string-pull  ✅ SHIPPED

> The router itself: cost-weighted grid A\* over the clearance mask, then
> string-pulled into the advisory polyline the corridor consumes.

## Shipped

Commit: _(this commit)_ — pure + unit-tested (`VehicleRoutePlannerTest`,
7 tests green, alongside slice-0's 7). Not wired into spawn (slice 2).

What actually landed vs. planned:

- **`GridPathfinder` cost-field overload** — `findPath(grid, sx,sy,gx,gy,
  float[] costField, boolean[] passable)` plus the full 8-arg form threading both
  the occupancy penalty and the cost/clearance gate. `costField` (nullable)
  multiplies the directional step cost by the **destination** cell's terrain cost
  (`stepCost = DIR_COST[dir] × costField[nIdx]`); `passable` (nullable) gates
  traversal in place of raw walkability, and both endpoints must be passable.
  Infantry signatures are untouched — the old 6-arg form just delegates with
  `costField=null, passable=null`, byte-for-byte the prior behaviour. Layering:
  the overload takes **primitive arrays**, so `nav` gains no dependency on
  `vehicle` — `VehicleRoutePlanner` hands it `TerrainCostField.costArray()` /
  `VehicleClearance.passableArray()` (new package-private zero-copy accessors).
- **`VehicleRoutePlanner.route(sx, sy, gx, gy, grid, costField, clearance)`** —
  runs the cost A\*, then string-pulls. Returns `float[][]{xs, ys}` in cell-center
  coords (matches `expandToWaypoints`), or **`null`** for no-route (distinct from
  a valid path) so callers can fall back. A single-cell result (start == goal)
  also returns null — nothing to drive.
- **String-pull** — greedy "last visible from the anchor" funnel. Visibility is a
  clearance-aware **Amanatides–Woo supercover trace**: every cell the straight
  segment touches (corner-graze cells included) must be vehicle-passable, so a
  straightened segment never cuts a corner through a sub-clearance cell. At exact
  diagonal corners it visits both adjacent cells (the safe, conservative bias).

**Decisions recorded:**
- *Diagonal cost across a kind boundary* uses the **destination** cell's cost, as
  planned — matches the occupancy model and keeps the octile heuristic admissible
  (nothing is cheaper than a road at 1.0).
- *String-pull is cost-blind* (clearance-aware only). It can straighten a
  road-hugging A\* path across a passable grass corner, because the geometric pull
  doesn't re-check terrain cost — that's the explicit slice-4 "cost-aware pull"
  follow-up. **Consequence for tests:** cost-bias (road-vs-shortcut) is asserted
  on the raw A\* **cell path**, not the pulled polyline; the polyline is tested
  only for geometry (collapse-to-endpoints, keep-a-corner, clearance-clear).

## Goal

`VehicleRoutePlanner.route(startCell, goalCell, costField, clearance, grid)` →
a sparse cell-center polyline (`float[][]{xs, ys}`, same shape `expandToWaypoints`
returns) that hugs roads, crosses open ground only when it pays, never threads a
gap the vehicle can't fit, and is geometrically clean (no staircase). Pure,
unit-tested. Not wired.

## What lands

- **`GridPathfinder` cost-array overload** — generalize the existing
  `occupancy` hook into a per-cell step multiplier: `stepCost = DIR_COST[dir] ×
  costField[nIdx]`, traversal gated by the clearance mask (vehicle-passable)
  rather than raw walkable. Infantry callers keep today's signatures untouched.
  Baseline cost 1.0 keeps the octile heuristic admissible.
- **String-pull / funnel** — collapse the jagged cell path to a sparse polyline:
  walk from the current anchor, keep extending while a straight segment to the
  next cell stays within the clearance mask (clearance-aware LOS — a thickened /
  supercover trace, not the bare `hasLineOfSight`), drop the vertex when it
  breaks. Cell centers → `cellX + 0.5`.
- **`VehicleRoutePlanner`** wrapping both, returning the polyline (≥2 points;
  signal no-route distinctly from an empty path).

## Out of scope

- Spawn wiring + entry/exit selection (slice 2).
- Cost-aware pull (only straighten if cost doesn't rise past a threshold) —
  slice 4 if needed.

## Acceptance

Unit tests against hand-built fixtures:
- Straight clear road → near-straight 2–3 point polyline along it.
- Road bends around a block → polyline follows the road, not through the block.
- A shortcut across an open plaza that's much shorter than the road detour →
  the route takes the plaza (cost bias loses to a big distance win).
- A plaza shortcut that's only marginally shorter → the route stays on the road
  (cost bias wins).
- A corridor one cell too narrow for the vehicle radius → routed around (the
  clearance mask excludes it), or no-route if that's the only way.
- Every returned segment is clearance-clear (string-pull never cuts a corner
  through a sub-clearance cell).

## Notes

- Diagonal step across a kind boundary: use destination-cell cost (matches the
  occupancy model). Record if changed.
- Keep pure: `(cells, costField, clearance, grid) → polyline`. Reuse
  `[[spatial_unit_index]]`-style flat-array discipline already in `GridPathfinder`.
