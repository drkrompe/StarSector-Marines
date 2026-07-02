# Identity-collapse — dissolve the `Entity` handle into a bare `long` id

> **Status: Phases A + B + C COMPLETE (2026-07-02). Phase D (the bare-`long` sweep)
> IN PROGRESS (2026-07-02) — D0 (infra) + D1 (combat pipeline) shipped `240df7f9`;
> D2 (`TacticalScoring` params) shipped `a782ad71`; D3 (sim-facade `targetOf`/`advanceMovement`)
> shipped `14a6d774`; D4 (`DamageService`/`HitResponse` front-doors) shipped `d6b61af2`; D5
> (weapon-fire methods) shipped `51d6f1c`. NOTE: this stretch is a bottom-up cascade of sequential
> slices, NOT a parallel behavior-cluster fan-out — see the § Phase D "SEQUENCING CORRECTION".**
> The endgame is still `entity = long` everywhere. **Phase A** — `rng`→`ThreadLocalRandom` (`4e6238c0`), base
> methods→Services (`ead4ec0d`), String `id`→`IDENTITY_NAME` + `IdentityService` (`e0240ac6`).
> **Phase B** — subclasses dissolved into components (B1 `a4180ef0` / B2 `2d9eb894` / B3
> `38764ca7`). **Phase C** (spawn-spec; user scope "Full — build it right") — C1 `b7884353` /
> C2 `9ea2a95a` / C3 `e4e45833` / C4 `6fa06ca1`→`50d92c8d`: `Entity` is now
> `{entityId, faction, type, NO_SQUAD, idOf}`, all construction via `EntitySpec` + `spawn(spec)`.
> **Next: Phase D.**
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
- ~~**C3 · test migration → `spawn(spec)`**~~ — **SHIPPED (`e4e45833`; 3 parallel Sonnets, 45 test
  files, ~217 sites; full suite green).** `new Entity(...) [+ .seedX=…] + sim.addUnit/queueSpawn(u)` →
  `sim.spawn(new EntitySpec(...)[.setterX(…)])`; the C2 `.toEntity()` factory bridges collapsed into
  `sim.spawn(Factory.create(...))`. Seed→setter map = the `EntitySpec` fluent API. Seeds still exist,
  so it's behavior-identical + the untouched sites still compile.
- **The exact sites left on the raw path for C4** (all 3 agents converged on these — the C4 to-do list):
  - **`mintSquad`-before-adoption squad-setup** (~11 sites): `CivilianCombatMembershipTest`,
    `StaticEmplacementMembershipTest`, `HitResponseSystemTest` (×2), `MechMoraleTest`, `SquadMoraleTest`,
    `CompoundSupplyGatingTest`, `RecaptureTargetServiceTest`, `OverwatchKillZoneGoalTest`,
    `BackstopAssignedSquadGoalTest`, `BreachToEngageTest`, `BreachAndAdvanceTest` (×4). Pattern: leader
    handed to `mintSquad` while `entityId==0` (→ `leaderId 0L`). The `mintSquad(faction,type)` refactor
    unblocks these (spawn first / mint by type, then `spec.squad`).
  - **sim-less `allocate`-direct tests**: the 7 `*ServiceTest` (`World`/`Vision`/`Task`/`Squad`/`Role`/
    `Home`/`Combat`) + `AirEntityAllocationTest` + `VehicleEntityAllocationTest` — build `new Entity` +
    `roster.allocate(Entity)`; need a **roster-level `spawn(EntitySpec)`** (add in C4).
  - **`UnitRosterServiceTest`** (tests `addUnit`/`allocate`/swap-pop directly) + **`DeathDispatcherTest`**
    (sim-less fixtures, reads `e.unit().seedName`) — rework for the post-deletion construction.
  - **sim-less builder helpers** that return an unadded `Entity`: `OverwatchPostureTest.defenderAt`,
    `BreakLOSTest.marineAt`/`defenderAt` — thread `sim` + `spawn` (as group B did for its helpers).
  - **`InfantryUnitPrepTest.rocketeer()`** — sets a `seedPrimaryWeapon` **reference without** its stat
    block; `.primaryWeapon()` would inject the weapon's stats. Set the weapon post-spawn by id instead
    (`sim.combat().setPrimaryWeapon`).
