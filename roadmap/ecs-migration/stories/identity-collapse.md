# Identity-collapse — dissolve the `Entity` handle into a bare `long` id

> **Status: ACTIVE (2026-07-01). Recon COMPLETE (3 parallel agents — full tabulations in
> session). Scope DECIDED: value-first sequencing — do A → B (+ C) this arc; **Phase D
> (the bare-`long` sweep) is committed, not optional — deferred to a follow-up session.**
> The endgame is still `entity = long` everywhere. B1 (DroneHubUnit → `HUB_STATE`) and
> B1/B2/B3 all SHIPPED — **Phase B COMPLETE** (no `Entity` subclasses left). **Phase A COMPLETE
> (2026-07-02)** — `rng`→`ThreadLocalRandom` (`4e6238c0`), base methods→Services (`ead4ec0d`),
> String `id`→`IDENTITY_NAME` column + `IdentityService` (`e0240ac6`). **Next: Phase C**
> (spawn-spec); Phase D (bare-`long` sweep) is the committed follow-up.**
> The last open ECS-migration epic that touches the identity layer: turn `Entity` from a
> ~305-line heap object held in the roster's `Entity[]` into a bare `long` id, so
> `entity = id` is literally true everywhere. The spatial index goes id-native as a
> byproduct, which reopens [`systems-to-columns`](systems-to-columns.md) (closed at its
> Phase-0 terminus). Backlog item 9 in [`../next-session.md`](../next-session.md).

## Where `Entity` was (pre-Phase-A baseline)

> **Phase A (2026-07-02) dissolved everything in this section except `entityId` +
> `faction`/`type` + the `seed*` inputs — see the header.** `rng` → `ThreadLocalRandom`;
> the two methods → `MovementService`/`CombatService`; the String `id` → the `seedName` seed
> mirrored into the `IDENTITY_NAME` column (read by id via `IdentityService.name`). Kept below
> as the epic's starting point.

The [`entity-field-migration`](entity-field-migration.md) already hollowed the **base**
`Entity`: at the start of this epic it carried only

- `entityId` (the `long` — the identity),
- immutable identity: `id` (String, human-readable name), `faction`, `type` (`UnitType`),
  `rng` (`java.util.Random`),
- write-only `seed*` construction inputs (consumed once by `UnitRosterService.allocate`),
- two methods: `advanceAlongPath(World, float)` and `beginBurst(CombatService, Entity)`.

`faction` and `type` are **already mirrored** into the world's `IDENTITY` component
(`IDENTITY_FACTION` / `IDENTITY_TYPE`, readable by id). So a base-`Entity` ref existed
almost entirely to (a) name the type in a method signature, (b) reach `.entityId`, (c) read
the String `id` or `rng`, or (d) call the two methods.

**The mass isn't the base — it's the three subclasses.** `Drone`, `MapTurret`, and
`DroneHubUnit` still carry **live per-instance state that was never componentized**:

| Subclass | Live fields (non-`static final`) | Owner subsystem(s) |
|---|---|---|
| `DroneHubUnit` | `demolished`, `spawnCooldown`, `dronesLaunched`, `droneSquad` | `DroneHubBehavior` (cadence), `DroneSpawner` (launch bookkeeping + squad link), `HubDemolitionSystem` (`demolished`) |
| `MapTurret` | `facingDegrees`, `recoilTimer`, `demolished`, `kind` (final), **`burstRemaining`/`burstTimer`/`burstTargetId`** | `TurretBehavior` (all writes), `UnitRenderService` (reads facing/recoil/kind), `TurretDemolitionSystem` (`demolished`) |
| `Drone` | `patrolGoalX/Y`, `pursuitGoalX/Y`, `pursuitTimer`, `homeHub` (final ref) | `DroneSwarmAction` (all patrol/pursuit; threads a `Drone d` through 7 helpers); `homeHub` read by `DroneHubBehavior`/`HubDemolitionSystem` |

You can't subclass a `long`, so these live fields must become world components (or side-tables)
and every `instanceof`/state-cast must be erased **before** the handle can collapse. That is
the substance of this epic; the base-handle sweep is downstream plumbing.

## Recon findings (magnitudes)

