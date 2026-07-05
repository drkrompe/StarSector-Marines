# Identity-collapse — dissolve the `Entity` handle into a bare `long` id

> **Status: Phases A + B + C COMPLETE (2026-07-02). Phase D (the bare-`long` sweep)
> IN PROGRESS (2026-07-02) — D0 (infra) + D1 (combat pipeline) shipped `240df7f9`;
> D2 (`TacticalScoring` params) shipped `a782ad71`; D3 (sim-facade `targetOf`/`advanceMovement`)
> shipped `14a6d774`; D4 (`DamageService`/`HitResponse` front-doors) shipped `d6b61af2`; D5
> (weapon-fire methods) shipped `51d6f1c`; D6 (sim-facade `fire*` + `applyDamage(Entity)` front-door)
> shipped `da6c022c`; D7 (dest-index id-native + `NavigationService` setPath/clearPath internals)
> shipped `c296b13b`; D8 (facade `setPath`/`clearPath` → `long`, ~44 caller ripple) shipped `404bb3c5`.
> NOTE: this stretch is a bottom-up cascade of sequential
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
- ~~**D6 · facade `fire*` + external damage front-door**~~ — **SHIPPED (`da6c022c`; suite green, 869
  tests).** `BattleControl.fireShot` (×2) / `fireSecondary` / `fireMechWeapon` (×2) params → `long`
  (impls delegate to the long-native `InfantryWeapons`/`HeavyWeapons`, dropping the `.entityId`
  extraction — a pure passthrough), and the `BattleSimulation.applyDamage(Entity)` (×2) external-damage
  front-door → `long` (its `damageService.applyDamage` call went long-native in D4; callers are
  test-only now — the flyby strafing path routes elsewhere). A symmetric **60/60** diff, zero logic
  change: 5 production `sim.fire*` sites + 34 `applyDamage` test sites append `.entityId`; 2 stale
  `{@link …(Entity,…)}` Javadoc refs (`fireShot`, `MechSurviveContact`→`applyDamage`) fixed to `long`.
- ~~**D7 · dest-index id-native (the storage-core gate)**~~ — **SHIPPED (`c296b13b`; suite green, 869
  tests).** `UnitDestinationSpatialIndex` converted from `ArrayList<Entity>` buckets to id-native storage:
  a new `LongBucket` (minimal growable primitive-`long` list, order-agnostic swap-remove, public
  `ids`/`size` for SoA hot-loop iteration — no `Long`/`Entity` boxing) is both the bucket element AND the
  `gather` scratch. `rebuild` stores ids; `addDestination`/`removeDestination`/`gather` take/emit `long`.
  **Behavior nuance:** `gather` now skips `!isAliveById(id)` entries — a released id's by-id reads are
  unsafe under dense slot reuse (an `Entity` handle's `final faction` survived release; a bare id does
  not), and a dead unit is not a live destination occupant. Cascade: `DamageService.OccupancyApplier` +
  `applyOccupancyDelta(long)` (the queue already stored `unitId` as a `long`; the drain resolves ONLY to
  gate liveness, then passes the id) → `NavigationService.setPath`/`clearPath`/`applyOccupancyDeltaInline`
  internals → `long`. The `BattleSimulation.setPath(Entity)`/`clearPath(Entity)` **facade KEPT `Entity`**
  (delegates `.entityId` inward), so the ~30 behavior callers + 3 test callers do NOT move this slice.
  Reopens [`systems-to-columns`](systems-to-columns.md) (the id-native spatial-index work). Sister
  `UnitSpatialIndex` (still `Entity` buckets, more `gather` callers) + roster `Entity[]` are the later
  storage-finale scope, deliberately deferred.
- ~~**D8 · facade `setPath`/`clearPath` → `long`**~~ — **SHIPPED (`404bb3c5`; suite green, 869 tests).**
  `BattleControl.setPath(Entity, int[])`/`clearPath(Entity)` params → `long` (impls delegate the id to the
  id-native `NavigationService`). `writeFallbackInline` dropped its `clearPath(getOrNull(id))` backward
  resolve for a direct `clearPath(targetId)` (the D1-noted transitional resolve is gone). 44 behavior +
  test call sites across ~27 files (infantry/mech/decision/goap) pass `.entityId` (2 parallel Sonnet
  passes over disjoint clusters); `EquipmentDropSystem` + `SquadFallbackSystem` path-clearer
  `Consumer<Entity>` → `LongConsumer` (`this::clearPath` method-refs re-bind to `clearPath(long)`).