- ~~**C4 · delete `seed*` + squad-site + `mintSquad`**~~ — **SHIPPED (2026-07-02; 4 green commits).**
  **C4.1** (`6fa06ca1`) `mintSquad(Faction, UnitType)` added as a **dual overload** — NOT the planned
  outright replace. The recon premise ("every caller mints a not-yet-allocated leader → `leaderId` 0")
  **held for the production callers** (all mint pre-allocation) but was **wrong for four test fixtures**:
  `ReinforceContact`/`Sabotage`/`Conquest`/`Assault` spawn-then-mint with an ALLOCATED leader and rely on
  the resulting real `leaderId`. Keeping `mintSquad(Faction, Entity)` for those auto-preserves it (no
  explicit leader-assignment needed); the new `(Faction, UnitType)` overload is the pre-spawn mint the
  spec path needs (no live Entity at mint). `mechSquad` is preserved either way — both overloads derive it
  from the unit's `type.isMech()`, so it is NOT a reason to keep the Entity overload. The 11 literal-`null`
  passers (ambiguous under the overload) → `UnitType.MARINE` (non-mech, so `mechSquad=false` preserved). **C4.2** (`0a03f604`) production construction → `EntitySpec`: deboard ×2
  (`MarineLoadout.seedInto(EntitySpec)`, `addUnitSink` → `Consumer<EntitySpec>` wired to `this::spawn`,
  `mintSquad(faction, deboardType)`), 3 `BattleSetup` clusters + `makeDefender`→spec, `WalkInMeans`,
  `DroneSpawner` (mint the drone squad BEFORE `queueSpawn` so `spec.squad` seeds membership; `leaderId`
  preserved via `newSquad || dead-leader`), `SimProxyMirror` (unit name snapshotted into `ProxyLink` at
  creation — release-safe, replacing the deleted `seedName` read). **C4.3** (`fe86d529`) the 24 raw-path
  test files → spec (3 parallel Sonnets + `InfantryUnitPrep.rocketeer` done main-thread with post-spawn
  `combat().setPrimaryWeapon` to dodge `.primaryWeapon()`'s stat derivation; mint-before-adoption →
  mint-by-TYPE-then-spawn-with-`.squad`, NEVER spawn-then-mint — that would set a real `leaderId`).
  **C4.4** (`50d92c8d`) the deletion: `allocate(Entity)` → private `adopt(Entity, EntitySpec)` reading
  the spec; `spawn`/`queueSpawn(EntitySpec)` roster-native (pending queue holds `(handle, spec)` pairs
  the flush adopts); `addUnit`/`allocate`/`queueSpawn(Entity)` + `EntitySpec.toEntity()` + every `Entity`
  `seed*`/`localRender*` field + the 5-arg ctor DELETED (the double-allocate guard with them — `spawn`
  always mints a fresh handle); minimal `Entity(faction, type)` ctor. `UnitRosterServiceTest` reworked to
  `spawn(spec)` (`allocateRejectsAlreadyAllocatedUnit` deleted — scenario unreachable), `DeathDispatcherTest`
  keys on `entityId` (the name is gone), `World`/`VisionServiceTest` read expected values off the
  spec/`UnitType`. **End state: `Entity` = `{entityId, faction, type, NO_SQUAD, idOf}`, all construction
  via `EntitySpec` + `spawn(spec)`.** −470 net lines in C4.4; compiler-verified zero seed refs; full
  suite green at every commit.

**Phase D — the bare-`long` handle sweep. IN PROGRESS (2026-07-02).** Roster dense `Entity[]` →
`long[]`; the resolve layer (`getOrNull`/`resolveUnit`/`targetOf`/`findBestTarget`/`DeathEvent.unit()`)
returns `long`; every `Entity` param → `long`; every `.entityId`/identity read → by-id/service. The
spatial indexes go id-native here — **which is where [`systems-to-columns`](systems-to-columns.md)
reopens.** ~150–200 files (recon 2026-07-02: **1484 `Entity` refs / 210 files**, 918/149 main +
566/61 test); mechanical.

