# Cost-field routing — handoff state

## State of play

**New track, just opened.** Design + decomposition are in
[`overview.md`](overview.md); read it first. Nothing built yet.

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

## Next up: slice 0 — cost field + clearance mask

Build the two pure inputs, unit-tested, nothing wired:
- `TerrainCostField` (battle/vehicle) — `GroundKind` → per-cell cost (`byte[]`
  or `float[]` — decide + record). Starting weights in the slice-0 doc.
- `VehicleClearance` (battle/vehicle) — walkable eroded by ~footprint half-width;
  cache one mask per radius.

Then slice 1 (cost-A\* + string-pull planner), slice 2 (wire spawn — the
behavioral payoff), slice 3 (retire RoadGraph after audit), slice 4 (tune).

## Slice chain

- [ ] **Slice 0** — `TerrainCostField` + `VehicleClearance`, pure + tested.
      ([`stories/slice-0-cost-and-clearance.md`](stories/slice-0-cost-and-clearance.md))
- [ ] **Slice 1** — `VehicleRoutePlanner` (cost-A\* + string-pull), tested.
      ([`stories/slice-1-route-planner.md`](stories/slice-1-route-planner.md))
- [ ] **Slice 2** — wire `ConvoyMeans` + `BattleSetup`; cell-based entry/exit.
      **Vehicles route off-road here.** ([`stories/slice-2-wire-spawn.md`](stories/slice-2-wire-spawn.md))
- [ ] **Slice 3** — retire dead RoadGraph routing after audit.
      ([`stories/slice-3-retire-roadgraph.md`](stories/slice-3-retire-roadgraph.md))
- [ ] **Slice 4** — tune weights / clearance / string-pull.
      ([`stories/slice-4-tune.md`](stories/slice-4-tune.md))

## Picking up cold

Start at slice 0. Both classes are pure functions of (`CellTopology`,
`NavigationGrid`[, radius]) — build them next to `ConvoyPlanner` in
`battle/vehicle`, unit-test against hand-built grids (mirror
`LocalTrajectoryPlannerTest`'s `carve` fixture style). The cost source is
`CellTopology.getGroundKind(x, y)`; walkability is `NavigationGrid.isWalkable`.
Don't wire anything into spawn until slice 2.
