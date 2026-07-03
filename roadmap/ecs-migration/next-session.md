# Next session — ECS migration handoff

Read [`overview.md`](overview.md) for the arc's framing, [`archetype-storage.md`](archetype-storage.md)
for the live engine rules, and [`complete/`](complete/) for shipped stories.

**This file is the LIVE handoff only.** Per-slice detail, commit hashes, and shipped
narrative live in the story docs + `complete/` — not here. When a slice ships, log it
in its story doc (or move the story to `complete/`) and update the *pointers* below;
don't accrete another status block.

## Where we are (2026-07-01)

**The storage / topology half is DONE. The systems / identity / perf half is OPEN.**
Watch the scope: "done" has always meant *storage*, never the whole migration.

**DONE — storage / engine half** (verified 2026-06-28 by a 9-agent audit):
- A real, game-agnostic archetype engine — `engine.ecs` (`EntityWorld` /
  `ArchetypeTable` / `Column` / `ComponentType` / `Query` / `CommandBuffer`), zero
  game imports, `transmute` beyond the design sketch.
- Every dense per-unit numeric column lives in one `EntityWorld`. `UnitRegistry` is
  **deleted**; `UnitRosterService` owns roster + id-mint + world; `World` is the sole
  by-id facade (no surviving `*ById` adapters).
- Optional capabilities are real archetype presence (SECONDARY_WEAPON / MECH_LOADOUT /
  CRASHING / KINEMATICS / SQUAD), plus MOVEMENT / AI_STATE membership-narrowing.
- The **air-into-world epic shipped** — shuttles AND drones are entities in the one
  world; `ComponentStore<T>` is **deleted**.
- The **convoy-vehicle epic shipped (2026-07-01)** — convoy vehicles are ground-archetype
  entities (`{GROUND_IDENTITY, GROUND_KINEMATICS, VEHICLE_MISSION}` + optional
  `GROUND_TURRET`), reached `ConvoyService`-direct; the `Vehicle` POJO is **deleted**. The
  last non-ECS storage space in the battle tier is closed.

## What's NOT done — the open half (why an ECS is worth building)

1. ~~**The systems are not query-shaped.**~~ **CLOSED at terminus (2026-07-01).** The
   mainline loops still read columns by id, but [`stories/systems-to-columns.md`](stories/systems-to-columns.md)
   § Terminus walks each remaining slice against the code and finds them lateral (they
   *move* hashmap probes, not remove them) or Phase-0-parked (~0.02%/frame). Slice 1
   (occupancy) collected the one genuine win. Reopen only alongside the deliberate
   *identity-collapse* (Entity-handle) epic, where the spatial index goes id-native as a
   byproduct — not on systems-half perf grounds.
2. ~~**`Entity` carries live behavior fields.**~~ **DONE (2026-07-01).** All 8 slices of
   [`stories/entity-field-migration.md`](stories/entity-field-migration.md) shipped;
   `Entity` now holds no mutable per-unit state (id + immutable identity + write-only
   `seed*` inputs). See the "entity-field-migration — DONE" section below.
3. ~~**Convoy ground `Vehicle` never entered the world.**~~ **DONE (2026-07-01).** The
   `Vehicle` POJO is deleted; convoy vehicles are world entities like every other unit.
   See [`complete/vehicle-into-world.md`](complete/vehicle-into-world.md).
4. ~~**Authored-appearance is corpse-only**~~ — **core loop CLOSED (2026-07-01).**
   Live units carry SPRITE authored by `battle.appearance.FacingSystem`; the renderer
   is a pure Query collector. Remaining phases art/scope-gated (backlog item 7).

Perf was **measured** ([`phase0-measurement.md`](phase0-measurement.md)): by-id is ~20×
the access cost of a column-walk, but the absolute saving is ~7.3 µs/tick ≈ 0.02% of a
30 Hz frame at N=200. Verdict: the SoA premise is confirmed in relative terms, so the
systems conversion is **idiom-completion, optional on perf grounds** — do it for the
shape, not the microseconds.

