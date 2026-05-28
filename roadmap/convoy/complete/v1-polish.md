# Convoy V1 polish — kinematics, docking, reservation, pathfinding

The maturation pass that took convoy from "drives a debug toy" to a
robust ground-vehicle stack. [`v1-foundation.md`](v1-foundation.md) is
the PoC this builds on.

## Commit chain

```
b5227e1  convoy: bicycle model + pure pursuit replace AirBody hover   (2026-05-21)
8eedc63  convoy: Reeds-Shepp LZ docking + VehicleFootprint collision check
2112bb6  convoy: dump-to-disk diagnostic on spawn failure
0f63a2e  debug: collapsible toggle widget at top of battle screen
5f9dd43  mapgen: road reservation — stampers respect the road graph
2703184  battle: Reeds-Shepp CCC family — tight U-turns for APC docking
7f958fd  battle: APC overwatch timer — 20s fire support then depart
06ad3ac  battle: APC sprite facing — separate chassis/turret offsets
a6e4fc2  battle: remove MILITIA_TRUCK — APC is the sole convoy vehicle
3e7dfa9  convoy: wall constraint + roadmap refresh for APC / resources
b1c405a  convoy: Hybrid A* planner, vehicle debug tools, reactive wall recovery
7202a08  convoy: direct pose playback, fresh outbound pathing, three-tier refinement  ← latest
```

Playtest after V1 exposed three problems: the truck orbited waypoints on
tight geometry, the convoy occasionally failed to spawn at all, and
trucks visually clipped buildings/turrets the BSP generator placed on
road cells. This pass fixes all three, adds the debug instrumentation
that made them tractable, then matures the path planner from reactive
pure-pursuit to direct pose playback.

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

The old hover model struggled below ~4-cell turning radius and circled
waypoints; the bicycle reads visibly as "front-steer, rear follows."

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
The `LpRmLp` base (curve-curve-curve for tight U-turns, requires
`ξ²+η² ≤ 16`, `u = 2·asin(√d²/4)` arcs) adds 4 candidates via the same
four transforms. CCSC deferred — not yet needed.

## Convoy spawn diagnostics + disconnected-graph fix

The trigger bug: `(151,0) → (124,88) — planPath failed` happened when the
chosen perimeter entry and the destination ended up in different
connected components of the road graph.

```
src/main/java/com/dillon/starsectormarines/battle/ui/debug/
  ConvoySpawnDumper.java
    Single JSON file at saves/common/.../debug/convoy_spawn_fail.json,
    overwritten per occurrence. Schema: reason, gridW/H, defenderSpawn,
    roadGraph stats + per-component node listing + entry/destComponentId.
    Uses SettingsAPI.writeJSONToCommon — script sandbox blocks
    java.nio.file / java.io.File (see [[starsector_script_sandbox]]).
```

The entry-pick was rewritten to iterate perimeter sorted by distance,
flood the reachable component per candidate, and pick the best interior
junction within that set (degree threshold 3 → 2 fallback). Three-layer
fallback handles the disconnected-graph case. This logic now lives in
`ConvoyMeans` (see [`reinforcement-integration.md`](reinforcement-integration.md));
the legacy `BattleSetup.maybeSpawnDebugConvoy` retains a copy behind the
unused debug flag.

## Road reservation — buildings/defenses respect the road graph

The clipping bug had stampers (compound walls, fortress towers, MG
nests, defense posts) painting on top of cells the road graph treated as
centerline. Fix is generator-side: derive a reservation mask from the
road graph and pass it down to every stamper that paints non-walkable
structure.

```
src/main/java/com/dillon/starsectormarines/battle/mapgen/road/
  RoadReservation.java   derives boolean[][] mask from RoadGraph:
                         every node cell + every edge chain cell

src/main/java/com/dillon/starsectormarines/battle/mapgen/bsp/
  BspCityGenerator.java
    + builds reservation mask right after RoadGraphBuilder.build
    + threads it to: compound fillers, applyBeachShoreline,
      FortressWallStamper.stamp, DefensePostStamper.stamp
    + verifyRoadGraphWalkable() post-gen diagnostic — LOG.warns any
      centerline cell that ended up non-walkable

  CompoundFiller.java       added boolean[][] roadReservation param
  fill/MilitaryBaseFiller.java   skip reserved cells in markBridgedRoads
                                 + paintWallRing
  fill/GatedHousingFiller.java   same
  fill/DenseQuarterFiller.java   signature-only (plaza repaint stays walkable)
  DefensePostStamper.java   footprint reject + slideToValid on reserved cells
  FortressWallStamper.java  paintWall / stampTower3x3 / stampMgNest skip
                            reserved cells — fortress trunk becomes a gate
```

User's framing: "cities probably build up along a couple main roads and
sprawl from there over the years" — this fix bakes that into the
generator's contract.

