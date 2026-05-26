# Convoy V1 polish — kinematics, docking, road reservation

V1 ([`stage2.md`](stage2.md) "V1 recap" for what landed first) shipped a
single MILITIA_TRUCK that drove a perimeter trunk to a tactical node and
deboarded. Playtest exposed three problems: the truck orbited waypoints
on tight geometry, the convoy occasionally failed to spawn at all, and
trucks were visually clipping through buildings/turrets the BSP
generator placed on top of road cells. This pass fixes all three and
adds the debug instrumentation that made them tractable.

Source session: [`../sessions/2026-05-21-2.md`](../sessions/2026-05-21-2.md).

## Truck kinematics — bicycle model + pure pursuit

```
src/main/java/com/dillon/starsectormarines/battle/ground/
  GroundBody.java        abstract base — public x/y/facingDeg/speed;
                         teleport() with onTeleport hook; subclass tick()
  BicycleBody.java       kinematic bicycle: wheelbase, maxSteeringRad,
                         steeringSlewRadPerSec; θ' = (v/L)·tan(δ)
  PurePursuit.java       carrot picker — walks polyline, advances cursor
                         past passed waypoints, returns lookahead target
  Pose.java              immutable (x, y, facingDeg) tuple for planners
  VehicleType.java       dropped AirHandling; added bicycle params +
                         lookAheadCells + abstract createBody()
  Vehicle.java           body field flipped from AirBody to GroundBody
  GroundSystem.java      advancePath uses pure pursuit; docking branch
                         triggers within DOCKING_TRIGGER_CELLS=6
```

`MILITIA_TRUCK`: wheelbase 1.4, maxSteer 25°, slew 180°/s, look-ahead
2.0. Min turn radius L/tan(maxSteer) ≈ 3.0 cells. The old hover model
struggled below ~4-cell turning radius and circled waypoints; the
bicycle reads visibly as "front-steer, rear follows."

## Reeds-Shepp docking

Within 6 cells of the LZ the convoy switches from pure pursuit to
playback mode: solve a Reeds-Shepp path from current pose to LZ pose,
footprint-validate each sampled cell against the navigation grid, then
play the body along the path. Falls back to pure pursuit if RS is
infeasible.

```
src/main/java/com/dillon/starsectormarines/battle/ground/
  ReedsShepp.java        CSC subset — LSL + LSR base × 4 transforms
                         (identity, τ timeflip, μ reflect, τμ).
                         Closed-form: shortest(start, goal, R);
                         sample(start, R, path, distanceCells)
  VehicleFootprint.java  5×3 OBB sample-grid feasibility check against
                         NavigationGrid

src/test/java/.../ground/ReedsSheppTest.java
  6 cases: identity, pure-straight, sampling at 0/total length,
  length monotonicity, CSC heading change. All pass.
```

Scope: CSC + CCC families (12 candidates). CCC landed after playtest
showed APCs circling at tight junctions where CSC had no feasible path.
CCSC deferred — not yet needed.

## Convoy spawn diagnostics + disconnected-graph fix

The trigger bug: `(151,0) → (124,88) — planPath failed` happened when
the chosen perimeter entry and the destination ended up in different
connected components of the road graph.

```
src/main/java/com/dillon/starsectormarines/battle/ui/debug/
  ConvoySpawnDumper.java
    Single JSON file at saves/common/.../debug/convoy_spawn_fail.json,
    overwritten per occurrence. Schema: reason, gridW/H, defenderSpawn,
    roadGraph stats + per-component node listing + entry/destComponentId.
    Uses SettingsAPI.writeJSONToCommon — script sandbox blocks
    java.nio.file / java.io.File (see [[starsector_script_sandbox]]).

src/main/java/com/dillon/starsectormarines/battle/BattleSetup.java
  maybeSpawnDebugConvoy:
    + sortedByDistance(...)             perimeter candidates by distance from defender spawn
    + reachableFrom(...)                BFS flood per entry
    + bestInteriorJunctionWithin(...)   degree threshold 3 → 2 fallback
  Replaces single-entry-pick with: iterate perimeter sorted by distance,
  per candidate flood the reachable component, pick best interior
  junction within that set. Three-layer fallback handles the
  disconnected-graph case.
```

## Road reservation — buildings/defenses respect the road graph

The clipping bug had stampers (compound walls, fortress towers, MG
nests, defense posts) painting on top of cells the road graph treated
as centerline. Fix is generator-side: derive a reservation mask from
the road graph and pass it down to every stamper that paints
non-walkable structure.

```
src/main/java/com/dillon/starsectormarines/battle/mapgen/road/
  RoadReservation.java   derives boolean[][] mask from RoadGraph:
                         every node cell + every edge chain cell

src/main/java/com/dillon/starsectormarines/battle/mapgen/bsp/
  BspCityGenerator.java
    + builds reservation mask right after RoadGraphBuilder.build
    + threads it to:
        - compound fillers (CompoundFiller signature change)
        - applyBeachShoreline
        - FortressWallStamper.stamp
        - DefensePostStamper.stamp
    + verifyRoadGraphWalkable() post-gen diagnostic — LOG.warns any
      centerline cell that ended up non-walkable

  CompoundFiller.java       added boolean[][] roadReservation param
  fill/MilitaryBaseFiller.java   skip reserved cells in markBridgedRoads
                                 + paintWallRing
  fill/GatedHousingFiller.java   same
  fill/DenseQuarterFiller.java   signature-only (plaza repaint stays walkable)

  DefensePostStamper.java   hasValidFootprint + slideToValid reject
                            footprints touching reserved cells;
                            stampNonConquest also takes the mask

  FortressWallStamper.java  paintWall / stampTower3x3 / stampMgNest
                            skip reserved cells — fortress trunk
                            becomes implicit gate

src/main/java/com/dillon/starsectormarines/battle/BattleSetup.java
  stampNonConquest sites derive mask from map.roadGraph
```