## entity-field-migration — DONE (all 8 slices, 2026-07-01)

Story (kept in `stories/` as the living **Access-model / Service convention**
reference 15 Services cite — not moved to `complete/` despite being shipped):
[`stories/entity-field-migration.md`](stories/entity-field-migration.md).

Shipped: **1** `attackCooldown`→COMBAT · **2** `moveSpeed`→MOVEMENT · **3**
`visionRange`+`airLosRadius`→VISION (+ fog-class rename `VisionService`→`FogOfWarService`)
· **4** `primaryWeapon`→COMBAT (OBJECT) · **5** `squadId`→presence-based SQUAD (`32a00239`)
· **6** `role`→universal ROLE (int ordinal) + `RoleService` (`2cede400`) · **7** the
decision cluster: **7a** `homeCell`→HOME + `HomeService` (`eb676efb`), **7b**
`lastReprioTickIndex`→**not a component** (CAS reprio gate lifted onto `HitResponseSystem`)
(`84d0625c`), **7c** `assignedObjective`+`equipmentDropTarget`→TASK + `TaskService`
(tolerant reads) (`7537de69`) · **8** `deathPoseIdx`→folded into the `DeathEvent`
(`6f528fc8`).

**`Entity` now carries NO mutable per-unit state** — and after identity-collapse Phase A
(2026-07-02) no methods and no readable identity field either. It's the `long` `entityId`
+ immutable `faction`/`type` + write-only `seed*` construction inputs (the human name is
now the write-only `seedName`, mirrored into the `IDENTITY_NAME` column). `rng` dissolved
into `ThreadLocalRandom`; `advanceAlongPath`/`beginBurst` moved to `MovementService`/
`CombatService`; the String `id` moved to the `IDENTITY_NAME` column, read by id via
`IdentityService.name(id)`. Every migrated field's by-id access is Service-direct
([[feedback_world_facade_deprecated]]).

**DONE — convoy `Vehicle` folded into the world** ([`complete/vehicle-into-world.md`](complete/vehicle-into-world.md)):
the live convoy `Vehicle` POJO — the **last non-ECS storage space** in the battle tier — is
**deleted**. Convoy vehicles are now world entities (`{GROUND_IDENTITY, GROUND_KINEMATICS,
VEHICLE_MISSION}` + optional `GROUND_TURRET`), reached by id through `ConvoyService`
(Service-direct, no `World` delegator), mirroring the air template. A pure-data
`VehicleMission` bag carries lifecycle/path state only (the `ShuttleMission` shape — no
identity/kinematics/turret/id); `ConvoyService.spawn` is the factory. All 4 phases shipped
2026-07-01 — chain `321cc047` → `730713d6` → `963d7987` → `1e128ce0` → `88bf85c6` →
`80d2e55d` (4d-1) → `f1ad8753` (4d-2, the deletion) → `35840353` (javadoc sweep); both 4d
critiques cleared clean. Full per-phase record in the story doc. `MapVehicle` (static
render-only decoration) was explicitly out of scope.

**Follow-up (separate epic, NOT this one):** statelessify `VehicleController` into components
+ a system — air has no per-craft controller (stateless `AirSteeringSystem` over `AirBody`);
4d kept the controller stateful, only swapping its `Vehicle` back-ref for
`VehicleMission`/`GroundBody`/`VehicleType` refs.

### Access model (in force for every new slice)

Each migrated field's by-id access lands on a **per-component Service** (data owner:
`CombatService` owns COMBAT, `MovementService` MOVEMENT, `VisionService` VISION,
`SquadService` SQUAD, …), constructor-injected or reached via `sim.<svc>()` /
`roster.<svc>()`. **Do NOT grow the `World` god-facade** — since slice 3 the precedent
is Service-direct (no new `World.<field>` delegator). Full rationale in the story §
"Access model". Systems = stateless per-tick processors; Services = data owners
([[battle_services_systems]], [[feedback_components_by_capability_not_store]]).

### Component class convention (locked 2026-06-03)