- ~~**D9 · the atomic `Action.execute(Entity member)` flip**~~ — **SHIPPED (`68838b84`; suite green, 869
  tests).** `Action.execute`'s `member` param → `long` (interface + all 24 concrete implementors +
  `AbstractZoneAction`'s `memberInZone`/`advanceIntoZone` + every private helper that received `member`,
  in ONE atomic commit). Bodies already read everything by id, so the bulk was a mechanical
  `Entity member`→`long member` + `member.entityId`→`member` sweep. The non-mechanical bits: (a)
  `DroneSwarmAction` — `member.type`/`member.faction` → `sim.identity().type/faction(member)`,
  `resolveSlotIndex`'s `List<Entity>.indexOf(member)` → an `entityId ==` scan; (b) the shared
  `PatrolMotion` helper class (`advance`/`WaypointSource.next`/`onHold`/`onMove`/`moveToward`/`hold`/
  `fireIfAble`) → `long`; (c) the adjacent `u`/`self`-named cluster flipped for consistency —
  `MechCombatantBehavior.tryFire*` (acting-unit `u`→`long`, `target` kept `Entity`),
  `EngageAtCurrentBand.execute`, `MechBreakContact.opportunisticMechFire`,
  `InfantryCohesion.cohesionOverride`, `ClearZone`/`HoldZone`'s `pickInZoneTarget`/`pickNearestInZoneEnemy`;
  (d) `SquadPlan.Step.slotOf(long)` overload added (the `Entity` overload delegates) — the role/slot
  system (`Slot<Entity>`, `assignments` `List<Entity>`) deliberately **stays `Entity`**. Callers: 3
  dispatchers (`Goap{Infantry,Mech,Drone}Behavior`) + `FiringSystem.tryReposition` pass `.entityId`; 13
  test files' `.execute(x,…)` / helper call sites pass `.entityId`; `PlannerTest`'s anon `Action` flipped.
  6 now-dead `Entity` imports dropped. **SEQUENCING CONFIRMED:** the "~30 implementors" were indeed one
  atomic commit (shared interface), but the flip rippled one hop past `member`-named params into
  same-file/shared helpers whose acting-unit param was named `u`/`self` — caught by compile, not a new
  slice. Reopens nothing new; the spatial index was already id-native (D7). **Next: `mintSquad` (nullable
  leader→`0L`) → air/vehicle/ui → the storage finale** (§ "Storage finale").
- ~~**D10 · `mintSquad(Faction, Entity leader)` → `long`**~~ — **SHIPPED (`739fd228`; suite green, 869
  tests).** The `mintSquad(Faction, Entity leader)` front-door → `(Faction, long leaderId)` at all three
  layers (`BattleControl` interface + `BattleSimulation` facade + `UnitRosterService` impl). `0L` was
  already the no-leader sentinel, so the body dropped the null-guard (`leaderId` feeds `squad.leaderId`
  directly); the `mechSquad` denormalization reads `identityService.type(leaderId).isMech()` off the
  leader's immutable `IDENTITY_TYPE` column (seeded at adopt — the leader is an already-live unit) instead
  of the `leader.type` handle field. The sibling `mintSquad(Faction, UnitType)` overload (the spec-based
  pre-spawn mint **every production caller** uses) is unchanged; the `Entity` overload was **test-only** in
  its callers — 5 sites (`ReinforceContact` ×2, `Sabotage`/`Conquest`/`Assault` command tests) pass
  `.entityId`. **MILESTONE:** with mintSquad flipped, the **`BattleControl`/`BattleView` sim facade now has
  ZERO `Entity` *params*** — every mutate/read front-door takes `long`. The only `Entity` left on the facade
  are the three *return* types (`liveUnitAt`/`targetOf`/`resolveUnit`), which are storage-finale scope.

