# Battle-tier package reorganization — feature-vertical

Restructure `battle/` from its current mix of *technical-layer* packages
(`ai/`, `fx/`, `damage/`, `shots/`) and *feature-domain* packages
(`drone/`, `air/`, `compound/`, `reinforcement/`) into a consistent
**feature-vertical** layout: a thin framework/engine core plus
self-contained feature domains that each own their data, systems,
behaviors, and render hooks.

Locked direction, not a proposal. Companion to
[`ecs-migration/`](../ecs-migration/overview.md) — same destination
(Services own state, Systems are stateless tick consumers over a shared
registry), this doc settles *where the code lives* rather than *how state
is stored*.

## Why

The package taxonomy splits on **two axes at once**:

- **Technical layers** — `ai/`, `ui/`, `fx/`, `damage/`, `nav/`,
  `sprites/`, `profile/`, `shots/`.
- **Entity/feature domains** — `drone/`, `air/`, `ground/`, `compound/`,
  `reinforcement/`, `flyby/`, `turret/`.

When a feature is added there's no rule for which axis it belongs to, so
it lands in **both** — producing the two symptoms simultaneously:

- **Conflation** (one package, several concepts): `ai/` holds dispatch
  infra + shared scoring + 8 behavior strategies; `map/` holds data model
  + theming + content metadata + gameplay knobs + a generator; `fx/`
  mixes pure visuals with *simulation state* (`Projectile`, `ShotEvent`,
  `PendingDetonation`); `sim/` bundles the orchestrator with a 1593-line
  `BattleSetup` + resource pools + dispatch plumbing.
- **Fracture** (one concept, several packages): the fire→hit→damage→fx
  pipeline spans `weapons/` + `shots/` + `damage/` + `turret/` + `fx/`;
  *squad* spans `unit/` + `squad/` + `ai/goap/` + `ai/`; *static
  emplacements* span `turret/` + `drone/` + `ai/`; *vehicles* span
  `air/` + `ground/` + `map/`; *map generation* lives in both
  `map/UrbanMapGenerator` and the `mapgen/` tree.

## Organizing principle: framework core vs feature domain

The user's framing (from a prior engine project): an **engine** offers
opinions on *how* mechanisms operate and iterate per update tick (2D
navigation, the tick loop, GOAP planning); the **game** supplies the
*specific behaviors* layered on those mechanisms.

- **Framework core** = mechanism with no single feature owner: the entity
  registry / SoA storage, the navigation substrate, the GOAP *engine*
  (planner — not the behaviors), the tick orchestrator, shared scoring,
  profiling. Stays central.
- **Feature domain** = behavior + data + visuals + render hooks for one
  slice of the game. Each domain is self-contained.

"No shared `ai/` or `fx/` layer" means **kill the catch-alls** — `ai/` as
a behavior dumping ground, `fx/` as a visual dumping ground. It does
**not** mean dissolving genuine mechanism: the GOAP engine and the entity
registry are framework, not catch-alls. Behaviors and visuals distribute
into domains; engines stay in a thin core.

## Boundary decisions (locked)

