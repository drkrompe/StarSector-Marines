# Slice 2 — Wire the spawn sites to the cost router

> The payoff. Swap convoy route construction from RoadGraph BFS to the cost
> router; vehicles can finally cross open ground. Corridor stack untouched.

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