**Reservation is gen-time, orthogonal to runtime movement class.** Trucks
read the road graph + reservation; future tanks ignore the road graph and
use terrain-cost A* with wall-HP crush. Nature-only maps emit an empty
graph → empty mask → stampers behave as if reservation didn't exist. The
seam where vehicle classes diverge is runtime (`VehicleType.movementMode`),
not generator.

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

Reusable for any future boolean debug knob — one-line lambda per toggle.
(Later promoted to `DebugTogglesPanel` with a force-reinforcement action,
`014d1e6`.)

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

State machine extended: PENDING → INCOMING → LANDED → **OVERWATCH** →
DEPARTING → GONE. In OVERWATCH the turret aim/fire loop runs (shared
`TurretAim.tick` + `fireSink.fire` path) and `overwatchCountdown` ticks
down; when expired the vehicle departs. `departsAfterDeboard` selects
between immediate departure (truck behavior) and overwatch entry.

`turretSpriteFacingOffsetDeg` is separate from `spriteFacingOffsetDeg`
because chassis and turret frames face different directions in the sprite
sheet. `TurretAuthorPanel` provides visual validation of mount/pivot
positions and facing offsets (DRIVE and BARREL forward-direction arrows).
MILITIA_TRUCK's sprite is retained for mapgen flavor only.

## Wall constraint — footprint check in advancePath

Vehicles respect walls as movement constraints. `advancePath` saves the
body pose before each tick, ticks normally, then checks
`VehicleFootprint.isPoseFeasible()` against the navigation grid. If the
new pose overlaps any non-walkable cell:

- Position, facing, and speed revert to pre-tick values.
- Internal `steeringRad` (private to BicycleBody) is NOT reverted — the
  "wheels keep turning" while the body is stopped.
- PurePursuit waypoint-advance may shift the carrot to the next segment,
  naturally breaking corner deadlocks.

Extension point for future terrain types (road preferred, off-road
penalty): replace the `isWalkable()` call inside
`VehicleFootprint.isPoseFeasible()` with a cost query and feed cost back
into `targetSpeed`. The footprint-check signature stays stable.

## Hybrid A* path planner

`HybridAStarPlanner.refine()` replaces coarse cell-center waypoints with
kinematically-feasible paths. Searches `(x, y, heading)` config space
with 36 heading bins, 5 steering angles × 2 directions (forward +
reverse), VehicleFootprint feasibility with 0.6-cell clearance inflation,
and Reeds-Shepp analytic expansion. Both inbound and outbound paths are
refined; perimeter-edge waypoints where the footprint extends off-grid
are skipped and prepended as-is.

Re-plan from stuck: when `wallStuckTime > 2.0s`, runs Hybrid A* from the
vehicle's current pose to the remaining goal. 3-second cooldown between
attempts. Progress-based stuck-timer — `wallStuckTime` resets only when
the vehicle moves >1.5 cells from where it first got stuck, preventing
oscillation from clearing the timer.

## Direct pose playback (the plan IS the path)

`b1c405a`'s Hybrid A* planned kinematically-valid paths, but PurePursuit
execution threw away the direction info and just chased `(x, y)` points —
when it deviated, the reactive wall constraint couldn't recover reliably.
`7202a08` closes the plan/execution gap:

- `HybridAStarPlanner.extractPath` preserves heading data (was discarded).
- `GroundSystem.advancePlayback` interpolates planned poses directly —
  no steering law, no reactive wall recovery. `advancePath` dispatches on
  plan shape: headings present → playback, else → PurePursuit.
- Departing vehicles plan a **fresh** road-graph path to the farthest
  exit gate via `ConvoyPlanner.pickExitNode` instead of reversing the
  inbound — eliminates PurePursuit oscillation on sustained-reverse paths.
- `ConvoyPlanner.refineWithFallback` tries full HA*, then prefix-only HA*
  (first 40 waypoints) stitched with segment-derived headings, then full
  segment headings — a smooth departure even when the full path exceeds
  the planner's iteration budget.

Same pattern as RS docking in `advanceDocking`; the reactive collision
layer is no longer the primary path-following mechanism. See
[[ground_vehicle_playback]].

## Vehicle debug tools

Click-select vehicles on the battle map (1.5-cell pick radius). Debug
overlay shows path polyline (gray=passed, cyan=ahead), state text (state,
waypoint progress, speed, facing, wallStuckTime, path type).

F5 with a vehicle selected dumps JSON to
`saves/common/starsector_marines/debug/vehicle_state.json` via
`VehicleStateDumper`: current state, full inbound/outbound waypoints,
120-tick history ring buffer, and 17×17 ASCII walkability grid centered
on the vehicle.

## Still open after this pass

- **Terrain-cost steering** — road-preferred / off-road penalty layer on
  top of the binary walkability check. Extension point identified in
  `VehicleFootprint`; not needed until missions feature off-road
  traversal. (Tracked in [`../stories/driving-feel-tuning.md`](../stories/driving-feel-tuning.md)
  and the tanks line in [`../overview.md`](../overview.md).)