**Where D leaves the param-flip (2026-07-03).** Sequencing question RESOLVED (user: sweep params-first).
The **acting-unit param sweep is DONE** (D9→D12): the sim facade is param-clean (D10), the GOAP
`Action.execute` (D9) + the `UnitBehavior.update` dispatch entry (D11) + every scattered acting-unit
subsystem param (D12) are `long`. **Every `Entity` param outside storage-finale scope is now `long`.**
What remains is exclusively **finale-coupled**: `target`/`candidate`/`threat` params fed from
`Entity`-returning queries (`targetOf`/`findBestTarget`/`liveUnitAt` — flip *with* those returns),
the `combathybrid/bridge` presentation layer (iterates `getDeathsThisFrame()`/`targetable` `Entity`
collections), roster/spatial storage, `DeathEvent`, and the `Entity.idOf`/`slotOf(Entity)`/roster
`adopt`/`PendingSpawn` seams. **Next is the storage finale** (§ below) — no more incremental param slices.

- ~~**D11 · the `UnitBehavior.update(Entity u)` flip**~~ — **SHIPPED (`72dfcdfb`; suite green, 869 tests).**
  The D9 analogue for the per-unit *dispatch entry*: interface `UnitBehavior.update(Entity u, BattleSimulation)`
  → `(long u, …)` + 11 implementors (`FallbackBehavior`, `FleeBehavior`, `DroneHubBehavior`,
  `Goap{Infantry,Mech,Drone}Behavior`, `CombatantBehavior`, `KitRetrieverBehavior`, `MechCombatantBehavior`,
  `StructureBehavior`, `TurretBehavior`) + acting-unit helpers (`FleeBehavior.updateFleeing/updateIdle/
  pickWanderDestination/findNearestThreat/pickFleeDestination`, `GoapInfantryBehavior.prepareForAction` →
  `InfantryUnitPrep.tickAimAndShortCircuit/tickCooldowns/tryOpportunityRocket`,
  `KitRetrieverBehavior.fireOpportunistically`, `DroneSpawner.tryLaunch`, `DroneHubBehavior.countActiveDrones`).
  Dispatcher `UnitUpdateSystem.updateUnit` keeps `Entity u` (roster walk), passes `.entityId`. Specials:
  `DroneHubBehavior` `u.type.isDroneHub()` → `sim.identity().type(u)`; `TurretBehavior` `u.faction` ×3 +
  `DroneSpawner` `hub.faction` ×2 + `InfantryUnitPrep` `unit.faction` → `sim.identity().faction(id)`.
  **The loop-local gotcha held:** `FleeBehavior.findNearestThreat` + `DroneHubBehavior.countActiveDrones`
  (and the `Goap*.replanIfNeeded` / `DroneSpawner` scans) keep their loop-local `Entity u` — sed targeted only
  the acting-param name (`self`/`hub`/`unit`), and the two true `u`-collision methods were hand-edited.
  `FleeBehavior`'s `u == self` reference-identity → `u.entityId == self`. `DroneSpawner.tryLaunch(long hub)`
  still **returns `Entity`** (finale scope; discarded at its only caller). 4 test files + 2 dead `Entity`
  imports. Clean compile first pass. **Critique: SHIP** (loop-local handling verified equivalent; the 3
  type-check-both-ways sites all read the intended variable).
- ~~**D12 · remaining scattered acting-unit params**~~ — **SHIPPED (`1a44f09a`; suite green, 869 tests).**
  The last cleanly-flippable acting-unit `Entity` params (NOT query-fed): `NavigationService.pathDestX`/
  `pathDestY`, `FogOfWarService.addContributor`, `BattleSetup.attachMechLoadout`,
  `SquadStateDumper.computeTargetReachable` → `long`. Callers pass `.entityId` (3 `addContributor` in
  `BattleSimulation` incl. a `for (Entity u : snapshot)` loop var; 3 `attachMechLoadout` + 1
  `computeTargetReachable` in-file). **Loop-local near-miss (compiler-caught, no silent bug):** a file-wide
  `u.entityId`→`u` sed on `FogOfWarService` also hit a *second* method's per-frame `for (Entity u : roster)`
  loop var (`isCellRevealed(cellX(u.entityId),…)`) — flagged as `Entity`→`long` at compile, reverted.
  **Follow-up:** `pathDestX`/`pathDestY` have **zero callers** — dead code, deletion candidate.
  **This completes the params-first acting-unit sweep** — only finale-coupled `Entity` remains.

## Storage finale — the dedicated closing session (the `entity = long` terminus)