Data components are `XxxComponent` in a per-domain `components` subpackage
(`battle.<domain>.components`); `ComponentType` infra + processing systems stay put.
Full rule in [`component-model.md`](component-model.md#component-class-convention-locked-2026-06-03).

## Backlog by leverage

Full designs in the linked stories. Struck-through items are shipped/decided.

1. ~~**Convert the combatant hot loop to `Query` + column-array iteration**~~ — **CLOSED
   at terminus (2026-07-01).** [`stories/systems-to-columns.md`](stories/systems-to-columns.md)
   § Terminus: Slice 1 (`NavigationService.rebuildOccupancyMap` column-walks
   `BattleComponents.gridOccupants`) collected the win; the rest is lateral or
   Phase-0-parked (~0.02%/frame). Reopen only with the identity-collapse epic.
2. ~~Measure it (TickProfile A/B at N=200)~~ — **DONE** ([`phase0-measurement.md`](phase0-measurement.md)).
3. ~~**Migrate the behavior-tier `Entity` fields onto components**~~ — **DONE
   (2026-07-01):** all 8 slices shipped; `Entity` carries no mutable per-unit state.
4. ~~**Fold convoy `Vehicle` into the world as a ground archetype**~~ — **DONE
   (2026-07-01).** `Vehicle.java` deleted; convoy vehicles are world entities reached by id
   via `ConvoyService`. (`MapVehicle` stayed out of scope — render-only decoration.)
   [`complete/vehicle-into-world.md`](complete/vehicle-into-world.md).
5. ~~Decide `CommandBuffer`'s fate~~ — **DECIDED (keep):** committed engine infra;
   the systems-half epic is its consumer.
6. ~~Combatant-narrow COMBAT membership~~ — **SHIPPED (`74c565d1`):** "has COMBAT" now
   defines a combatant.
7. **Live authored-appearance** — **core loop CLOSED (2026-07-01): Phases 1+2 SHIPPED**
   (`9f1c33f0`+`ee215e14` author, `9bd3c7fa` read). Live sheet-drawn units carry `SPRITE`
   authored per-tick by `battle.appearance.FacingSystem`; `UnitRenderService.sweepLiveSprites`
   is a pure `liveSprites` `Query` collector (dense-roster walk + all renderer derivation
   deleted; single-walk convergence rejected — sweep order is paint order). Remaining phases
   are **gated, not next-up**: Phase 3 walk-cycle `ANIMATION` needs art; Phase 4 =
   secondary-aim-target bug-fix + FX-child-entities (scope on its own when reached).
   [`stories/live-appearance.md`](stories/live-appearance.md).
   [[feedback_appearance_authored_component]], [[feedback_compose_effects_not_carrier]].
8. ~~**FiringSystem**~~ — **EXTRACTION COMPLETE (2026-07-01):** proving slice
   `c07a11ef`+`426f21db`, sweep `b418d835`. Every infantry-family fire site authors a
   consume-once fire-intent on COMBAT; `battle.combat.FiringSystem` (serial FIRING phase,
   combat effects deferred to the APPLY_DAMAGE flush) is the sole executor; the three
   double-tick decrements are deleted (garrison/patrol cadence drops to the intended
   `attackCooldown` spacing — they were firing ~2× fast). Turret/drone/mech stayed with
   their single owners by design. **Open on the story:** playtest verification + optional
   Phase 3 stance normalization (deliberate behavior change).
   [`stories/firing-system.md`](stories/firing-system.md).
9. **Identity-collapse (`Entity` handle → bare `long` id)** — **ACTIVE (2026-07-01).** Full
   design + recon in [`stories/identity-collapse.md`](stories/identity-collapse.md). Scope
   DECIDED: value-first — do Phase A (side-quests) → B (subclass componentization, the value) →
   C (spawn-spec) this arc; **Phase D (the ~150–200-file bare-`long` sweep) is committed but
   deferred to a follow-up session** (endgame is still `entity = long` everywhere; the spatial
   index goes id-native there and reopens systems-to-columns). **B1 SHIPPED** — the `DroneHubUnit`
   subclass is deleted (live state → `HUB_STATE` component + `HubStateService`; `demolished` → a
   `HubDemolitionSystem` side-table; `Drone.homeHub` → `homeHubId`; config/ctor → a `DroneHub`
   factory; all `instanceof DroneHubUnit` → `UnitType.isDroneHub()`). **B2 SHIPPED** — the
   `MapTurret` subclass is dissolved (live state → `TURRET_STATE` component + `TurretStateService`;
   the burst triplet stays a self-contained turret-only burst in `TURRET_STATE` — **not** folded
   onto the COMBAT burst columns, since `InfantryWeapons.tick`'s burst-continuation pass would
   double-process a turret burst through the wrong (infantry) firing pipeline if it did; `demolished`
   → a `TurretDemolitionSystem` side-table; config/ctor → a `MapTurret.create` factory; all
   `instanceof MapTurret`/`(MapTurret)` → `UnitType.isTurret()` or a `TURRET_STATE` read). **B3
   SHIPPED** — the `Drone` subclass is dissolved (live patrol/pursuit vectors + `homeHubId` →
   `DRONE_STATE` component id 29 + `DroneStateService`; `Drone` rewritten into a factory keeping its
   tuning constants; `DroneSwarmAction`'s `execute` + 7 helpers rewritten to `(Entity + droneState)`
   byte-identically; patrol goals seed to `Float.NaN`; all `instanceof Drone` → `UnitType.isDrone()`).
   **PHASE B COMPLETE** (B1 `a4180ef0` → B2 `2d9eb894` → B3 `38764ca7`) — no `Entity` subclasses
   remain, no live per-instance state outside components, no state-reach `instanceof`.
   **PHASE A COMPLETE (2026-07-02)** — `rng` → `ThreadLocalRandom` (`4e6238c0`); `advanceAlongPath`/
   `beginBurst` → `MovementService`/`CombatService` (`ead4ec0d`); String `id` → `IDENTITY_NAME` column
   + new `IdentityService`, UI selection key → `entityId` (`e0240ac6`). The base `Entity` now holds
   only `entityId` + immutable `faction`/`type` + write-only `seed*` (incl. `seedName`): no methods,
   no readable identity field — everything a `long` + an `IDENTITY` read can serve.
   **PHASE C COMPLETE (2026-07-02; user scope = "Full — build it right").** **C1** deboard-loadout
   dedupe (`b7884353`) · **C2** `EntitySpec` + `spawn(spec)` + factories-return-specs (`9ea2a95a`) ·
   **C3** test-spawn migration (`e4e45833`, 45 files) · **C4** the seed-deletion finale, this session:
   **C4.1** `mintSquad(Faction,UnitType)` dual overload (`6fa06ca1`) · **C4.2** production construction →
   `EntitySpec` (`0a03f604`) · **C4.3** the remaining 24 raw-path test files → spec (`fe86d529`) · **C4.4**
   delete `Entity.seed*`/5-arg-ctor/`toEntity`/roster `addUnit`+`allocate`+`queueSpawn(Entity)`; roster
   goes spec-native (`adopt(Entity,EntitySpec)` seeds the columns); `Entity` = `{entityId, faction, type,
   NO_SQUAD, idOf}` (`50d92c8d`; −470 net lines, suite green). All construction now flows through
   `EntitySpec` + `spawn(spec)`. Full record: [`stories/identity-collapse.md`](stories/identity-collapse.md) § Phase C.
   **PHASE D IN PROGRESS (2026-07-02) — the bare-`long` sweep.** Strategy = **params-first,
   returns+storage-finale** (flip `Entity` params → `long` reading by-id, callers pass `.entityId`;
   `Entity`-returning query/resolve methods + roster `Entity[]` storage + spatial indexes + `DeathEvent`
   + `Entity` deletion are the finale). Recon: 1484 `Entity` refs / 210 files. **D0+D1 SHIPPED
   (`240df7f9`, suite green):** D0 infra (`IdentityService.type/faction(id)` + `BattleView.identity()`);
   D1 the combat damage pipeline internals → `long` (queue `Entity[]`→`long[]`, appliers + `resolve` +
   death cascade by-id, `deathSink`→`LongConsumer`, `isHardened(UnitType)`; public front-doors keep
   `Entity` to sever caller ripple). **D2 SHIPPED (`a782ad71`, suite green, 869 tests):** every `Entity`
   param in `TacticalScoring` → `long` (by-id reads; Entity-returning queries still return `Entity`;
   nullable → `0L`; `TurretAim.State.excludeFromCrowding` field → `long`; ~26 callers + test pass
   `.entityId`). **D3 SHIPPED (`14a6d774`, suite green, 869 tests):** the two already-id-native facade
   methods `BattleView.targetOf(long)` (still returns `Entity`) + `BattleControl.advanceMovement(long)`;
   ~48 callers pass `.entityId` (3 parallel Sonnet passes over disjoint clusters). **D4 SHIPPED
   (`d6b61af2`, 869 green):** the D1-deferred `DamageService` `applyDamage`/`applyReprio`/`applyFallback`
   + `HitResponseSystem` `rollFallbackOnHit`/`rollReprioritizeOnHit` front-doors → `long` (nullable
   `shooter`→`0L`; kept `applyOccupancyDelta(Entity)` = dest-index applier). **D5 SHIPPED (`51d6f1c`,
   869 green):** the weapon-fire leaf methods `InfantryWeapons.fireShot`/`fireSecondary` +
   `HeavyWeapons.fireMechWeapon` (×2) → `long` (`shooter.type`/`faction` cached via `roster.identity()`;
   tick continuations + facade wrappers pass `.entityId`). **D6 SHIPPED (`da6c022c`, 869 green):** the
   sim-facade `BattleControl.fireShot`/`fireSecondary`/`fireMechWeapon` + the
   `BattleSimulation.applyDamage(Entity)` external front-door → `long` — impls delegate the id straight
   through (a pure 60/60 passthrough); 5 production `fire*` callers + 34 `applyDamage` test sites pass
   `.entityId`. **SEQUENCING CORRECTION:** the behaviors share one atomic `Action.execute(Entity)`
   interface, gated bottom-up; sequential cascade, only the caller ripple fans out. **D7 SHIPPED
   (`c296b13b`, 869 green):** the storage-core step — `UnitDestinationSpatialIndex` went id-native (new
   `LongBucket` primitive-long store replacing `ArrayList<Entity>` buckets; `gather` now skips released
   ids, since a released id's by-id reads are unsafe under slot reuse), unblocking the dest-index gate:
   `DamageService.OccupancyApplier`/`applyOccupancyDelta` + `NavigationService` `setPath`/`clearPath`/
   `applyOccupancyDeltaInline` internals → `long`. The `BattleSimulation.setPath(Entity)`/`clearPath(Entity)`
   facade KEPT `Entity` (delegates `.entityId`), so the ~30 behavior callers don't move yet. Reopens
   `systems-to-columns` (the id-native spatial-index work). **D8 SHIPPED (`404bb3c5`, 869 green):** the
   facade `BattleControl.setPath`/`clearPath` params → `long` — 44 behavior + test callers across ~27 files
   pass `.entityId` (2 parallel Sonnet passes); `writeFallbackInline` dropped its `getOrNull` backward
   resolve; `EquipmentDropSystem`/`SquadFallbackSystem` path-clearer `Consumer<Entity>` → `LongConsumer`.
   **D9 SHIPPED (`68838b84`, 869 green):** the atomic `Action.execute(Entity member→long)` flip — the interface +
   all 24 implementors + `AbstractZoneAction` + every private helper that received `member`, in ONE commit.
   Mostly mechanical (`Entity member`→`long member`, `member.entityId`→`member`); the non-mechanical spots
   were `DroneSwarmAction` (`member.type`/`faction`→`identity()`, `indexOf`→`entityId` scan), the shared
   `PatrolMotion` helper class, the adjacent `u`/`self`-named cluster (`MechCombatantBehavior.tryFire*` acting-unit
   `u`→`long` with `target` kept `Entity`, `EngageAtCurrentBand`, `MechBreakContact`, `InfantryCohesion`,
   `ClearZone`/`HoldZone` pick-target helpers), and a new `SquadPlan.Step.slotOf(long)` overload (roles/slots
   stay `Entity`). 47 files; 6 dead `Entity` imports dropped. **Next: `mintSquad` (nullable leader→`0L`) →
   air/vehicle/ui → the storage finale.** Sister `UnitSpatialIndex` + roster `Entity[]` stay Entity — the
   **storage finale**, its own dedicated closing session (roster `Entity[]`→`long[]`, sister index
   id-native, Entity-returning queries → `long`, `DeathEvent` → `long`, **delete `Entity`**): story doc
   § "Storage finale". Full record + corrected sequence: the story doc § Phase D.
10. **Statelessify `VehicleController`** — turn the stateful per-vehicle controller (the last
    per-craft handle with mutable motion state) into components + a stateless system, the air
    `AirSteeringSystem`-over-`AirBody` shape. Self-contained follow-up from vehicle-into-world;
    low leverage (N≈1–4). Candidate.

## Recent ECS-track commits

```
30f7ced8 ecs-migration: identity-collapse F3a - resolveUnit -> long
a671b0c0 ecs-migration: identity-collapse F2 - the target web -> long
5eb8dff0 ecs-migration: identity-collapse F1 - DeathEvent payload Entity -> long
1a44f09a ecs-migration: identity-collapse D12 - remaining scattered acting-unit params -> long
72dfcdfb ecs-migration: identity-collapse D11 - UnitBehavior.update(Entity u) -> long
739fd228 ecs-migration: identity-collapse D10 - mintSquad(Faction, Entity leader) -> long
68838b84 ecs-migration: identity-collapse D9 - atomic Action.execute(member) -> long
404bb3c5 ecs-migration: identity-collapse D8 - facade setPath/clearPath -> long
c296b13b ecs-migration: identity-collapse D7 - dest-index id-native; NavigationService setPath/clearPath internals -> long
da6c022c ecs-migration: identity-collapse D6 - facade fire* + applyDamage front-door -> long
51d6f1c  ecs-migration: identity-collapse D5 - weapon-fire methods -> long
d6b61af2 ecs-migration: identity-collapse D4 - DamageService/HitResponse front-doors -> long
14a6d774 ecs-migration: identity-collapse D3 - sim-facade targetOf/advanceMovement params -> long
a782ad71 ecs-migration: identity-collapse D2 - TacticalScoring params Entity->long
240df7f9 ecs-migration: identity-collapse D0+D1 - IdentityService type/faction(id); combat pipeline -> long
50d92c8d ecs-migration: identity-collapse C4.4 - delete seed*, Entity = {entityId,faction,type}
fe86d529 ecs-migration: identity-collapse C4.3 - test construction -> EntitySpec
0a03f604 ecs-migration: identity-collapse C4.2 - production construction -> EntitySpec
6fa06ca1 ecs-migration: identity-collapse C4.1 - add mintSquad(Faction,UnitType) overload
e4e45833 ecs-migration: identity-collapse C3 - migrate test spawns to sim.spawn(EntitySpec)
26b0d0e4 ecs-migration: identity-collapse Phase C docs - C1/C2 shipped, C3/C4 plan
9ea2a95a ecs-migration: identity-collapse C2 - EntitySpec + spawn(spec); factories return specs
b7884353 ecs-migration: identity-collapse C1 - dedupe deboard loadout into MarineLoadout.seedInto
e0240ac6 ecs-migration: identity-collapse A - String id into IDENTITY name column
ead4ec0d ecs-migration: identity-collapse A - rehome Entity base methods to services
4e6238c0 ecs-migration: identity-collapse A1 - dissolve Entity.rng into ThreadLocalRandom
38764ca7 ecs-migration: identity-collapse B3 - dissolve Drone into DRONE_STATE; Phase B COMPLETE
```
(Doc hash-fill + critique micro-commits are elided from this window.)

Older history is in git + the `complete/` docs. Sibling tracks (battle-render,
goap, campaign) interleave on HEAD.

## Sanity check before resuming

- `gradlew.bat compileJava` clean, full suite green (`:test --rerun` BUILD SUCCESSFUL at
  the FiringSystem sweep, 862 tests). `Vehicle.java` is **gone**: convoy vehicles are
  world entities; mission state is `VehicleMission` in `VEHICLE_MISSION`, reached via
  `convoy.mission(id)`; identity/kinematics/turret are their own columns read by id.
- `git log --oneline -5` shows `404bb3c5` (identity-collapse D8) or your own recent work at the top.
- **identity-collapse Phases A + B + C COMPLETE (2026-07-02)** — `Entity` is now a bare handle:
  `{entityId, faction, type, NO_SQUAD, idOf}`. No subclasses, no live/seed state, no ctor beyond
  `Entity(faction, type)`; all construction flows through `EntitySpec` + `UnitRosterService.spawn(spec)`
  (the private `adopt(Entity,EntitySpec)` seeds the world columns). Phase C4 (the seed-deletion finale)
  shipped C4.1–C4.4 (`6fa06ca1`→`50d92c8d`), suite green + critique-reviewed.
- **identity-collapse Phase D IN PROGRESS (2026-07-02)** — the bare-`long` handle sweep (roster
  `Entity[]`→`long[]`; the resolve layer + every `Entity` param → `long`; spatial indexes go id-native,
  reopening `systems-to-columns`; `Entity` deleted). ~150–200 files (1484 refs / 210 files), mechanical.
  Strategy = **params-first, returns+storage-finale** (see the story doc § Phase D). **D0+D1 SHIPPED
  (`240df7f9`, suite green):** D0 infra (`IdentityService.type/faction(id)`, `BattleView.identity()`);
  D1 combat damage pipeline internals → `long` (public front-doors keep `Entity`). **D2 SHIPPED
  (`a782ad71`, 869 tests green):** every `TacticalScoring` `Entity` param → `long`. **D3 SHIPPED
  (`14a6d774`, 869 green):** sim-facade `targetOf(long)` + `advanceMovement(long)`. **D4 SHIPPED
  (`d6b61af2`, 869 green):** `DamageService`/`HitResponseSystem` front-doors → `long`. **D5 SHIPPED
  (`51d6f1c`, 869 green):** the weapon-fire leaf methods (`InfantryWeapons` fireShot/fireSecondary,
  `HeavyWeapons` fireMechWeapon ×2) → `long`. **D6 SHIPPED (`da6c022c`, 869 green):** the sim-facade
  `BattleControl` fireShot/fireSecondary/fireMechWeapon + the `BattleSimulation.applyDamage(Entity)`
  external front-door → `long` (pure passthrough; 5 prod callers + 34 test sites pass `.entityId`).
  **D7 SHIPPED (`c296b13b`, 869 green):** the storage-core step — `UnitDestinationSpatialIndex` id-native
  (new `LongBucket` primitive-long store; `gather` skips released ids), unblocking the dest-index gate:
  `DamageService.OccupancyApplier`/`applyOccupancyDelta` + `NavigationService` setPath/clearPath internals
  → `long`. The `BattleSimulation.setPath(Entity)`/`clearPath(Entity)` facade KEPT Entity (delegates
  `.entityId`), so the ~30 behavior callers don't move yet. Reopens `systems-to-columns`. **D8 SHIPPED
  (`404bb3c5`, 869 green):** the facade `BattleControl.setPath`/`clearPath` params → `long` (44 behavior +
  test callers across ~27 files pass `.entityId` via 2 parallel Sonnet passes; `writeFallbackInline`
  dropped its `getOrNull` backward resolve; `EquipmentDropSystem`/`SquadFallbackSystem` path-clearer →
  `LongConsumer`). **SEQUENCING CORRECTION:** the behaviors share one atomic `Action.execute(Entity)`
  interface, gated bottom-up — a sequential cascade, only the caller ripple fans out. **Next: the atomic
  `Action.execute(Entity→long)` flip** (interface + ~30 implementors + dispatcher + test anon impls, one
  commit — every implementor shares the signature). Sister `UnitSpatialIndex` + roster `Entity[]` stay
  Entity — the **storage finale** (its own dedicated closing session; story doc § "Storage finale").
  [`stories/identity-collapse.md`](stories/identity-collapse.md).
- **live authored-appearance: core loop CLOSED 2026-07-01** — Phases 1+2 shipped
  (`9f1c33f0` + `ee215e14` critique fixes + `9bd3c7fa`; suite 843 green). Remaining phases
  are art/scope-gated (Phase 3 needs walk-cycle sheets; Phase 4 scopes on its own), so the
  epic is **parked, not active**.
- **FiringSystem: EXTRACTION COMPLETE 2026-07-01** — proving slice `c07a11ef` +
  critique fixes `426f21db`, sweep `b418d835` (all 11 remaining sites + KitRetriever
  author intent; three double-tick decrements deleted; suite 862 green). Full record:
  [`stories/firing-system.md`](stories/firing-system.md). **Open before closing the
  epic:** (a) **playtest** — garrison/patrol/guard-post fire cadence drops to the
  intended `attackCooldown` spacing (the double-tick bug had them firing ~2× fast) and
  the overall intent-flip feel; (b) optional Phase 3 stance normalization via
  `FireStance.stanceFor(moveProgress)` — a deliberate behavior change, its own slice +
  playtest. **Next-up:** **identity-collapse — the STORAGE FINALE, IN PROGRESS** (§ "Storage finale — Execution"
  in the story doc). Running as green-per-commit slices F1–F5: **F1** (`DeathEvent`→`long`, `5eb8dff0`)
  + **F2** (the target web→`long`, `a671b0c0`) + **F3a** (`resolveUnit`→`long`, `30f7ced8`) SHIPPED, suite
  green (869); **F3b** (`liveUnitAt`→`long`, ~50 roster-walk consumers w/ `.faction`/`.type` reads) is
  next, then **F4** (`UnitSpatialIndex` id-native), **F5** (roster storage `Entity[]`→`long[]` + spawn→long
  + delete `Entity.java`). **The params-first acting-unit sweep is DONE (D9→D12).** Every `Entity` *param* outside storage-finale
  scope is now `long`: the sim facade (D10 — `BattleControl`/`BattleView` take `long` on every front-door),
  the GOAP `Action.execute` (D9), the `UnitBehavior.update` dispatch entry (D11), and the scattered subsystem
  acting-unit params (D12). The only `Entity` that remains is **finale-coupled**: `target`/`candidate`/`threat`
  params fed from `Entity`-returning queries (`targetOf`/`findBestTarget`/`liveUnitAt` — flip *with* the
  returns), the `combathybrid/bridge` presentation layer (iterates `getDeathsThisFrame()`/`targetable`),
  roster/spatial storage, `DeathEvent`, and the `Entity.idOf`/`slotOf(Entity)`/`adopt`/`PendingSpawn` seams.
  So the **storage finale** is the whole remaining tail as one coherent pass: roster `Entity[]`→`long[]`,
  sister `UnitSpatialIndex` id-native (`LongBucket`, D7-proven), Entity-returning queries → `long`, `DeathEvent`
  → `long`, rehome `Entity.idOf`/`NO_SQUAD`, **delete `Entity.java`**. **Follow-up:** `NavigationService.pathDestX`/
  `pathDestY` are dead (zero callers) — delete in the finale. D0+D1 `240df7f9`; D2 `a782ad71`; D3 `14a6d774`;
  D4 `d6b61af2`; D5 `51d6f1c`; D6 `da6c022c`; D7 `c296b13b`; D8 `404bb3c5`; D9 `68838b84`; D10 `739fd228`;
  D11 `72dfcdfb`; D12 `1a44f09a`. Other candidate: **statelessify `VehicleController`** (item 10).
- Working model note (2026-07-01): implementation delegated to Sonnet 5 subagents from
  prescriptive specs; planning/review/suite/commit on the main thread.
