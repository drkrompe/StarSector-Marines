# Cost-field routing — handoff state

## State of play

**Slices 0–2 shipped; design in [`overview.md`](overview.md) (read it first).**
The pure inputs (`TerrainCostField`, `VehicleClearance`), the router
(`VehicleRoutePlanner` + the `GridPathfinder` cost-field overload, 14 tests
green), AND the spawn wiring all exist. `ConvoyMeans.dispatch` now builds inbound
+ outbound corridors via `VehicleRoutePlanner.route(...)` over the cost field +
clearance mask (endpoints snapped onto the mask) instead of the road-graph BFS.
**Slice 2 is shipped but UNPLAYTESTED** — it's the behavioral slice; verify by
eye in a convoy run (see slice-2 complete doc Acceptance) before trusting it.

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

## Next up: PLAYTEST slice 2, then slice 3 — retire RoadGraph routing

**First: playtest.** Slice 2 changed runtime convoy routing and hasn't been seen
in motion. Run a convoy battle and confirm (slice-2 Acceptance): inbound still
docks + LANDED; outbound still drives the chosen exit → GONE; routes hug roads
but cut across open ground for real shortcuts; no threading of gaps too narrow
for the truck. If routing misbehaves, the knobs are the cost weights
(`TerrainCostField`, slice 4) and the clearance radius — not the tracking stack.

**Then slice 3 — retire dead RoadGraph routing** ([`stories/slice-3-retire-roadgraph.md`](stories/slice-3-retire-roadgraph.md)):
audit `ConvoyPlanner` consumers and the `RoadGraph` routing surface, then delete
what cost-field routing made dead. Concrete targets carried over from slice 2:
- `BattleSetup.maybeSpawnDebugConvoy` (dead since the ReinforcementService cut;
  still references `ConvoyPlanner.planPath`/`expandToWaypoints`/`pickExitNode`).
- `ConvoyPlanner.planPath` / `expandToWaypoints` once the debug path is gone —
  `ConvoyMeans` only still calls `ConvoyPlanner.pickExitNode`. Decide whether to
  keep `pickExitNode` (graph-based exit selection) or replace it with the
  **cell-based exit flood** the slice-2 plan floated (deferred — see slice-2
  complete doc "deviations"). The flood removes the last RoadGraph dependency
  from `ConvoyMeans` routing.

Slice 4 (tune cost weights / clearance / string-pull — deliberately not
load-bearing yet); optionally the cost-aware string-pull (so straightening can't
re-cross open ground a road-hugging A\* avoided — see slice-1 complete doc).

## Slice chain

- [x] **Slice 0** — `TerrainCostField` + `VehicleClearance`, pure + tested.
      ([`complete/slice-0-cost-and-clearance.md`](complete/slice-0-cost-and-clearance.md)) ✅
- [x] **Slice 1** — `VehicleRoutePlanner` (cost-A\* + string-pull) + `GridPathfinder`
      cost-field overload, pure + tested.
      ([`complete/slice-1-route-planner.md`](complete/slice-1-route-planner.md)) ✅
- [x] **Slice 2** — `ConvoyMeans.dispatch` routes via `VehicleRoutePlanner`
      (endpoints snapped onto the clearance mask). **Vehicles route off-road
      here.** ⚠️ shipped but UNPLAYTESTED.
      ([`complete/slice-2-wire-spawn.md`](complete/slice-2-wire-spawn.md))
- [ ] **Slice 3** — retire dead RoadGraph routing after audit.
      ([`stories/slice-3-retire-roadgraph.md`](stories/slice-3-retire-roadgraph.md))
- [ ] **Slice 4** — tune weights / clearance / string-pull.
      ([`stories/slice-4-tune.md`](stories/slice-4-tune.md))

## Picking up cold

Slices 0–2 are done — the route layer exists and is wired into the live convoy
spawn:
- `TerrainCostField.from(topology)` — per-cell terrain cost.
- `VehicleClearance.erode(grid, radius)` — footprint-eroded passable mask.
- `VehicleRoutePlanner.route(sx, sy, gx, gy, grid, costField, clearance)` →
  sparse cell-center polyline or `null`. Backed by the `GridPathfinder`
  cost-field overload (`findPath(..., float[] costField, boolean[] passable)`).
- `ConvoyMeans.dispatch` bakes the cost field + a radius-keyed clearance mask
  (lazy, cached on the means), snaps the graph entry/dest/exit cells onto the
  mask (`snapToMask`), and routes between them. Off-map staging + the corridor /
  controller stack are unchanged.

**The immediate next action is a playtest of slice 2** (see "Next up"). Code-wise
the next build step is **slice 3** (retire the now-dead RoadGraph routing —
`BattleSetup.maybeSpawnDebugConvoy`, `ConvoyPlanner.planPath`/`expandToWaypoints`,
and possibly `pickExitNode`). Don't start slice 3 deletions until the playtest
confirms cost-field routing is behaving — RoadGraph is still the fallback story
if something's badly wrong.
