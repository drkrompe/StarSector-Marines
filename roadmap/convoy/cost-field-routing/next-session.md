# Cost-field routing — handoff state

## State of play

**Slice 0 shipped; design in [`overview.md`](overview.md) (read it first).**
The two pure inputs exist + are unit-tested: `TerrainCostField` (GroundKind →
per-cell cost) and `VehicleClearance` (walkable eroded by footprint radius).
Nothing wired into routing yet.

The pivot: replace the convoy **road-graph** router with cost-field grid search
+ string-pulling. Roads become a *cost bias* over the free grid, not a topology
vehicles are confined to. Swaps only the route layer; the navigation-rework
tracking stack (`ReferenceCorridor → LocalTrajectoryPlanner → VehicleController`)
consumes the resulting polyline unchanged.

Two enabling facts (why this is mostly wiring): `GridPathfinder` already has a
per-cell extra-cost hook (the `occupancy` byte[]); `CellTopology.GroundKind`
already classifies every cell's terrain. See overview "why this is mostly wiring."

**Locked decisions:** erode a per-vehicle clearance mask (route can't thread a
pinch); multiplicative cost, baseline road = 1.0 (keeps octile heuristic
admissible); bypass RoadGraph now, retire after a consumer audit.

## Next up: slice 1 — cost-weighted route planner + string-pull

`VehicleRoutePlanner.route(startCell, goalCell, costField, clearance, grid)` →
sparse cell-center polyline (same `float[][]{xs,ys}` shape `expandToWaypoints`
returns). Cost-weighted grid A\* gated on the clearance mask, then a
clearance-aware string-pull. Reuse `GridPathfinder`'s machinery via a per-cell
cost-array overload (generalize its existing `occupancy` hook) — keep infantry
signatures untouched, baseline cost 1.0 so the octile heuristic stays admissible.
Unit-test against hand-built fixtures (mirror `LocalTrajectoryPlannerTest`/the
slice-0 `carve` style). Details in
[`stories/slice-1-route-planner.md`](stories/slice-1-route-planner.md).

Then slice 2 (wire spawn — the behavioral payoff; heed the **perimeter-erosion
flag** in the slice-0 complete doc — entry/exit cells get eroded out, so snap
route endpoints to the nearest in-mask cell), slice 3 (retire RoadGraph after
audit), slice 4 (tune).

## Slice chain

- [x] **Slice 0** — `TerrainCostField` + `VehicleClearance`, pure + tested.
      ([`complete/slice-0-cost-and-clearance.md`](complete/slice-0-cost-and-clearance.md)) ✅
- [ ] **Slice 1** — `VehicleRoutePlanner` (cost-A\* + string-pull), tested.
      ([`stories/slice-1-route-planner.md`](stories/slice-1-route-planner.md))
- [ ] **Slice 2** — wire `ConvoyMeans` + `BattleSetup`; cell-based entry/exit.
      **Vehicles route off-road here.** ([`stories/slice-2-wire-spawn.md`](stories/slice-2-wire-spawn.md))
- [ ] **Slice 3** — retire dead RoadGraph routing after audit.
      ([`stories/slice-3-retire-roadgraph.md`](stories/slice-3-retire-roadgraph.md))
- [ ] **Slice 4** — tune weights / clearance / string-pull.
      ([`stories/slice-4-tune.md`](stories/slice-4-tune.md))

## Picking up cold

Slice 0 is done — `TerrainCostField` + `VehicleClearance` (both `battle/vehicle`,
both pure + tested). Start at **slice 1**: build `VehicleRoutePlanner` next to
them. The cleanest path is a `GridPathfinder` cost-array overload — its existing
`occupancy` hook (`stepCost += OCCUPANCY_PENALTY × occupancy[idx]`) is the exact
seam; generalize it to a per-cell step *multiplier* from `TerrainCostField` and
gate traversal on `VehicleClearance.isPassableAt` instead of raw walkable. Then
string-pull the cell path with a clearance-aware LOS (a thickened trace, not the
bare `NavigationGrid.hasLineOfSight`). Unit-test against hand-built grids
(slice-0 `carve` style). Don't wire spawn until slice 2 — and there, mind the
perimeter-erosion flag in [`complete/slice-0-cost-and-clearance.md`](complete/slice-0-cost-and-clearance.md).