User's framing: "cities probably build up along a couple main roads and
sprawl from there over the years" — this fix bakes that into the
generator's contract.

### Why this is a gen-time concept

**Reservation is gen-time, orthogonal to runtime movement class.**
Trucks read the road graph + reservation; future tanks ignore the road
graph and use terrain-cost A* with wall-HP crush. Nature-only maps emit
an empty graph → empty mask → stampers behave as if reservation didn't
exist. The seam where vehicle classes diverge is runtime
(`VehicleType.movementMode`), not generator.

## Debug toggles widget

```
src/main/java/com/dillon/starsectormarines/ui/DebugTogglesWidget.java
  Collapsible "DEBUG +/-" header at top-center of battle screen.
  Each entry: (String label, BooleanSupplier getter, Runnable toggle).
  Direct GL (no BaseWidget chrome dependency).

src/main/java/com/dillon/starsectormarines/ops/BattleScreen.java
  + DEBUG_RENDER_DOCKING_PATHS overlay (green forward / red reverse)
  + DebugTogglesWidget registration in rebuild()
```

Reusable for any future boolean debug knob — one-line lambda per
toggle.

## HEAVY_APC — turret + overwatch

MILITIA_TRUCK retired. HEAVY_APC is the sole `VehicleType` constant:
4-capacity, roof-mounted HEAVY_MG turret, separate chassis/turret sprite
frames in `army-apc.png`.

```
VehicleType.HEAVY_APC:
  spriteFacingOffsetDeg=-90f, turretSpriteFacingOffsetDeg=-90f
  turretKind=TurretKind.HEAVY_MG, departsAfterDeboard=false
  overwatchDurationSec=20f
  turretMount=(-0.159, 0.268), turretPivot=(0.108, 0.025)
  turretVisualCells=0.7f
```

State machine extended: PENDING → INCOMING → LANDED → **OVERWATCH**
→ DEPARTING → GONE. In OVERWATCH the turret aim/fire loop runs
(shared `TurretAim.tick` + `fireSink.fire` path) and
`overwatchCountdown` ticks down; when expired the vehicle departs.
`departsAfterDeboard` flag on VehicleType selects between immediate
departure (truck behavior) and overwatch entry.

`turretSpriteFacingOffsetDeg` is separate from `spriteFacingOffsetDeg`
because chassis and turret frames face different directions in the
sprite sheet. `TurretAuthorPanel` added forward-direction arrows
(DRIVE and BARREL) for visual offset validation.

## CCC Reeds-Shepp family

`LpRmLp` base function added: curve-curve-curve for tight U-turns
where CSC has no solution (requires `ξ²+η² ≤ 16`, uses
`u = 2·asin(√d²/4)` arcs). Four geometric transforms (identity,
reflect μ, timeflip τ, τμ) produce 4 CCC candidates alongside the
existing 8 CSC — total 12 per dock attempt.

```
src/test/java/.../ground/ReedsSheppTest.java
  + cccTightUturn, cccSamplingEndpointMatchesGoal,
    plannerHandlesTightGeometry — 3 new CCC-focused tests
```

## Wall constraint — footprint check in advancePath

Vehicles now respect walls as movement constraints. `advancePath`
saves the body pose before each tick, ticks normally, then checks
`VehicleFootprint.isPoseFeasible()` against the navigation grid. If
the new pose overlaps any non-walkable cell:

- Position, facing, and speed revert to pre-tick values
- Internal `steeringRad` (private to BicycleBody) is NOT reverted —
  the "wheels keep turning" while the body is stopped
- PurePursuit waypoint-advance may shift the carrot to the next
  segment, naturally breaking corner deadlocks

Extension point for future terrain types (road preferred, off-road
penalty): replace the `isWalkable()` call inside
`VehicleFootprint.isPoseFeasible()` with a cost query and feed cost
back into `targetSpeed`. The footprint check signature stays stable.

## What's still open

- **Phase 3 / Hybrid A*** — global kinodynamic planning for non-LZ
  re-routing around dynamic blockers (wrecks, other trucks). Deferred
  until ambush mechanics actually create the demand.
- **CCSC Reeds-Shepp family** — would add a few more dock paths in
  complex geometry; defer until CCC+CSC fails visibly.
- **Terrain-cost steering** — road-preferred / off-road penalty layer
  on top of the binary walkability check. Extension point identified
  in VehicleFootprint; not needed until missions feature off-road
  vehicle traversal.
- **Corner smoothing** — tight road junctions where the min turn
  radius exceeds road width cause vehicles to pause (correct behavior
  for the bicycle model). Smoother polyline corners or wider junction
  road cells would eliminate the pause. Monitor in playtest.
- **Convoy spawn dumper** triggers on FAILURE; the post-gen road-graph
  diagnostic logs on every gen.
