# Cost-field routing — handoff state

## State of play

**Slices 0 + 1 shipped; design in [`overview.md`](overview.md) (read it first).**
The pure inputs (`TerrainCostField`, `VehicleClearance`) AND the router itself
(`VehicleRoutePlanner` + the `GridPathfinder` cost-field overload) exist and are
unit-tested (14 tests green). `VehicleRoutePlanner.route(...)` turns a
start/goal cell pair into the sparse cell-center polyline the corridor stack
consumes — or `null` for no-route. **Nothing wired into spawn yet** (slice 2).

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

## Next up: slice 2 — wire spawn (the behavioral payoff)

Swap the convoy spawn sites (`ConvoyMeans` + `BattleSetup`) from the road-graph
router (`ConvoyPlanner.planPath`/`expandToWaypoints` over `RoadGraph`) to
`VehicleRoutePlanner.route(...)`. Bake one `TerrainCostField` per battle from the
`CellTopology`, and one `VehicleClearance` per distinct footprint radius
(`VehicleClearance.radiusForWidth(visualWidth)`); feed the resulting polyline
into the same `ReferenceCorridor → LocalTrajectoryPlanner → VehicleController`
stack — that stack is untouched.

**Heed the perimeter-erosion flag** ([`complete/slice-0-cost-and-clearance.md`](complete/slice-0-cost-and-clearance.md)):
at radius ≥1 the clearance mask excludes every cell within `r` of the map border,
and convoy entry/exit *are* perimeter cells. `VehicleRoutePlanner.route` returns
`null` if either endpoint isn't in the mask, so slice 2's endpoint selection must
**snap the route start/goal to the nearest in-mask cell** (the off-map staging /
exit waypoints stay handled by the controller's `exitingOffMap` gate — this is
only about where the *on-grid* route terminates). A `null` route is the
fall-back signal — decide the behaviour (retry with relaxed clearance? abort the
convoy?) when wiring.

Then slice 3 (retire `RoadGraph` routing after a consumer audit), slice 4 (tune
cost weights / clearance / string-pull — the weights are deliberately not
load-bearing yet).

## Slice chain

- [x] **Slice 0** — `TerrainCostField` + `VehicleClearance`, pure + tested.
      ([`complete/slice-0-cost-and-clearance.md`](complete/slice-0-cost-and-clearance.md)) ✅
- [x] **Slice 1** — `VehicleRoutePlanner` (cost-A\* + string-pull) + `GridPathfinder`
      cost-field overload, pure + tested.
      ([`complete/slice-1-route-planner.md`](complete/slice-1-route-planner.md)) ✅
- [ ] **Slice 2** — wire `ConvoyMeans` + `BattleSetup`; cell-based entry/exit.
      **Vehicles route off-road here.** ([`stories/slice-2-wire-spawn.md`](stories/slice-2-wire-spawn.md))
- [ ] **Slice 3** — retire dead RoadGraph routing after audit.
      ([`stories/slice-3-retire-roadgraph.md`](stories/slice-3-retire-roadgraph.md))
- [ ] **Slice 4** — tune weights / clearance / string-pull.
      ([`stories/slice-4-tune.md`](stories/slice-4-tune.md))

## Picking up cold

Slices 0 + 1 are done — the whole route layer exists, pure + tested, in
`battle/vehicle`:
- `TerrainCostField.from(topology)` — per-cell terrain cost.
- `VehicleClearance.erode(grid, radius)` — footprint-eroded passable mask.
- `VehicleRoutePlanner.route(sx, sy, gx, gy, grid, costField, clearance)` →
  sparse cell-center polyline or `null`. Backed by the `GridPathfinder`
  cost-field overload (`findPath(..., float[] costField, boolean[] passable)`).

Start at **slice 2** (above): wire `ConvoyMeans` + `BattleSetup` to
`VehicleRoutePlanner` and snap eroded perimeter endpoints. This is the
behavioral slice — playtest a convoy run after (vehicles should now hug roads
but cut across open ground for real shortcuts, instead of being confined to the
road graph). Find the current spawn-time routing calls by grepping for
`ConvoyPlanner` / `expandToWaypoints`; the corridor/controller stack downstream
is unchanged.
