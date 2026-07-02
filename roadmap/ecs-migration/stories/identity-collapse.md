# Identity-collapse — dissolve the `Entity` handle into a bare `long` id

> **Status: DESIGN (2026-07-01, picked up). Recon COMPLETE (3 parallel agents — full
> tabulations in session). Scope decision PENDING (see § Scope decision). No code yet.**
> The last open ECS-migration epic that touches the identity layer: turn `Entity` from a
> ~305-line heap object held in the roster's `Entity[]` into a bare `long` id, so
> `entity = id` is literally true everywhere. The spatial index goes id-native as a
> byproduct, which reopens [`systems-to-columns`](systems-to-columns.md) (closed at its
> Phase-0 terminus). Backlog item 9 in [`../next-session.md`](../next-session.md).

## Where `Entity` is today

The [`entity-field-migration`](entity-field-migration.md) already hollowed the **base**
`Entity`: it now carries only

- `entityId` (the `long` — the identity),
- immutable identity: `id` (String, human-readable name), `faction`, `type` (`UnitType`),
  `rng` (`java.util.Random`),
- write-only `seed*` construction inputs (consumed once by `UnitRosterService.allocate`),
- two methods: `advanceAlongPath(World, float)` and `beginBurst(CombatService, Entity)`.

`faction` and `type` are **already mirrored** into the world's `IDENTITY` component
(`IDENTITY_FACTION` / `IDENTITY_TYPE`, readable by id). So a base-`Entity` ref exists today
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

**Phase A — identity side-quests + base-method rehoming.** Independent, small (~15–20 files).
`rng` → thread-local; String `id` → `IDENTITY` name column + selection-key on `entityId`;
`advanceAlongPath` → `MovementService`; `beginBurst` → `CombatService`. After A the base `Entity`
is `entityId` + `faction`/`type` (already-in-world) + `seed*` — nothing a `long` + `IDENTITY` read
can't serve.

**Phase B — subclass live-state → components (the value).** Cheapest-first, per recon:
- **B1 · `DroneHubUnit`** — `HUB_STATE` for `spawnCooldown`/`dronesLaunched`/`droneSquad`;
  `demolished` as a shared demolition flag; establish `homeHubId` (Drone's link) as a component/
  side-table. Convert the 4 type-tag `instanceof` (renderer HP-bar sizing, footprint, `isHardened`)
  to `UnitType`/presence lookups; 1 real state-cast (`DroneHubBehavior`). Lowest blast radius —
  **the proving slice.**
- **B2 · `MapTurret`** — **fold `burstRemaining`/`burstTimer`/`burstTargetId` onto the existing
  COMBAT burst columns** (the turret is the sole writer of its shadow copies; deletes 3 fields +
  ~12 sites for free); `TURRET_STATE` for `facingDegrees`/`recoilTimer`; `kind` → side-table by id;
  `demolished` shares B1's flag. Convert 3 type-tag `instanceof` (`TacticalScoring.isHardened`,
  `AirSystem` AA filter, `HitResponseSystem` reprio — the last has a ready `hasAiState`-style
  presence precedent) + 2 renderer casts.
- **B3 · `Drone`** — `DRONE_STATE` for the patrol/pursuit vectors + timer; rewrite `DroneSwarmAction`'s
  7 `Drone d` helpers to `(long id)` + component fetch. Entangled with the hub via `homeHub`
  (migrate as a pair, B1 establishes `homeHubId` first). Hairiest; `DroneCrashSystem`'s `CRASHING`
  composition is the in-repo end-state to imitate.

After B: no `Entity` subclasses, no live state outside components, no state-reach `instanceof`.

**Phase C — spawn-spec.** Replace `new X(...)` + `seed*` writes + `allocate(Entity)` with an
`EntitySpec` (mirroring `allocateAir`/`allocateVehicle`) consumed by `roster.spawn(spec) → long`.
Dedupe the deboard loadout. Absorb test churn with a shared `spawn(sim, spec)` helper (and/or a
transitional `addUnit(Entity)` adapter kept until Phase D). Subclass ctors become spec factories.

**Phase D — the bare-`long` handle sweep.** Roster dense `Entity[]` → `long[]`; the resolve layer
(`getOrNull`/`resolveUnit`/`targetOf`/`findBestTarget`/`DeathEvent.unit()`) returns `long`; every
`Entity` param → `long`; every `.entityId`/identity read → by-id/service. Sliceable package-by-package:
combat → decision/`TacticalScoring` → infantry/mech/drone behaviors → sim facade → ~55 test files.
The spatial indexes go id-native here — **which is where [`systems-to-columns`](systems-to-columns.md)
reopens.** ~150–200 files; mechanical.

## Scope decision (PENDING — the fork worth your call)

The value is **front-loaded**: Phase B is the genuine structural win (last live state into components,
`instanceof` branching gone, subclasses dissolved). Phase D is ~150–200 files of mechanical churn
whose payoff is *idiom-completion* — the perf case was already measured at ~0.02%/frame
([`phase0-measurement.md`](../phase0-measurement.md)), so D is "entity = long everywhere," not speed.
Phase B is a prerequisite for D regardless (can't collapse a still-subclassed `Drone`).

Three defensible stopping lines:
1. **Full arc (A → D)** — end at bare `long` everywhere; spatial index id-native; systems-to-columns
   reopens. The backlog-literal target. Multi-session; the largest remaining ECS-migration commitment.
2. **Value-first (A → B, optionally C)** — dissolve the subclasses + side-quests (the real ECS win),
   land a spawn-spec, and treat Phase D as a separate optional later mechanical pass. Stops before the
   200-file churn while capturing everything structurally valuable.
3. **Proving slice (B1 only)** — componentize `DroneHubUnit`, then reassess appetite with a concrete feel.

`B1` (DroneHubUnit → `HUB_STATE`) is on the critical path of **every** option that does anything, so
it is the correct first slice regardless of where the line lands.

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