The one milestone that *literally* makes `entity = long` and **deletes `Entity.java`**. Called out
as its own session (not tail-text) because it is the **largest single structural piece** of the whole
migration — it converts the roster spine and every remaining `Entity`-typed storage/return in one
coherent pass — and because it is only safe to do **last**, once every caller is already `long`-native.

**Entry precondition (why last, not now).** Params-first is what de-risks this: by the time we reach
it, every method *parameter* and behavior body already takes/holds `long`, so flipping the *returns*
and the *storage* is a mechanical find-and-replace, not a semantics change. Pulling it forward would
force backward `long`→`Entity` re-resolves at every not-yet-converted caller — the exact churn the
phase ordering exists to avoid. **Gate: behavior tier (`Action.execute` + all behaviors) + sim facade
+ air/vehicle/ui all long-native** before this session opens.

**Scope (one dedicated session — its own commit chain + critique pass):**
- Roster dense `Entity[]` → `long[]` (the `denseArray()`/`liveCount()` spine + swap-and-pop release);
  `UnitRosterService` becomes id-native storage.
- Sister `UnitSpatialIndex` → id-native (the `LongBucket` shape D7 proved; its `gather` has many
  callers, so this is the widest single ripple in the session).
- Every `Entity`-returning query/resolve method → `long` (`targetOf`, `getOrNull`/`resolveUnit` at the
  boundary, the `closestVisible*` family, …); the transient `Entity` locals evaporate.
- `DeathEvent` payload → `long`.
- Rehome the two remaining statics off `Entity`: `Entity.idOf` and `NO_SQUAD`.
- **Delete `Entity.java`.**

**Execution — sliced green-per-commit (IN PROGRESS 2026-07-03).** The finale is being run as
green-at-each-step slices (F1…F5), not one non-compiling mega-edit — same discipline as A–D:
- ~~**F1 · `DeathEvent` payload → `long`**~~ — **SHIPPED (`5eb8dff0`; 869 green).** `DeathEvent`
  record `Entity unit` → `long unitId`. Publisher (`DamageResolver`, `HubDemolitionSystem` cascade)
  passes the id it already holds; the 5 subscribers classify by id — turret/hub demolition read
  `identity().type(id).isX()` off their roster, `DroneCrashSystem` reads the kept-through-transmute
  `IDENTITY_TYPE` column directly (no roster handle), `MechWreck`/`DeadBody` read id-keyed corpse
  columns. `SimProxyMirror` (bridge, still `Entity`) compares `link.unit.entityId == event.unitId()`.
- ~~**F2 · the "target web" → `long`**~~ — **SHIPPED (`a671b0c0`; 869 green; 33 files).** A unit's
  acquired/current target is a `long` id everywhere it flows — ONE coherent cut because the behavior
  callers share a single `target` local fed by BOTH `findBestTarget` AND `targetOf`, so those seams
  couldn't flip independently without backward `getOrNull`/`idOf` wrapping. Flipped: `TacticalScoring`
  `findBestTarget`(×4)/`refreshTargetIfNotShootable`/`closestEnemyInAttackRange`/`findEngageableEnemyWithin`
  returns (internals keep `Entity` locals off the still-`Entity[]` `denseArray`; convert at `return`);
  facade `targetOf` (isLive-gated so a stale released target id still resolves to `0L` — the long-native
  `getOrNull==null` lazy-validity, a real semantic that had to be preserved); the whole turret-fire chain
  (`TurretAim.State.target` field, `TurretFireSink`/`TurretFireSystem.fire`, `fireShotFrom`×2, all THREE
  `TurretAim.State` consumers — `TurretBehavior`/`AirSystem`/`GroundSystem` — `MountedTurret` setters);
  mech `tryFire*` family; `DroneSwarmAction` `s.target`/`tryAgroScan`; `BreachToEngage.effectiveTarget`;
  `EngagePosture` `isHardened(target.type)`→`isHardened(identity().type(id))`. `currentBurstTarget` stays
  `Entity` (`resolveUnit`, F3). `ClearZone`/`HoldZone` bridge their `liveUnitAt` in-zone pickers with
  `Entity.idOf` (F3 follow-up).
