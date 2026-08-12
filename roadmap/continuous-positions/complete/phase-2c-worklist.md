# Phase 2c — surveyed worklist (2026-08-12)

Full site inventory from the read-only survey. Companion to
`phase-2c-spatial-boundaries.md`; this is the execution checklist.

## Slicing

Two commits, ordered so every signature change is non-breaking (int args
widen to float; new overloads added beside int versions):

- **2c-1 (services + primitives, single agent or lead):**
  - `UnitSpatialIndex`: snapshot `int[] cellX/cellY` → `float[] posX/posY`
    (true positions at rebuild/add); `gather(float cx, float cy, ...)` —
    existing int-passing callers compile unchanged via widening; bucket
    binning floors internally.
  - `UnitDestinationSpatialIndex`: dest stays an int path cell (genuine
    grid data); the rebuild "already there" skip becomes an
    arrival-radius compare against true position; `gather` takes floats.
  - `TacticalScoring.cellDistance`: add float overload `distance(float...)`
    (the sweep converts unit-fed callers to it; int version stays for
    pure grid math). `RangeFalloff.dist` already accepts what widening
    provides — verify.
  - `Detonations.detonate`: unit loop reads `world.x/y(u)` and adds a
    `type.radius` term: hit iff `dist(pos, endpoint) <= aoe + radius`.
    Consider `getUnitIndex().gather` instead of the O(N) roster walk
    (radius = aoe + max unit radius) — decide in-slice.
  - `WorldPicker.nearestUnit`: pick radius becomes
    `max(PICK_RADIUS_CELLS, type.radius + forgiveness)` — decide exact
    form in-slice.
  - `DeathEvent`: record carries `float x, float y`; keep derived
    `cellX()`/`cellY()` floor accessors so rubble/guardpost consumers are
    untouched; `MechWreckSystem`/wreck FX switch to true position.
    Constructors: `DamageResolver:143` (capture x/y at :100), 
    `HubDemolitionSystem:130`.
  - `FogOfWarService`: no semantic change — the `lastCellX/Y` move gate
    and cell-origin shadowcasts are genuinely cell-grained. Fix only
    `BattleScreen:404` `(int) body.x` → `Math.floor` (negative-coord
    truncation bug).
  - `NavigationService.rebuildOccupancyMap`: already floors floats
    (phase 1); document as a density field. No code change expected.
- **2c-2 (distance-math sweep, 2–3 parallel agents by file):** convert
  category-9 sites (below) from `cellX/cellY` ints in float math to
  `x()/y()`. LoS / zone / biome / pathfind-start args KEEP `cellX/cellY`
  (floor at the genuine grid boundary). Render `cellX+0.5f` sites move to
  `x()` for smooth sub-cell motion (`UnitRenderService:106-177`,
  `BattleRenderer:634`).

## Category 9 — unit-fed float-math sites (the 2c-2 sweep)

TacticalScoring (8): 291, 403, 591-592, 678-679, 736, 780, 927, 1750-1751.
InfantryCohesion (8): 62-63, 73-74, 84-85, 88-89.
DroneSwarmAction (9): 133-134, 187-188, 273, 290-291, 325-326.
TurretBehavior (6): 61-62, 94, 112.
WorldStateBuilder (6): 165, 211-212, 293-294, 330-331.
ClearZone (5): 93, 163, 197. HoldZone (3): 101, 189, 233.
BackstopAssignedSquad (3): 95, 136, 161. FleeBehavior (4): 134, 152-153, 171.
OverwatchKillZone (2): 127, 172. ChokePointHold (2): 187, 255.
GarrisonCordon (2): 81, 137. HoldPortalCordon (2): 122, 212.
InfantryUnitPrep (2): 145-146. SwarmPressureBehavior (2): 109-110.
SquadMoraleSystem (2): 276-277. SquadFallbackSystem (2): 90-91.
SquadAlertSystem centroid (2): 138-139 (floored-int centroid consumed as
float by WorldStateBuilder:211, SabotageCommand:136, InfantryCohesion:84).
AirSystem centroid (2): 674-675. BreachAndAdvance (3): 115, 195-196.
Singles: MechCombatantBehavior:39, MechBreakContact:85,
EngageAtCurrentBand:59, ApproachPosture:66, EngagePosture:82, HoldPost:92,
PatrolMotion:187, KitRetrieverBehavior:64, EquipmentDropSystem:137,
InfantryWeapons:151 (RangeFalloff), HeavyWeapons:112 (RangeFalloff),
FiringSystem:130 (primary range gate), TurretAim:117, GuardPostPatrol:156,
AbstractZoneAction:98, BreakContact:89, SabotageCommand:136-137.
AirSystem:274 gather args: `Math.round(body.x)` → pass floats directly.

## Keep-int inventory (do NOT convert)

- LoS endpoints (`hasLineOfSight` + `LosCache` 12-bit int key): floor at
  call; all current `cellX/cellY` args stay.
- Zone/region/biome lookups: int-cell APIs; args stay `cellX/cellY`.
- Pathfind start cells (27 behavior files pass `cellX/cellY` as A* start).
- MovementService:151-152 + FacingSystem:181-200 path-cell step math.
- Occupancy map build/readers (density field over cells).
- Fog move-gate + shadowcast origins.

## UnitType.radius

Confirmed zero read sites pre-2c. Consumers land in 2c-1: Detonations,
WorldPicker. Arrival/separation uses deferred (not in scope).

## Shipped (a36955e) + follow-ups

Both slices landed as one commit (a36955e) — 49 files, build + tests
green. Integration fixes on top of the agent sweeps: InfantryCohesion's
leaderless fallback floors (not rounds) the true-position centroid into
a destination cell.

Deliberately NOT converted (agent judgment calls, confirmed):
- UnitRenderService footprint pad — GroundFootprint.emit wants the
  integer cell origin, and footprint drawers are static cell-anchored.
- BattleRenderer objective/equipment anchors — stored map cells.
- spawnSmokingWreck stays int cells (EffectsService API snaps to cells).

Critique pass on a36955e: no blockers; its three should-fix classes
(findBestTarget corner-vs-position scoring, unconverted centroid
consumers, Math.round-of-centroid siblings) landed as a follow-up
commit — findBestTarget chain takes true float positions end-to-end,
ReinforceContact/PatrolMotion/FlankApproach/Conquest/Assault centroid
math is center-consistent (Assault zone centroids centered at source),
round→floor at ZoneQueries/GarrisonAmbush/PatrolRoute/Backstop anchors.

Balance/polish notes from the critique (deliberate, not fixed):
- Detonations' per-unit +type.radius expansion is not mirrored by
  FIRING_AOE_SPREAD_RADIUS ally-safety or projectedRocketDamageOnTarget
  — rocket heuristics slightly under-margin vs the wider real blast.
  Revisit at tuning time.
- TurretAim drops lock on continuous out-of-range per tick — a target
  strafing the exact range circle can drop/reacquire at sub-cell
  granularity; self-heals, cosmetic. Hysteresis if it ever reads badly.

Candidate follow-up slices (precision debt found in-sweep, out of scope):
- TacticalScoring firing-position ring searches: distFromSelf/-Target
  compare int self cells vs int ring cells — tightly-coupled algorithm,
  convert whole or not at all.
- TacticalScoring.averageEnemyCell: int-rounded threat centroid feeding
  fallback scoring + getCoverAt facing.
- MechLoadoutComponent.overwatchAxisX/Y: int Math.round(centroid)
  snapshots for backstop drift tracking.
- Squad.lastSeenEnemyX/Y: int cells consumed with +0.5 centers at
  gathers; a float pair would drop the reconstruction.