- **Base-handle sweep is overwhelmingly mechanical.** `\bEntity\b` = ~1,504 refs / 206 files;
  realistic must-edit ~150–200 files. Weight: **240** `Entity` params (83 files) + **125**
  `Entity`-typed collections (43 files) + ~30 `Entity`-returning query methods, concentrated in
  ~20–30 heavyweight files (`TacticalScoring`, the `BattleSimulation`/`BattleView`/`BattleControl`
  facade, `UnitRosterService` + the two spatial indexes, `DamageService`/`DamageResolver`/weapons)
  plus a long tail of ~40 tiny infantry/GOAP/mech behavior classes each carrying one or two
  `Entity self`/`Entity target` params. Pure find-and-replace to `long`.
- **The resolve/write chokepoints are already clean.** `Entity.idOf(...)` (~44 sites) is the
  ref→id write seam and survives. `getOrNull(long)` / `resolveUnit(long)` / `targetOf(Entity)` (32) /
  `findBestTarget(...)` (50) / `DeathEvent.unit()` (6) are the id→ref resolve layer — they flip to
  returning/taking `long` and cascade the type change outward.
- **`rng` — ~16 sites / 7 files.** All in the parallel decide phase (hit rolls, shot scatter,
  flinch/fallback chance, flee wander, patrol jitter, drone swarm). Seeded from system time — **no
  battle reproducibility is required** (per the field's own javadoc).
- **String `id` — ~18 sites**, almost all debug-dump / logging / test assertions. **One
  load-bearing use**: the UI selection key (`WorldPicker` writes it, `SquadStateDumper` matches on
  it). No id-format construction, no `Map` keyed by it.
- **Spawn is funneled in production** (8 `new` sites / 5 files; adoption through
  `addUnit`/`queueSpawn`/`allocate`); **air + vehicle already allocate from an archetype spec**
  (`allocateAir`/`allocateVehicle`) — a working template for the pattern. The deboard loadout
  (~10-seed sequence) is **duplicated verbatim** in `AirSystem` and `GroundSystem` — a dedupe
  the spec collapses for free.
- **Tests are the churn tax.** 238 hand-rolled `new Entity(...)` / 58 files; 245 `sim.addUnit(...)` /
  47 files; **no shared construct+register helper** (`TestUnits` does kill/snapshot only). Shape is
  uniform-trivial (`new Entity(id, faction, type, x, y)` + at most one `seedSquadId`/`seedRole`),
  so a single spec-builder + a thin shared `spawn(sim, …)` helper could absorb nearly all of it —
  but introducing one is net-new work touching ~55 test files.

## Resolved sub-decisions (small; not forks worth a meeting)

- **`rng` → thread-local `Random`.** The field exists solely to avoid `Random` contention in the
  fork-join decide phase; a per-worker thread-local `Random` gives every worker its own stream with
  zero per-unit storage and zero contention. The only behavioral nuance — a unit's rolls come from
  the *processing thread's* stream rather than a unit-owned stream — is irrelevant given no
  reproducibility requirement. (Alternative considered: a `Random` side-table keyed by id — more
  storage for no benefit here. Rejected.)
- **String `id` → an `IDENTITY` name column + selection-key on `entityId`.** Add an `IDENTITY`
  OBJECT column carrying the human-readable name (seeded from the ctor String id), read by id where
  debug dumps / logs / tests want it — this preserves greppable ids. The one load-bearing use, the
  UI selection key, moves to `entityId` (the `long`), which is the correct stable identity anyway.
- **The two base methods move to their owning services:** `advanceAlongPath` → `MovementService`
  (1 caller), `beginBurst` → `CombatService` taking ids (2 callers). Trivial.
- **Subclass live-state style — default to field-decomposition (SoA columns), object-column only
  where justified.** Hot per-tick data (`Drone`'s patrol/pursuit vectors, `MapTurret`'s
  facing/recoil read by the renderer every frame) becomes typed columns on a new component — the
  SoA north star. Cold / occasional state can ride an OBJECT column (the `AirBody`-in-`KINEMATICS`
  precedent) where a column-per-field buys nothing. Decide per-field at slice time; bias to columns.
- **End-state handle type = bare `long`.** Not a value-record wrapper. Matches the backlog framing
  and [[feedback_entity_for_loop_endgame]] / [[feedback_skip_generation_bits]] (monotonic id, no
  generation bits — ABA can't occur, ids are never recycled).

## The decomposition (phases, in dependency order)

**Phase A — identity side-quests + base-method rehoming. ✅ COMPLETE (2026-07-02).**
Shipped as three commits: **A1** `rng` → `ThreadLocalRandom.current()` (per-worker stream, the
`Entity.rng` field deleted; `4e6238c0`); **A-methods** `advanceAlongPath` → `MovementService`,
`beginBurst` → `CombatService(long, long)` (both deleted from `Entity`; `ead4ec0d`); **A2** String
`id` → a new `IDENTITY_NAME` OBJECT column (field 2) + `IdentityService.name(id)` data owner
(`sim.identity()`/`roster.identity()`), the `Entity.id` field becoming the immutable write-only
`seedName`, and the UI selection key moving off the String onto the `long` `entityId` (`e0240ac6`).
The base `Entity` now is `entityId` + `faction`/`type` (already-in-world) + `seed*` (incl.
`seedName`) — nothing a `long` + an `IDENTITY` read can't serve; no methods, no readable identity
field. All three slices: green suite + background critique cleared.

**Phase B — subclass live-state → components (the value).** Cheapest-first, per recon:
- ~~**B1 · `DroneHubUnit`**~~ — **SHIPPED (2026-07-01; Sonnet-implemented, main-thread reviewed;
  full suite green, 864 tests).** The `DroneHubUnit` class is **deleted**. Live state →
  `HUB_STATE` component (`spawnCooldown`/`dronesLaunched`/`droneSquadId`, id 27) + `HubStateService`
  data owner (`sim.hubState()`); `droneSquad` object-ref → a `droneSquadId` INT resolved via
  `getSquad` (`-1`/`NO_SQUAD` sentinel since `0` is a valid squad id). `demolished` → a
  `LongOpenHashSet` side-table in `HubDemolitionSystem` (it's a defensive double-fire guard read
  *after* roster release, so it can't be a live-only column; `isDemolished(id)` exposed for tests).
  `Drone.homeHub` (a `DroneHubUnit` ref) → `homeHubId` (long, `0L` = none; captured eagerly, so
  pass `hub.entityId` only after the hub is registered — see the field javadoc) —
  the recon-recommended hub-first link, touching only `DroneSwarmAction`'s anchor reads in the
  otherwise-B3 drone file. Config + construction → a new non-`Entity` `DroneHub` factory
  (`DroneHub.create(...) → Entity`, `UnitType.DRONE_HUB_STRUCTURE`); `allocate` attaches `HUB_STATE`
  keyed off a new `UnitType.isDroneHub()` predicate, seeded from a transient `Entity.seedHubSpawnCooldown`
  (keeps `allocate` free of any `battle.drone` import — the Phase-C spawn-spec absorbs it). All 4
  type-tag `instanceof DroneHubUnit` (footprint, HP-bar sizing, hub render, `isHardened`) → the
  `type.isDroneHub()` gate. `HUB_STATE` added to the `DeadBodySystem` corpse-remove mask (live-only).
  Tests: `StaticEmplacementMembershipTest` extended (HUB_STATE presence + seed values), the three
  `HubDemolitionSystemTest` cases migrated to `isDemolished(id)` + hub-before-drone allocation
  ordering, new `DroneHubBehaviorTest` (cadence: spawn-cooldown ticks one `TICK_DT`/update, resets
  to `SPAWN_INTERVAL_SEC` after a launch attempt). **This proves the whole Phase-B pattern:** subclass
  live-state → component + service, subclass → factory, state-cast/type-tag `instanceof` → `UnitType`
  predicate, entity-ref field → id.
- ~~**B2 · `MapTurret`**~~ — **SHIPPED (2026-07-01; Sonnet-implemented; full suite green, 866
  tests).** The `MapTurret` class is **rewritten into a non-`Entity` factory** (`MapTurret.create(...)
  → Entity`, `UnitType.TURRET`; keeps the name). Live state → `TURRET_STATE` component (id 28:
  `facingDegrees`/`recoilTimer`/`kind`/`burstRemaining`/`burstTimer`/`burstTargetId`) +
  `TurretStateService` data owner (`sim.turretState()`). **Decision reversed from this story's
  original plan:** the burst triplet did **not** fold onto the COMBAT burst columns — it stays a
  deliberately self-contained turret-only burst inside `TURRET_STATE`, because
  `InfantryWeapons.tick`'s burst-continuation pass gathers every combatant with
  `COMBAT.burstRemaining(id) > 0` and continues via the *infantry* `fireShot` path; a turret fires
  its burst through `TurretBehavior`'s *turret* `fireShotFrom` pipeline (scatter/AoE/raycast per
  `TurretKind`), so writing the COMBAT columns would double-process a turret burst through the wrong
  pipeline. `demolished` → a `LongOpenHashSet` side-table in `TurretDemolitionSystem` (same shape as
  B1's `HubDemolitionSystem`; `isDemolished(id)` exposed for tests). `allocate` attaches
  `TURRET_STATE` keyed off a new `UnitType.isTurret()` predicate, seeded from a transient
  `Entity.seedTurretKind` (an OBJECT seed — same mechanism as B1's `seedHubSpawnCooldown`, just a
  richer payload); `recoilTimer` seeds to `1f` (matches the old subclass's field initializer) so an
  unfired turret doesn't read as mid-recoil. All `instanceof MapTurret`/`(MapTurret)` sites converted:
  `TurretBehavior` (the aim/fire ferry, rewritten to read/write by id via `TurretStateService`),
  `InfantryWeapons`'s shot-event `TurretKind` read, `UnitRenderService` (turret-body sweep + HP-bar
  sizing), `TacticalScoring.isHardened`, `HitResponseSystem.rollReprioritizeOnHit`, `AirSystem`'s AA
  "defense posts only" filter (pure tag, no kind read) — all → `UnitType.isTurret()` or a
  `TURRET_STATE` read. `FootprintCircleShape` (the `@DebugOnly` combat-bridge proxy) has no
  sim/roster reach from its static `footprintCells(Entity)` signature, so a turret there falls back
  to `DEFAULT_FOOTPRINT_CELLS` (a debug-only sizing degradation, noted inline) rather than reading
  the real `TurretKind.visualCells`. `TURRET_STATE` added to the `DeadBodySystem` corpse-remove mask
  (live-only). Tests: `StaticEmplacementMembershipTest` extended (TURRET_STATE presence + seeded
  kind/recoilTimer), `TurretDemolitionSystemTest` migrated to `isDemolished(id)`, new
  `TurretBehaviorTest` (recoil aging with no target; a burst kind latching `burstRemaining`/
  `burstTimer`/`burstTargetId`/facing into `TURRET_STATE` on a same-tick fire).
- ~~**B3 · `Drone`**~~ — **SHIPPED (2026-07-01; Sonnet-implemented, main-thread reviewed; full
  suite green, 870 tests).** The `Drone` class is rewritten in place into a non-`Entity` factory
  (`Drone.create(...) → Entity`, `UnitType.DRONE`) keeping all its tuning constants + `HANDLING`
  (`DroneSwarmAction`/`DroneCrashSystem` reference them). Live state → `DRONE_STATE` component
  (id 29: `patrolGoalX/Y`, `pursuitGoalX/Y`, `pursuitTimer` FLOAT, `homeHubId` LONG) +
  `DroneStateService` (`sim.droneState()`). The B1 `Drone.homeHubId` field folded into `DRONE_STATE`,
  seeded from a new `Entity.seedHomeHubId`. **Load-bearing seed:** `allocate` seeds the patrol/pursuit
  goals to `Float.NaN` (the "no waypoint yet" sentinel `ensureSectorWaypoint` gates on — a fresh row
  appends `0.0`, not NaN; the `AI_STATE -1/-1` cell precedent). `DroneSwarmAction`'s `execute` + 7
  helpers rewritten to `(Entity member)` + a threaded `DroneStateService` (byte-identical control
  flow — a pure storage relocation of the field reads/writes). `instanceof Drone` → `UnitType.isDrone()`
  at every site (`DroneHubBehavior.countActiveDrones`, `HubDemolitionSystem.cascadeKillDrones`,
  `DroneRenderSystem`, `DroneCrashSystem` — the last two confirmed tag-only). `DRONE_STATE` is
  live-only (corpse-remove mask) while KINEMATICS (the AirBody) stays for the crash. Tests:
  `StaticEmplacementMembershipTest` (DRONE_STATE presence + NaN goal seeds + homeHubId), new
  `DroneSwarmActionTest` (patrol-waypoint pick, pursuit-latch decay, non-drone rejection).

**Phase B COMPLETE (2026-07-01):** no `Entity` subclasses remain, no live per-instance state lives
outside a world component, and every state-reach / classification `instanceof` subclass-check is gone
(replaced by `UnitType.isX()` predicates + per-component Services). Remaining epic work: Phase A
(rng/name/base-method side-quests) + Phase C (spawn-spec); Phase D (bare-`long` sweep) stays the
committed follow-up-session deferral.

**Phase C — spawn-spec. IN PROGRESS (2026-07-02).** Scope confirmed by the user: **Full — build it
right** (introduce `EntitySpec`, migrate ALL production + tests, DELETE `Entity`'s `seed*` fields;
no lasting dual representation). Executed in green-at-each-step slices:

- ~~**C1 · deboard dedupe**~~ — **SHIPPED (`b7884353`).** The verbatim-duplicated loadout→seed block
  (`AirSystem`/`GroundSystem` `tryDeboardMarine`) extracted to `MarineLoadout.seedInto(Entity)`. The
  per-host BFS free-cell search stays a deliberate copy. (C4 will change `seedInto`'s param
  `Entity`→`EntitySpec` when the deboard moves to a spec.)
- ~~**C2 · `EntitySpec` + `spawn(spec)` + factories**~~ — **SHIPPED (`9ea2a95a`; suite green).**
  `EntitySpec` (`battle.unit`) = the construction bag mirroring the `seed*` surface (identity + cell +
  optional capability seeds + stat block seeded from `UnitType`), fluent, with a transitional
  `toEntity()` bridge. `BattleSimulation`/`BattleControl` gained `spawn(EntitySpec)→Entity` +
  `queueSpawn(EntitySpec)→Entity` (route through the existing fog-aware `addUnit`/`queueSpawn(Entity)`).
  `Drone`/`MapTurret`/`DroneHub.create` now **return `EntitySpec`**; production callers (`DroneSpawner`,
  `BattleSetup` defense-post turrets/hubs, ambient civilians) go through `spawn(spec)`. Test factory
  callers bridged with `.toEntity()` (33 sites/10 files). `Entity` keeps its `seed*` fields +
  `addUnit(Entity)`/`allocate(Entity)` as the transitional path (tests + `toEntity` still use it).
- **C3 · test migration → `spawn(spec)`** — **NEXT (parallel-Sonnet candidate).** ~219 `new Entity(...)`
  across **59 test files**. Rule: `new Entity(...) [+ .seedX=…] + sim.addUnit/queueSpawn(u)` →
  `sim.spawn(new EntitySpec(...)[.setterX(…)])`, keeping the returned handle where the test uses it.
  Seed→setter map = the `EntitySpec` fluent API (`seedSquadId`→`.squad`, `seedRole`→`.role`,
  `seedPrimaryWeapon`→`.primaryWeapon` [sets the 4 weapon stats], `seedSecondaryWeapon/Ammo`→`.secondary`,
  `seedHomeCellX/Y`→`.home`, `seedAssignedObjective`→`.assignedObjective`, stat seeds→their setters,
  `seedHp`+`seedMaxHp`→`.health`). **Seeds still exist during C3, so untouched sites still compile —
  the safety net.** **LEAVE UNTOUCHED (C4 handles):** `UnitRosterServiceTest` (tests the
  `addUnit(Entity)`/`allocate`/swap-pop roster API directly — its `new Entity` IS the subject),
  `DeathDispatcherTest` (sim-less fixtures via a `unit(name)` helper — no `addUnit`, currently reads
  `e.unit().seedName`), and any `new Entity` NOT followed by `addUnit`/`queueSpawn`. Air/vehicle
  allocation tests use `allocateAir`/`allocateVehicle` (different path) — check case-by-case.
- **C4 · delete `seed*` + squad-site + `mintSquad` refactor** — **AFTER C3 (delicate, main-thread).**
  (a) Refactor `mintSquad(Faction, Entity leader)` → `mintSquad(Faction, UnitType type)` — behavior-
  identical (every caller mints with a not-yet-allocated handle, so `leaderId` is already `0`; `mechSquad`
  comes from the type). (b) Convert the squad-interleaved production sites to spec: deboard ×2
  (`spec` + `seedInto(spec)`, `mintSquad(type)`, `spec.squad(id)`, `addUnitSink` → `Consumer<EntitySpec>`),
  the 3 `BattleSetup` defender clusters, `WalkInMeans`, and `DroneSpawner`'s deferred-drone squad seed
  (queue the spec, set `spec.squad` after mint). (c) Flip `spawn(spec)` to seed the world columns
  **directly** from the spec (a private `adopt(spec)`), delete `toEntity()`. (d) Delete every `Entity`
  `seed*`/`seedName`/`seedCellX/Y`/`localRender*` field + the 5-arg ctor + `addUnit(Entity)`/
  `allocate(Entity)`/`queueSpawn(Entity)`; give `Entity` a minimal `{entityId, faction, type}` ctor.
  Rework `UnitRosterServiceTest` + `DeathDispatcherTest` for the new construction. The compiler surfaces
  every remaining seed writer — fix each. End state: `Entity` = `{entityId, faction, type}`, all
  construction via `spawn(EntitySpec)`.

**Phase D — the bare-`long` handle sweep.** **Committed, not optional — deferred to a follow-up
session** (its own multi-session arc). Roster dense `Entity[]` → `long[]`; the resolve layer
(`getOrNull`/`resolveUnit`/`targetOf`/`findBestTarget`/`DeathEvent.unit()`) returns `long`; every
`Entity` param → `long`; every `.entityId`/identity read → by-id/service. Sliceable package-by-package:
combat → decision/`TacticalScoring` → infantry/mech/drone behaviors → sim facade → ~55 test files.
The spatial indexes go id-native here — **which is where [`systems-to-columns`](systems-to-columns.md)
reopens.** ~150–200 files; mechanical. This is the phase that literally makes `entity = long`; A→C
are the prerequisites that let it be a clean mechanical sweep instead of a semantics minefield.

## Scope decision (DECIDED 2026-07-01 — value-first sequencing; D committed, deferred)

The value is **front-loaded**: Phase B is the genuine structural win (last live state into components,
`instanceof` branching gone, subclasses dissolved). Phase D is ~150–200 files of mechanical churn
whose payoff is *idiom-completion* — the perf case was already measured at ~0.02%/frame
([`phase0-measurement.md`](../phase0-measurement.md)), so D is "entity = long everywhere," not speed.
Phase B is a prerequisite for D regardless (can't collapse a still-subclassed `Drone`).

**Decision (user, 2026-07-01):** sequence **value-first — do A → B (+ C) this arc — and defer the
Phase-D bare-`long` sweep to a follow-up session.** Phase D is **not dropped/optional**: the committed
endgame is `entity = long` everywhere. It is deferred so the high-value structural work (dissolving the
subclasses, killing the last live state outside components, landing a spawn-spec) lands first, which
turns the eventual D sweep into a clean mechanical pass rather than a semantics minefield. `B1`
(DroneHubUnit → `HUB_STATE`) is the proving slice — on the critical path of every phase.

## Sequencing & risks

- **Order:** A and B are independent; B is cheapest-first (B1 → B2 → B3, hub before drone for the
  `homeHubId` link). C needs B done (subclass ctors gone). D needs A+B+C.
- **Green at every slice**; each phase/sub-slice its own commit + background critique pass
  ([[feedback_critique_pass]]).
- **Risk — parallel-dispatch safety.** New components read/written in the parallel UPDATE_UNITS phase
  (Drone patrol vectors, turret facing) must respect the same single-writer/multi-reader contract the
  existing columns do. The `DroneSwarmAction` rewrite is the one non-mechanical slice.
- **Risk — test churn (Phase C/D).** Mitigate with the shared `spawn` helper up front so the 58-file
  test surface converts once, not per-slice.

## Cross-refs

- [`systems-to-columns.md`](systems-to-columns.md) — reopens when the spatial index goes id-native (Phase D).
- [`entity-field-migration.md`](entity-field-migration.md) — upstream; hollowed the base `Entity` this dissolves.
- [`../spatial-index-options.md`](../spatial-index-options.md) — the id-native `LinkedUnitSpatialIndex` shape Phase D enables (stale `Unit`/`denseIdx` names).
- [`../overview.md`](../overview.md) § "Naming" — records the still-open caveat this epic closes.
- Memory: [[feedback_entity_for_loop_endgame]], [[feedback_skip_generation_bits]],
  [[feedback_components_by_capability_not_store]], [[battle_entity_storage_topology]],
  [[feedback_storage_foundation_build_right]].