**Execution strategy — params-first, returns+storage-finale.** `Entity` and `long` are incompatible
types, so any seam flip cascades to callers immediately; to keep every slice green + committable, flip
in this order:
- **Params first (cheap, one-directional ripple).** Flip method `Entity` params → `long`, read fields
  by-id inside (`identity().type(id)`/`faction(id)`, the by-id Services, `world.*(id)`); callers pass
  `.entityId` (they usually already hold the id). `Entity`-**returning** query/resolve methods
  (`findBestTarget`, `targetOf`, `resolveUnit`, `getOrNull`, `liveUnitAt`, `denseArray`) KEEP returning
  `Entity` for now — so a converted method's local obtained from a query stays an `Entity` used for its
  `.entityId`. Wrap `resolveUnit(id)` inbound only where a converted body must hand an `Entity` to a
  still-`Entity`-param callee (minimize by converting callees before callers within a package).
- **Returns + storage last (the finale).** Once callers are long-native, flip every `Entity`-returning
  method → `long`, the roster dense `Entity[]` → `long[]`, both spatial indexes id-native,
  `DeathEvent` → `long`, rehome `Entity.idOf`/`NO_SQUAD`, and **delete `Entity`**. The transient
  `Entity` locals evaporate (queries now return `long`). **Finale follow-up (from the D1 critique):**
  when `clearPath(Entity)` goes id-native it should take `long` + null-guard internally, not lean on
  the caller — D1's `writeFallbackInline` transitionally does `clearPath(getOrNull(id))`, which would
  NPE if fallback were ever applied inline to an unregistered target (unreachable today, but the
  id-native `clearPath` should own the guard).

Sliceable package-by-package: combat → decision/`TacticalScoring` → infantry/mech/drone/turret/squad
behaviors → sim facade + air/vehicle/ui → finale → ~61 test files. This is the phase that literally
makes `entity = long`; A→C are the prerequisites that let it be a clean mechanical sweep instead of a
semantics minefield.

- ~~**D0 · infra**~~ + ~~**D1 · combat pipeline**~~ — **SHIPPED (`240df7f9`; suite green).**
  **D0:** `IdentityService.type(id)`/`faction(id)` (by-id reads over `IDENTITY_TYPE`/`IDENTITY_FACTION`
  — the replacement for `Entity.type`/`.faction`; every roster-held handle carries IDENTITY, which
  rides the death transmute so it reads on a corpse too); `BattleView.identity()` for leaf reach.
  **D1:** the combat damage pipeline internals → `long` — `DamageService` SoA queue `Entity[]` →
  `long[]`, the `DamageApplier`/`ReprioApplier`/`FallbackApplier` interfaces + `DamageResolver.resolve`
  + the full death cascade (leader promotion, morale drains, equipment-drop emit, death publish,
  release) key off the id; `deathSink` `Consumer<Entity>` → `LongConsumer`; `pickPromotionCandidate` →
  `long` (0L=none). The public `applyDamage`/`applyExternalDamage` front-doors KEEP `Entity` (extract
  `.entityId`), severing caller ripple. `EquipmentDropService.emitIfApplicable(long)`.
  `TacticalScoring.isHardened(UnitType)` (a pure type predicate). Deliberately left `Entity`: the
  `OccupancyApplier` + `resolver` liveness-null-check (dest-index reference-identity is finale scope);
  `DeathEvent` (resolved transitionally via `getOrNull`, pre-release).
- ~~**D2 · `TacticalScoring` params**~~ — **SHIPPED (`a782ad71`; suite green, 869 tests).** Every
  `Entity` param in `battle/decision/TacticalScoring.java` → `long` (read fields by-id via
  `roster.identity()`/`world()`/`vision()`); the Entity-RETURNING queries (`findBestTarget`,
  `closestEnemyInAttackRange`, `refreshTargetIfNotShootable`, `findEngageableEnemyWithin`,
  `closestVisibleOtherEnemy`) keep returning `Entity` until the finale. Nullable `self`/`exclude`
  → `0L` sentinel (`== null` → `== 0L`). `scoreCrowding` keeps an `Entity target` (feeds the
  Entity-keyed `AttackerIndexService`, finale scope). `TurretAim.State.excludeFromCrowding` field
  `Entity` → `long` (`null` → `0L` for shuttle turrets; `GroundSystem` defaults to `0L`). ~26
  behavior callers (infantry/mech/drone/turret/goap) + `TacticalScoringTest` pass `.entityId`.