1. **Shared GOAP vocabulary → framework built-ins; slice-specific behaviors
   owned by their domain.** Empirical finding (2026-05-28): the GOAP graph
   partitions *mostly cleanly by actor* — the three composers
   (`GoapInfantryBehavior` / `GoapMechBehavior` / `GoapDroneBehavior`)
   import **disjoint** goal sets, and each action is referenced by only
   1–2 goals. So the genuinely-shared "built-in" set is small; most
   actions/goals are slice-owned. See [the partition rule](#the-goap-partition-rule).
2. **Single `EffectsService` sink for now.** Most visual fx are
   combat-caused, so the one sink lives in `combat/`. Per-domain fx
   ownership can be targeted later; not worth fragmenting the service now.
3. **`HeavyWeapons` mechanism → framework (`combat/`); mech/vehicle-specific
   config in the slice.** `HeavyWeapons` is the chassis-weapon firing
   mechanism (mech today, future tanks); the `Mech*` config/role/loadout
   lives in `mech/`.

## Target tree

```
battle/
  # ---- framework core (mechanism; no single feature owner) ----
  entity/          Unit, UnitRegistry, SoA arrays, spatial indices,
                   Faction, UnitType, UnitRole, FactionUnitRoster,
                   UnitRosterService          (rename of unit/)
  nav/             NavigationGrid, GridPathfinder, NavigationService,
                   Direction, zone/{NavigationZone, Portal, *Detector,
                   ZoneGraph}                 (unchanged)
  decision/        TacticalScoring, AttackerIndexService, UnitUpdateSystem,
                   UnitBehavior, TacticalContextService, TacticalMap/Node/Linker
    goap/          Planner, Action, Goal, ActionStatus, Predicate, WorldState
    goap/action/   built-in shared actions (see partition rule)
    goap/goal/     built-in shared goals (see partition rule)
    goap/scoring/  RoleAssigner, Scorer, Scorers
    goap/world/    PredicateEvaluator, WorldStateBuilder, ZoneQueries
  sim/             BattleSimulation (orchestrator + tick order),
                   PendingOccupancyDelta, PendingTargetMutation
  setup/           BattleSetup, DefenderRoster
  profile/         TickProfile, TickInnerProfile, LosCache

  # ---- actor domains (entity + weapons + behavior/goap + lifecycle + render) ----
  infantry/        InfantryWeapons, Marine{Loadout,Secondary,Weapon},
                   CombatantBehavior, GoapInfantryBehavior, InfantryCohesion,
                   InfantryUnitPrep, KitRetrieverBehavior, infantry goals,
                   equipment/ (kit drops + retrieval)
  mech/            MechWeapon, MechLoadoutState, MechRole, MechCombatantBehavior,
                   GoapMechBehavior, MechBreakContact, Mech* goals
  drone/           Drone, DroneHubUnit, DroneSpawner, DroneCrashSystem,
                   HubDemolitionSystem, DroneHubBehavior, GoapDroneBehavior,
                   DroneSwarmAction, DefendHubGoal
  air/             AirBody, AirHandling, AirSystem, Shuttle*, MountedTurret,
                   TurretMount, SteeringMode, engine/   (unchanged)
  vehicle/         GroundBody, BicycleBody, GroundSystem, kinematics
                   (PurePursuit, ReedsShepp, HybridAStarPlanner, Pose),
                   ConvoyPlanner, Vehicle, VehicleType, VehicleFootprint,
                   + MapVehicle, VehicleKind   (rename/absorb of ground/)
  turret/          MapTurret, DefensePost, DefensePostKind, TurretKind,
                   TurretRole, TurretFireService, TurretFireSink,
                   TurretDemolitionSystem, TurretBehavior, TurretAim,
                   StructureBehavior

  # ---- feature domains (cross-actor systems) ----
  combat/          ShotService, ShotEvent, Projectile, PendingDetonation,
                   Detonations, DamageService, DamageResolver, HitResponseService,
                   HeavyWeapons, FireStance, RangeFalloff, ShotEndpoint, ShotRaycast
    fx/            EffectsService (single sink), Decal, DecalKind, Particle,
                   SmokePlume, SmokingWreck, WeaponLights, ImpactFx,
                   ImpactDecals, ImpactProfile
  squad/           Squad, SquadAlertSystem, SquadFallbackSystem,
                   SquadMoraleSystem, SquadReplanSystem, SquadPlan, SquadAlertLevel
  command/         CommanderService, MissionCommand, Conquest/Assault/Sabotage,
                   ObjectiveAssignment, AssignmentKind, BattleResources, ResourceType
    objective/     Objective, ObjectivesService, WinCheckSystem, *Objective
    reinforcement/ ReinforcementService, triggers, means, LandingZoneScorer,
                   RecaptureTarget*
    compound/      CompoundService, CompoundCaptureSystem, CompoundGarrisonSystem
  vision/          Shadowcast, VisionService, PlayerVisionState,
                   BuildingVisibilityPass     (unchanged)
  world/
    model/         Building(s), BuildingKind, CellTopology, WallMasks,
                   Doodad, DoodadService, PointOfInterest, RoomPurpose,
                   DistrictTheme, TileManifest, MapScale, TimeOfDay
    gen/           MapGenerator + UrbanMapGenerator (merged), bsp/, bsp/fill/,
                   road/                        (the mapgen/ tree)
    tiles/         NatureTile(set), Sliced/FixedGrid TileDrawer, SpriteSheet*,
                   TileSink, UrbanTile3(Tileset)  (the sprites/ tree)

  # ---- presentation ----
  ui/              BattleHud, panels, picking, highlight, debug   (unchanged)
  flyby/           FighterWing, FlybyOverlay, FlybyRoster, ...  (standalone feature)
```

## File-by-file mapping (by source package)

| current | →
| --- | ---
| `unit/` | `entity/` (rename); `Squad` → `squad/`
| `ai/` (behaviors) | `CombatantBehavior`, `InfantryCohesion`, `InfantryUnitPrep`, `KitRetrieverBehavior` → `infantry/`; `MechCombatantBehavior` → `mech/`; `DroneHubBehavior` → `drone/`; `TurretBehavior`, `TurretAim`, `StructureBehavior` → `turret/`; `SquadAlertLevel` → `squad/`
| `ai/` (infra) | `UnitUpdateSystem`, `UnitBehavior`, `TacticalScoring`, `AttackerIndexService` → `decision/`; `FleeBehavior`/`FallbackBehavior` → `decision/` if shared, else `infantry/` (verify at slice time)
| `ai/goap/` | engine (`Action`/`Goal`/`Planner`/`Predicate`/`WorldState`/`ActionStatus`) → `decision/goap/`; `SquadPlan` → `squad/`; `GoapInfantryBehavior` → `infantry/`; `GoapMechBehavior` → `mech/`; `GoapDroneBehavior` → `drone/`
| `ai/goap/actions/` | shared/posture → `decision/goap/action/`; `DroneSwarmAction` → `drone/`; `MechBreakContact` → `mech/` (rest per partition rule)
| `ai/goap/goals/` | follow composer: `Mech*` → `mech/`; `DefendHubGoal` → `drone/`; rest → `infantry/` (squad-coordination goals may promote to `squad/`)
| `ai/goap/scoring/`, `ai/goap/world/` | → `decision/goap/scoring/`, `decision/goap/world/`
| `tactical/` | → `decision/` (TacticalContextService, TacticalMap, TacticalNode, TacticalLinker)
| `weapons/` | `InfantryWeapons`, `Marine*` → `infantry/`; `MechWeapon`, `MechLoadoutState`, `MechRole` → `mech/`; `HeavyWeapons`, `Detonations`, `FireStance`, `RangeFalloff`, `ShotEndpoint`, `ShotRaycast` → `combat/`
| `shots/`, `damage/` | → `combat/`
| `fx/` | sim-state (`Projectile`, `ShotEvent`, `PendingDetonation`) → `combat/`; visuals + `EffectsService` → `combat/fx/`
| `ground/` | → `vehicle/`
| `map/` | data/theming/content → `world/model/`; `UrbanMapGenerator` → `world/gen/`; `MapVehicle`, `VehicleKind` → `vehicle/`
| `mapgen/` (+ bsp, fill, road) | → `world/gen/`
| `sprites/` | → `world/tiles/`
| `objective/` | → `command/objective/`
| `reinforcement/` | → `command/reinforcement/`
| `compound/` | → `command/compound/`
| `sim/` | `BattleResources`, `ResourceType` → `command/`; `BattleSetup`, `DefenderRoster` → `setup/`; rest stays
| `equipment/` | → `infantry/` (kit)
| `air/`, `turret/`, `nav/`, `vision/`, `ui/`, `flyby/`, `profile/`, `drone/` | stay (gaining moved-in members noted above)

## The GOAP partition rule

Settled per-file during the `ai/` dissolve slice, but the rule is fixed:

- **Goals follow their composer** (the actor slice whose
  `Goap*Behavior` builds the plan). The three composers import disjoint
  goal sets, so this is unambiguous: `Mech*` → `mech/`, `DefendHubGoal` →
  `drone/`, the rest → `infantry/`.
- **Actions follow their goal(s)**: an action referenced only by goals in
  one slice moves with that slice; an action used by goals across multiple
  slices, or part of the base posture set, becomes a built-in in
  `decision/goap/action/`. Actor-prefixed actions (`Mech*`, `Drone*`) are
  unconditionally slice-owned.
- **Squad-coordination goals** (`BackstopAssignedSquadGoal`,
  `SecureCompoundGoal`, `ReinforceContact`) currently compose under
  infantry. Park them in `infantry/` for the mechanical move; promote to
  `squad/` only if/when a second actor type starts composing them.

## Slice plan

Each slice is a self-contained mechanical move (imports change, logic
doesn't), independently compilable + committable, and a good candidate to
fan out to a Sonnet subagent (keep design/verify on the main thread).
Ordered to remove the worst conflation first and defer the highest
reference-churn moves to a quiet base.

1. ~~**`combat/` consolidation** — fold `fx/` + `shots/` + `damage/` + the
   combat-shared half of `weapons/` into `combat/` (+ `combat/fx/`).~~
   **SHIPPED.** Killed the `fx/` simulation-state conflation; unified the
   fire→damage pipeline. `fx/`/`shots/`/`damage/` dissolved. Note for
   future slices: a full recompile (`--rerun-tasks`) is needed to surface
   the complete missing-import set — Gradle's incremental compile
   under-reports; and same-package *test* files need relocating too.
2. ~~**`world/` consolidation** — `map/` + `mapgen/` + `sprites/` →
   `world/{model,gen,tiles}`; merge `UrbanMapGenerator` into `gen/`.~~
   **SHIPPED** as 2a (`sprites/`→`world/tiles/`), 2b (`mapgen/` tree →
   `world/gen/`), 2c (`map/`→`world/model/` + `UrbanMapGenerator`→`gen/`).
   `MapVehicle`/`VehicleKind` parked in `world/model/` for slice 3;
   `DistrictTheme`/`MapDistrictTheme` dedup deferred.
3. ~~**`vehicle/`** — `ground/` → `vehicle/`, absorb `MapVehicle`/`VehicleKind`.~~
   **SHIPPED.** `ground/` (11 files) renamed to `vehicle/`; `MapVehicle` +
   `VehicleKind` (with nested `VehicleSheet`) lifted out of `world/model/`
   into `vehicle/`. `ground/` dissolved. Clean package rename — no splits,
   no new cross-imports. Build + full suite green.
4. ~~**`command/` consolidation** — nest `objective/` + `reinforcement/` +
   `compound/` + resources (`BattleResources`/`ResourceType`) under `command/`.~~
   **SHIPPED.** `objective/` (6), `reinforcement/` (12), `compound/` (3)
   moved in as `command/{objective,reinforcement,compound}/` subpackages;
   `BattleResources` + `ResourceType` lifted flat from `sim/` into
   `command/`. The only bare-name fix-up was `BattleSimulation` (a `sim/`
   sibling using `BattleResources`) gaining an explicit import. No
   self-imports anywhere. 8 same-package tests relocated. Build + suite green.
5. ~~**`squad/` consolidation** — move `Squad` (from `unit/`), `SquadPlan`
   (from `ai/goap/`), `SquadAlertLevel` (from `ai/`) into `squad/`.~~
   **SHIPPED.** First true split: 3 classes pulled into the existing
   `squad/` (which held the 4 Squad*System consumers). Moved-file imports
   added on the main thread (`Squad`→`unit.{Unit,Faction}`,
   `SquadPlan`→`ai.goap.{Action,ActionStatus}`); left-behind sibling imports
   (`squad.Squad`×2, `squad.SquadPlan`×6) fanned out to a Sonnet subagent.
   Compiler caught one missed test sibling (`PlannerTest`, +`squad.SquadPlan`)
   — `git mv` test-dir glob misses single-class lifts; grep bare usages in
   `src/test` too. 111 files, build + suite green.
6. **`ai/` dissolve** (multi-sub-slice):
   - ~~6a engine + scoring + world + dispatch + tactical-scoring +
     tactical-graph → `decision/`.~~ **SHIPPED** (`c693c27`). GOAP engine →
     `decision/goap/` (+ `scoring/`, `world/`); dispatch infra
     (UnitUpdateSystem, UnitBehavior, TacticalScoring, AttackerIndexService)
     + the two role-agnostic dispatch behaviors (FallbackBehavior,
     FleeBehavior) → `decision/`; `tactical/` graph → `decision/` (dissolved).
     Class-specific FQN rewrite (actions/ + goals/ stay in `ai.goap`, so no
     blanket prefix rewrite). Bidirectional import fix-ups across the move
     boundary; 10 same-package tests relocated. Build + suite green.
   - ~~6b actor behaviors → `infantry/` / `mech/` / `drone/` / `turret/`.~~
     **SHIPPED** (`52817d7`). `infantry/` (new): CombatantBehavior,
     InfantryCohesion, InfantryUnitPrep, KitRetrieverBehavior,
     GoapInfantryBehavior. `mech/` (new): MechCombatantBehavior,
     GoapMechBehavior. `drone/`: DroneHubBehavior, GoapDroneBehavior.
     `turret/`: TurretBehavior, TurretAim, StructureBehavior. Class-specific
     FQN rewrite per destination; 4 cross-destination bare refs gained
     imports, same-destination refs stay same-package; staying goals/actions
     redirected via imports. `ai/` now holds only `goap/{actions,goals}`.
   - ~~6c goals/actions partition per the rule above.~~ **SHIPPED**
     (`882586a`). Goals follow composer (disjoint): infantry 12, mech 4,
     drone 1. Actions — **lean-engine choice** (user, 2026-05-28): postures
     stay infantry-owned; only genuinely-shared/framework-consumed actions
     are built-ins. `decision/goap/action/` built-ins (4): `BreakContact`
     (cross-slice) + `ClearZone`/`EnterZone`/`HoldZone` (constructed by the
     framework `ZoneQueries`). infantry 13 (postures + mission actions),
     mech 4, drone 1. **`ai/` removed entirely** — the technical-layer
     package no longer exists; the dissolve is done.
7. **`weapons/` slice split** — `Marine*` → `infantry/`, `Mech*` → `mech/`
   (combat-shared half already moved in slice 1).
8. **`entity/` rename** — `unit/` → `entity/`. Largest single reference
   surface; do on a quiet base.
9. **`sim/`/`setup/` tidy** — `BattleSetup`/`DefenderRoster` → `setup/`;
   confirm `Pending*` plumbing stays in `sim/`.
10. **tail tidy** — fold `equipment/` into `infantry/`; settle `flyby/`
    (standalone vs `ui/flyby/`); `LosCache` placement (`profile/` vs `nav/`).

## Sequencing

**Execute after [`drop-sim-facade-delegators`](../ecs-migration/stories/drop-sim-facade-delegators.md)
lands.** Both are large mechanical churns over the same shared working
tree (the facade-drop touches ~141 files; this reorg rewrites import
paths across nearly the whole `battle/` tree). Stacking them concurrently
multiplies merge-conflict surface for the parallel sessions sharing this
tree. The facade-drop also changes *who depends on what* (consumers →
services directly), which should settle before we relocate those services
into new packages — otherwise we move targets twice.

## Open items / wrinkles

- **`entity/` rename is optional.** Biggest reference churn of any slice;
  if the cost outweighs the clarity win, keep `unit/`. Flagged, not forced.
- **`flyby/`** is a hybrid (presentation + light sim that queues
  detonations). Kept standalone as a feature domain; could nest under
  `ui/` if it stays presentation-only.
- **`LosCache`** is a line-of-sight cache, not a profiler — it sits in
  `profile/` today. Candidate to move to `nav/` or `decision/`.
- **`DistrictTheme` (`map/`) vs `MapDistrictTheme` (`mapgen/`)** — possible
  duplication to consolidate during the `world/` slice.
- **`BattleSetup` decomposition** (1593 lines, mixes mission-specific
  wiring) is out of scope here — `setup/` just relocates it; splitting it
  is separate work.
- **FOLLOW-UP (slice 6c): framework→feature edges in `decision/`.**
  Slice-6c review caught a real *code* edge (I had wrongly called the slice
  code-clean): built-in `EnterZone` read
  `infantry.GoapInfantryBehavior.REPLAN_PERIOD`. **RESOLVED** (`93ccd49`) by
  hoisting the cadence to `Planner.REPLAN_PERIOD`. Built-in actions are now
  free of feature code deps. **Still open (pre-existing, out of reorg scope):**
  the `decision/` *dispatch/wiring* classes import feature behaviors —
  `UnitUpdateSystem` maps roles → behavior `INSTANCE`s
  (`infantry.CombatantBehavior`, `drone.DroneHubBehavior`, …),
  `TacticalScoring` → `drone.DroneHubUnit`, `WorldStateBuilder` →
  `infantry.InfantryCohesion`. These are inherent to the current
  role→behavior dispatch; removing them is a separate design question
  (registry-style dispatch, or relocating the dispatcher out of the
  framework core). Not blocking; deferred.

## Future direction: a thin core outside the feature packages

Not scheduled — a thing to think about once the reorg + `ecs-migration`
settle. Slice 6's `decision/` work exposed two distinct "shared core vs
feature" axes worth a deliberate design pass:

1. **Dispatch inversion (mechanism must not import behavior).** Today the
   core dispatch/wiring — `UnitUpdateSystem`'s role→behavior `switch`,
   plus `TacticalScoring` / `WorldStateBuilder` reaching into specific
   behaviors — imports concrete feature classes, a framework→feature edge
   inherent to the current design (see Open items). The clean shape is a
   **role/behavior registry the core owns and each feature registers into at
   startup**, so `decision/` never names `infantry.CombatantBehavior` et al.
   This converges with [`ecs-migration`](../ecs-migration/overview.md): the
   `switch(role)` dispatcher is the "entity for-loop" that becomes a set of
   Systems registered on the sim. **Prefer letting the ecs-migration subsume
   this** over a standalone pass — and note `UnitUpdateSystem` is the hot
   parallel per-unit loop, so indirection there carries a real perf cost.

2. **A shared "ground combatant" base (mech as an infantry variant).** Mech,
   infantry (and future tanks) likely share a combatant core — GOAP composer
   shape, posture vocabulary, squad coordination — with mech specializing
   weapons (`HeavyWeapons`) and kinematics (`GroundBody`). Tempting to extract
   now, but hold to the lean-engine rule adopted in slice 6c: **promote shared
   pieces to a common base only when a second actor genuinely consumes them**,
   not preemptively — otherwise we're guessing the abstraction. The
   `infantry/` ↔ `mech/` split is the right starting point; the third
   duplicated concept is the promote signal.

## Verification per slice

- `gradlew.bat compileJava` clean.
- Full test suite green (the move is import-only; behavior is unchanged,
  so any red is a missed reference).
- Diff is relocation + import rewrites only — no logic edits riding along.
