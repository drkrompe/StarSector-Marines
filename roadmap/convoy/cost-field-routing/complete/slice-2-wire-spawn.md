# Slice 2 — Wire the spawn sites to the cost router  ✅ SHIPPED (pending playtest)

> The payoff. Swap convoy route construction from RoadGraph BFS to the cost
> router; vehicles can finally cross open ground. Corridor stack untouched.

## Shipped

Commit: _(this commit)_ — full main compile green, reinforcement + vehicle test
suites green. **Behavioral slice — needs an eyes-on convoy run** (see Acceptance).

What actually landed vs. planned:

- **`ConvoyMeans.dispatch` rewired** — inbound is now
  `VehicleRoutePlanner.route(entryCell, destCell, grid, costField, clearance)`
  and outbound is `route(destCell, exitCell, …)`, replacing
  `ConvoyPlanner.planPath` + `expandToWaypoints`. The off-map staging prepend
  (inbound) and exit append (outbound) are byte-for-byte unchanged — the
  controller's `exitingOffMap` gate still handles them. The
  `ReferenceCorridor → LocalTrajectoryPlanner → VehicleController` stack is
  untouched.
- **Endpoint snapping** (`snapToMask`) — the slice-0 perimeter-erosion fix.
  HEAVY_APC is `visualWidthCells 1.4 → radius 1`, so perimeter entry/exit cells
  (and junctions hard against a building) are eroded out of the mask and
  `route()` would return `null`. `snapToMask` pulls each graph-node cell onto the
  nearest in-mask cell via expanding Chebyshev rings (≤ `SNAP_RADIUS = 8`).
- **Lazy per-battle caches on the means** — `TerrainCostField` baked once from
  `sim.getTopology()`; `VehicleClearance` masks cached in a `Map<radius, mask>`.
  Rebake-on-breach is deferred (overview decision) — a stale macro route is fine,
  the local planner handles live terrain.
- **Graceful fallback** — `route()==null` inbound logs + skips the dispatch
  (exactly the old `planPath==null` branch); outbound falls back to a single-cell
  stub at the LZ, and the appended off-map waypoint still pulls the truck off the
  board.

**Scope decisions / deviations from the plan:**
- *Only `ConvoyMeans` was wired.* `BattleSetup.maybeSpawnDebugConvoy` is dead code
  (no callers — "Replaces the prior `maybeSpawnDebugConvoy`"; retained behind
  `DEBUG_SPAWN_TEST_CONVOY` for emergency rollback). Migrating it is pointless;
  it gets deleted with the rest of the RoadGraph routing in **slice 3**.
- *Entry / dest / exit NODE selection stays graph-based* — the plan said "keep
  that selection logic" for entry/dest, and that's done. The plan also floated a
  **cell-based exit flood replacing `pickExitNode`**; that was **deferred** to keep
  the behavioral slice focused. Instead the graph still picks the exit perimeter
  node (farthest reachable from entry) and we snap + cost-route to it. A
  graph-reachable exit is essentially always terrain-reachable for the truck, so
  the `route()` succeeds; the cell-flood is a robustness refinement, not required
  for the open-ground payoff. **Follow-up:** fold the cell-based exit flood into
  slice 3 (when `RoadGraph` exit selection is audited) or slice 4 (tuning).
- *Single entry/dest commit.* The candidate loop still commits to the first
  graph-viable entry/dest pair; if its cost route fails the dispatch is skipped
  rather than retrying the next candidate. Cheap to add later if playtest shows
  frequent skips — noted for slice 4.

## Goal

`ConvoyMeans` and `BattleSetup` build inbound + outbound corridors via
`VehicleRoutePlanner` instead of `ConvoyPlanner.planPath`/`expandToWaypoints`.
Entry/exit become cell-based. The vehicle still receives a coarse polyline; the
`ReferenceCorridor → LocalTrajectoryPlanner → VehicleController` stack is unchanged.

## What lands

- Both spawn sites: replace `planPath` + `expandToWaypoints` with
  `VehicleRoutePlanner.route(entryCell, destCell, …)` for inbound and
  `route(destCell, exitCell, …)` for outbound. Off-map staging prepend (inbound)
  and exit append (outbound) stay exactly as-is — the controller's off-map gate
  (`exitingOffMap`) already handles them.
- **Cell-based exit selection** replacing `pickExitNode`: flood the clearance
  mask from `dest` to the reachable perimeter cells, pick the one farthest from
  `entry` (so the convoy drives across the map, not back out its gate). Fall
  back to `entry` if it's the only reachable perimeter cell.
- Build the `TerrainCostField` once per battle (cache on the sim / map result)
  and a `VehicleClearance` mask per footprint radius (cache by radius).

## Out of scope

- Deleting the now-bypassed RoadGraph routing (slice 3).
- Tuning (slice 4).

## Acceptance

- A convoy whose dest is reachable only by crossing open ground now routes there
  (the old graph router would have failed or detoured absurdly).
- Inbound still docks + LANDED; outbound still drives off the chosen exit → GONE
  (the slice-2 navigation-rework off-map fix still holds).
- No regression in the common all-on-road case — routes still hug streets.
- **Verify by eye** in a convoy run: smoother, road-hugging routes that cut
  sensible corners; no threading of gaps too narrow for the truck.

## Notes

- This is the behavioral slice — attach a critique-pass agent
  (`[[feedback_critique_pass]]`).
- Entry/dest cells come from the existing perimeter/rally selection; only the
  *route between them* changes. Keep that selection logic.
- If a route returns no-path (e.g. dest fully walled for a vehicle radius), log
  and skip the spawn as today's `planPath == null` branch does.
