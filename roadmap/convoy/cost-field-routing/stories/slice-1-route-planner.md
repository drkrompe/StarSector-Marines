# Slice 1 — Cost-weighted route planner + string-pull

> The router itself: cost-weighted grid A\* over the clearance mask, then
> string-pulled into the advisory polyline the corridor consumes.

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