- ~~**F3a · `resolveUnit` → `long`**~~ — **SHIPPED (`30f7ced8`; 869 green; 13 files).**
  `BattleView/BattleControl.resolveUnit(long)` → `isLive(id) ? id : 0L` (the long-native
  `getOrNull != null` liveness gate). All ~11 consumers read only `.entityId` on the resolved local →
  uniform (`long X`, `== 0L`, `X`). **Un-bridged the F2 `currentBurstTarget.entityId`** in all three
  turret-fire consumers (TurretBehavior via `resolveUnit`; AirSystem/GroundSystem via the sibling
  `roster.getOrNull` → `roster.isLive(id) ? id : 0L`).
- ~~**F3b · `liveUnitAt` → `long`**~~ — **SHIPPED (`58e3d4f6`; 869 green; 51 files).** The last
  `Entity`-returning facade method. `liveUnitAt(idx)` → `get(idx).entityId` transitionally (until F5
  flips `get`/`denseArray` to `long[]`). ~50 roster-walk consumers `Entity u = liveUnitAt(i)` → `long u`;
  `.faction`/`.type` → `identity().faction(u)`/`type(u)`; `.entityId` → the id. Un-bridged the F2
  `Entity.idOf` in `ClearZone`/`HoldZone` in-zone pickers (now return `long`); `ChokePointHold.enemyOnPortalCell`
  + `BreachToEngage` helpers → `long` (`0L`=none). `TestUnits.kill`/`snapshot` flipped forward
  (`kill(sim,long)`, `snapshot()`→`List<Long>`; kill callers append `.entityId`). **Two couplings the flip
  forced — both closed WITHOUT re-adding an `Entity`-returning facade:** (1) the GOAP-replan `aliveMembers`
  feeds `RoleAssigner.assign`, whose `Action.roles()` pins `Slot<Entity>` (F5 scope). `RoleAssigner` is
  generic in `<C>` so needs nothing, but `Action` pins `C=Entity` — so `aliveMembers` stays `List<Entity>`,
  read from the still-`Entity` dense store via `getRoster().get(i)` (the id-based squad-membership check
  still uses `liveUnitAt(i)`); identical fix in `BreachAndAdvanceTest.attach`. This is the F3b↔F5 seam:
  role/plan storage flips WITH the roster's `Entity[]`→`long[]`, not with the facade. (2) `isRoofShielded` /
  `applyExternalDamage` (flyby strafing's only callers, plus the combat bridge) take a `liveUnitAt`-sourced
  arg → `Entity`→`long`; `SimProxyMirror` (bridge, `ProxyLink.unit` still `Entity`) appends `.entityId`.
- ~~**F4 · `UnitSpatialIndex` id-native**~~ — **SHIPPED (`7080ce8b`; 869 green; 6 files).** The primary
  index carries bare ids. Inner `Bucket.units` `Entity[]`→`long[]` (the snapshot `cellX`/`cellY` parallel
  arrays stay — the "no by-id probe in gather" perf choice is preserved); `gather(...)` out
  `ArrayList<Entity>`→`LongBucket`; `add(roster,Entity)`→`add(roster,long)`; `rebuild` reads
  `dense[i].entityId` (denseArray stays `Entity[]` until F5); `clear()` drops the null-out loop (a `long[]`
  pins nothing). 10 `gather` sites (`TacticalScoring`×7, `InfantryUnitPrep`, `AirSystem`, `WorldStateBuilder`)
  iterate `LongBucket.ids[0..size)`; `.faction`/`.type`→`identity()`. `filterEnemyCombatants` dropped
  `static` (it now needs `roster.identity()`) and compacts the `LongBucket` in place (`ids[write++]`;
  `size=write`); `resolveThreatColumns(List<Entity>)`→`(LongBucket)`. Sister `UnitDestinationSpatialIndex`
  (already id-native from D7) untouched; `AttackerIndexService.getAttackersOf` stays `Entity` (F5).
- **F5 · roster storage + delete `Entity.java`** (the terminus) — sub-sliced green-per-commit; F5 recon
  found ~928 `Entity` tokens over 161 files (many `{@link}`/prose), so it runs as F5a→F5b→F5c:
  - ~~**F5a · role/plan storage id-native**~~ — **SHIPPED (`7227310e`; 869 green; 17 files).**
    `Action.roles()`→`List<RoleAssigner.Slot<Long>>`; `SquadPlan.Step.assignments`→`Map<String,List<Long>>`;
    `allAssignedMembers()`/`slotOf(long)` walk `List<Long>`. `RoleAssigner`/`Slot<C>`/`Scorer<C>` were
    already generic — zero change; the `Entity` coupling was only ever at the callers. 5 `roles()` overrides
    flip `Slot<Entity>`→`Slot<Long>` (Scorer lambdas drop `.entityId` — the `Long` candidate auto-unboxes);
    the 3 GOAP replan loops **shed the F3b `getRoster().get(i)` bridge** (`aliveMembers` is `List<Long>` from
    `liveUnitAt(i)`), closing the F3b↔F5 seam. Consumers (`DroneSwarmAction`, `SquadStateDumper`,
    `SquadPlanDebugPanel`) + 4 role tests id-native.
  - ~~**F5b · spawn/death boundary**~~ — **SHIPPED (`e16dfea7`; 869 green; 66 files).**
    `spawn`/`queueSpawn`→`long` (roster + `BattleControl`/`BattleSimulation` facade; the deferred
    `queueSpawn` returns `0L` — the id is minted at the flush, so none exists at queue time, and
    `DroneSpawner`'s `squad.leaderId` stays `0L` until a serial spawn assigns it, byte-identical to the old
    `entityId==0`-until-flush handle). `adopt(EntitySpec)` builds the `Entity` handle internally + returns the
    id; **`PendingSpawn` is spec-only** (the recon's "`(long,spec)`" sketch was wrong — no id at queue time);
    `flushPendingSpawns()`→`LongList` of the adopted ids (fog registration in `BattleSimulation` reads faction
    by-id off the returned ids — IDENTITY is seeded in `adopt` before the id is returned); `getPendingSpawns()`
    **deleted** (its lone caller folded into the flush return). `deathsThisFrame`→`LongList` +
    `getDeathsThisFrame()`→`LongList`; the `deathSink` captures the dying id directly (dropping the transitional
    `getOrNull` resolve — strictly more correct), and the two death-voice consumers (`BattleScreen`,
    `GroundSimPresentation`) read faction + render-pos by-id post-advance (IDENTITY rides the transmute,
    RENDER_POSITION is universal + off the corpse-remove mask, so both resolve on the just-dead unit).
    `DroneSpawner.tryLaunch`→`long`. **Deliberate F5c-scope bridge:** `BattleSetup.spawnDefensePostTurrets`
    KEEPS `List<Entity>` via `getRoster().getOrNull(id)` (non-null immediately post-spawn) — its return + the
    `MapBuild`/`SimProxyMirror` bridge consumers flip with the roster storage in F5c. ~58 test files flipped
    `Entity` spawn locals→`long` (6 parallel Sonnet passes over disjoint clusters); `UnitRosterServiceTest`
    reworked by hand — `get`/`getOrNull`/`denseArray` stay `Entity` until F5c, so its handle-identity
    `assertSame(handle, r.get(i))` became `assertEquals(id, r.get(i).entityId)`.
  - **F5c · roster storage + delete** (next, the terminus) — dense `Entity[]`→`long[]`; `getOrNull`/`get`/`denseArray`
    deleted-or-`long`; **un-bridge F5b's `spawnDefensePostTurrets` `getOrNull`** + the last render/ui/bridge
    (`SimProxyMirror`/`GroundBattleConfig` `targetable`) + `AttackerIndexService` consumers; rehome
    `Entity.idOf`/`NO_SQUAD`; drop the dead `SquadPlan.slotOf(Entity)` overload; **delete `Entity.java`**.

**Tracked follow-ups to fold in here (don't lose):**
- `clearPath`/`setPath` should own an internal null/liveness guard rather than lean on the caller
  (D1 critique — `writeFallbackInline` transitionally does `clearPath(getOrNull(id))`).
- [`../spatial-index-options.md`](../spatial-index-options.md) — the id-native `LinkedUnitSpatialIndex`
  shape this enables (stale `Unit`/`denseIdx` names to reconcile).

**Definition of done.** `Entity.java` is gone; no `Entity` type in the battle tier; `entity = long`
holds at every layer (params, returns, storage, events); the overview.md "naming north star did NOT
fully reach" caveat is struck; full suite green. **This closes the identity-collapse epic.**

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