- ~~**D3 · sim-facade `targetOf`/`advanceMovement` params**~~ — **SHIPPED (`14a6d774`; suite green,
  869 tests).** The two `BattleView`/`BattleControl` facade methods that were *already id-native
  underneath* → `long`: `targetOf(long)` (still RETURNS `Entity`) over `world.targetId(id)`,
  `advanceMovement(long)` over `movement().advanceAlongPath(world, id, dt)` — zero resolve churn.
  ~48 callers across goap/infantry/mech/drone/turret/ui pass `.entityId` (fanned out to 3 parallel
  Sonnet passes over disjoint file groups).
- ~~**D4 · `DamageService`/`HitResponse` front-doors**~~ — **SHIPPED (`d6b61af2`; suite green, 869
  tests).** The D1-deferred "keep `Entity` front-door" layer → `long`: `DamageService`
  `applyDamage`/`applyReprio`/`applyFallback` + `HitResponseSystem`
  `rollFallbackOnHit`/`rollReprioritizeOnHit` (internals already long-native from D1, so the flip just
  drops the `.entityId` extraction). `rollReprioritizeOnHit`'s nullable `shooter` → `0L`;
  `target.type` → `roster.identity().type(target)`; reprio-dedup `ConcurrentHashMap<Long,Integer>`
  keys unchanged. Kept `applyOccupancyDelta(Entity)` (dest-index applier — dest-index-gated, see below).
  16 callers (HeavyWeapons / InfantryWeapons / TurretFireSystem / Detonations / BattleSimulation
  front-door + 2 tests) pass `.entityId`.

**SEQUENCING CORRECTION (discovered D3, 2026-07-02).** The "behavior clusters" are **not** an
independent parallel fan-out. All ~30 behaviors implement one shared `Action.execute(Entity member,…)`
interface (flipping it is a single *atomic* commit, not N independent ones), and that flip is **gated
bottom-up**: `execute`'s `member` feeds `Entity`-taking `BattleControl` mutators (`setPath`,
`fireSecondary`, …), which in turn delegate to `Entity`-taking leaf services (`NavigationService`,
`InfantryWeapons`, `HeavyWeapons`). Flipping any upper layer before the one below it just re-resolves
`long`→`Entity` at the boundary (backwards churn). So this stretch is a **bottom-up cascade of
self-contained slices** (D2-cadence: flip the layer, callers pass `.entityId`, delegate the ripple),
*not* parallel cluster-commits. The only parallelism available is delegating each slice's mechanical
caller ripple (as D2/D3 did). Everything funnels through the shared `BattleSimulation` facade +
`Action` interface, so the *slices* are sequential.
- ~~**D5 · weapon-fire methods**~~ — **SHIPPED (`51d6f1c`; suite green, 869 tests).**
  `InfantryWeapons.fireShot`/`fireSecondary` + `HeavyWeapons.fireMechWeapon` (×2) params → `long`
  (unblocked by D4; `shooter.type`/`faction` cached via `roster.identity()`, `UnitType`/`Faction`
  imports added; internal burst/salvo tick continuations + the `BattleSimulation` facade wrappers pass
  `.entityId`).
- **Next — facade `fire*` + external damage front-door:** `BattleControl.fireShot` (×2) /
  `fireSecondary` / `fireMechWeapon` (×2) params → `long` (impls now delegate to long-native
  `InfantryWeapons`/`HeavyWeapons`; callers = the few direct `sim.fire*` sites), and the
  `BattleSimulation.applyDamage(Entity)` external-damage front-door (its `damageService.applyDamage`
  call went long-native in D4).
- **Dest-index-gated (its own slice, NOT the tail finale):** `setPath`/`clearPath` feed
  `DamageService.applyOccupancyDelta(Entity u,…)` → the `UnitDestinationSpatialIndex` (reference-identity
  `remove`). They — and the `Action.execute(Entity member)` interface flip, whose bodies call
  `sim.setPath(member,…)` — can't cleanly go `long` until the **dest-index goes id-native** (the
  reopened [`systems-to-columns`](systems-to-columns.md) / id-native spatial-index work). That slice
  then unblocks: `setPath`/`clearPath` (+ the D1 `writeFallbackInline` `getOrNull` follow-up) → the
  **atomic `Action.execute` flip** (interface + ~30 implementors + dispatcher + `AbstractZoneAction` +
  test anon impls, one commit) → `mintSquad` (nullable leader→`0L`) → air/vehicle/ui → the finale
  (Entity-returning queries → `long`, roster `Entity[]` → `long[]`, `DeathEvent` → `long`, rehome
  `idOf`/`NO_SQUAD`, delete `Entity`).

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
