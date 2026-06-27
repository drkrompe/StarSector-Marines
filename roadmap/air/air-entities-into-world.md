# Air entities into the EntityWorld — one world, not two

> **STATUS: COMPLETE (2026-06-27).** All five phases shipped — air craft are world
> entities (id + components), the `Shuttle` handle / `nextAirId` / air
> `ComponentStore`s are all gone. See the Progress section below for the commit
> chain. This doc is retained as the design record.

> **Design decision (2026-06-25): there should not be two separate entity
> worlds.** Air craft and battle units belong in the *same* archetype
> `EntityWorld`; "air vs ground" is a difference in *components*, not a reason
> for a separate id space + storage. This doc captures that decision and the
> epic that realizes it. **Gated on `ecs-migration` step 4** (the sim must own
> the one world before air can be adopted into it).

## The decision

Today the battle tier has **two** entity spaces:

- **Battle units** — `Entity` (+ `Drone`/`MapTurret`/`DroneHubUnit`/…), id-minted
  by `UnitRegistry`, stored in the archetype `EntityWorld` (after the
  `ecs-migration`). Grid-positioned (`POSITION` = `int cellX/cellY`), with
  `HEALTH`/`COMBAT`/`MOVEMENT`/`AI_STATE`/etc.
- **Air craft** — `Shuttle` (non-`Entity`), id-minted by a *private*
  `AirSystem.nextAirId`, stored in `AirSystem.List<Shuttle>`, with its own
  components (`ThrusterFx`, `AirTurrets`) in standalone `ComponentStore`s keyed by
  the air id. Continuous-positioned (`AirBody` = `float x/y` + facing + velocity).

This split is an **incremental-development artifact, not a principled boundary** —
three facts say so:

1. **Drones already straddle.** `Drone extends Entity` (it lives in the battle
   world, is targetable, has `HEALTH`/`COMBAT`) *and* carries an `AirBody`
   (continuous, free-rotating). The grid cell is *synced from the body each
   tick* ([[air_unit_render_sync]]) for targeting/vision. So the battle world
   **already hosts a flier** — the grid/air line isn't the entity boundary.
2. **The real seam is the position model, not entity-ness.** Battle `POSITION`
   is discrete `int` cells; air is continuous `float` `AirBody`. And `AirBody`
   is a **POJO field**, not an ECS component, on both `Drone` and `Shuttle`.
   That — *how is position represented, and is kinematics a component yet* — is
   the actual difference.
3. **The code already states the intent.** `Shuttle`'s own doc: *"The first step
   of the air tier's air-entity-composition migration — air craft as real
   entities,"* and the separateness is rationalized only as *"shuttles fly in
   fractional world space … and don't pathfind or fight on the grid."* That's a
   *component* distinction (no `GRID_POSITION`, no combat/squad components), not a
   separate-world one.

In the ECS end-state (the Artemis model the migration follows), a shuttle is just
**an entity with `{Identity, Kinematics, Sprite, ShuttleMission, AirTurrets?,
ThrusterFx?}`** and *no* grid/combat components. One world; the component set is
the type. `nextAirId` and the parallel `ComponentStore`s go away.

## Why it's gated on ecs-migration step 4

Air-unification needs a **single, sim-owned `EntityWorld`** to adopt air entities
into. Until `ecs-migration` step 4 dissolves `UnitRegistry` (hoisting world
ownership from the registry up to the sim), there is no neutral one-world home —
the world is owned by `UnitRegistry` "for the transition." So **step 4 is the
enabling step**, and this epic is its natural successor, not a competitor.

(The air `ComponentStore`s — `ThrusterFx`, `AirTurrets` — are therefore the one
legitimate reason `ComponentStore<T>` survives the end of `ecs-migration`: they
store *air* components, and air isn't in the world yet. They die in this epic.)

## Epic shape (the work, roughly)

1. **Position-model components.** Decide the representation: a continuous
   `KINEMATICS`/`AIR_BODY` component (float pos + facing + velocity) distinct from
   the grid `POSITION` (int cell). Grid-dwellers have `POSITION`; fliers have
   `KINEMATICS`; a drone has **both** (its cell derived from the body, as today).
   `AirBody` stops being a POJO field and becomes the component payload (or is
   decomposed into float columns).
2. **Adopt air craft as world entities.** `Shuttle` (and planned fighters) get a
   real world `entityId` via `createEntity`; drop `AirSystem.nextAirId` and the
   disjoint namespace. The `List<Shuttle>` iteration becomes a world `Query`
   (`{KINEMATICS, ShuttleMission}` or similar). Shuttle's POJO fields that are
   really per-entity state migrate to components as motivated (the
   `secondary`/`mech` precedent).
3. **Re-key the air FX onto world components.** `ThrusterFx` and `AirTurrets`
   become world components (OBJECT columns, like `CRASHING`/`MECH_LOADOUT` got),
   keyed by the unified id. `ThrusterFxSystem` walks a query. The two air
   `ComponentStore`s — and then `ComponentStore<T>` itself — are deleted.
4. **Reconcile the shared systems.** Render, death, and FX can treat air + ground
   uniformly where it makes sense (one `Sprite`/`RenderPosition` story); the
   grid-only systems (pathfinding, occupancy, squad/GOAP) simply don't match the
   air archetypes (no `GRID_POSITION`/`MOVEMENT`/`AI_STATE`), so they skip air for
   free — the membership-narrowing pattern already established.

This composes with the `air/` track's **hull-extraction** work
([`hull-extraction.md`](hull-extraction.md)): that decides the *kinematics +
geometry data model* (vanilla hull → `AirBody`); this decides *where the entity
lives* (the one `EntityWorld`) and *how its capabilities are stored* (components,
not a side `ComponentStore`). They meet at "air craft as real entities."

## Implementation plan (LOCKED 2026-06-27)

Designed via an ultracode understand-workflow (6 dimension maps → synthesis →
adversarial critique). The critique caught that a *drone-first* pilot is not
green (dropping `Drone.body` breaks 4 readers + hits the deferred-spawn seeding
problem), so the pilot is **shuttle-first** (the handle aliases the same
`AirBody` → zero reader churn), with the drone fold as its own scoped slice.

### User decisions (the forks)

- **Dissolve `Shuttle` fully** into components + an air `Query` — migrate all 7
  `getShuttles()` consumers, add `world.destroy(id)` at terminal `GONE`, delete
  the `Shuttle` handle class. (End-state, not a thin handle.)
- **Dedicated `APPEARANCE` component now** for air render-state (`altitudeT`,
  `flightPhase`; `scaleMult` derived) — not dumped on the mission bag. (NB: the
  air *draw path* — a whole rotated hull texture vs ground's batched facing
  frames — stays the separate unified-sprite-registry epic; this is the authored
  render-*state* component, the [[feedback_appearance_authored_component]] pattern.)
- **Transport-only air this epic** — no anti-air. `mission.hp` stays on the
  `SHUTTLE_MISSION` payload; **no `HEALTH`/`COMBAT`/vision** on air. Keeps air
  cleanly world-only so membership-narrowing skips it everywhere.

### Component design

| Component | id | kind | payload |
|---|---|---|---|
| `KINEMATICS` | 12 | OBJECT | the existing `AirBody` (shared with `Drone`/`FlybyOverlay`; zero churn to `AirSteeringSystem`/`ThrusterDemand`/renderers) |
| `AIR_IDENTITY` | 13 | OBJECT,OBJECT | `ShuttleType`, `Faction` (separate from grid `IDENTITY`, which is consumed as a concrete `UnitType`) |
| `SHUTTLE_MISSION` | 14 | OBJECT | the existing `ShuttleMission` bag verbatim (`mission.hp` lives here — air liveness is `mission.state`, not `HEALTH.hp`) |
| `THRUSTER_FX` | 15 | OBJECT | `ThrusterFx` (re-keyed off its `ComponentStore`) |
| `AIR_TURRETS` | 16 | OBJECT | `AirTurrets` (presence == "armed") |
| `APPEARANCE` | 17 | FLOAT,FLOAT | `altitudeT`, `flightPhase` (authored render-state; lands in the dissolution phase — FLOAT can't alias the handle, unlike the OBJECT components) |

Air archetype: `{AIR_IDENTITY, KINEMATICS, SHUTTLE_MISSION, APPEARANCE}` +
optional `THRUSTER_FX`/`AIR_TURRETS`. **No** `POSITION`/`HEALTH`/`COMBAT`/
`MOVEMENT`/`AI_STATE`.

**KINEMATICS = OBJECT (not decomposed floats):** `AirBody` is already a clean
POJO, shared by `Drone` + the non-entity `FlybyOverlay` (the class must survive)
+ held by `CrashingComponent`; air is a tiny population so float-column cache
density buys nothing, and decomposition would churn all 7 `AirSteeringSystem.steer`
call sites. The CRASHING/MECH_LOADOUT precedent. **`has`-gate every air read** —
`EntityWorld.getObject` throws on absent ids (unlike `ComponentStore.get`→null).

**One id-mint:** `UnitRosterService.allocateAir(archetype)` mints from the single
`nextId` authority and adopts via `createEntity(id, archetype)` **world-only — no
dense `Entity[]` insert, no `unitIndex.add`**. Delete `AirSystem.nextAirId`.
(Self-mint `createEntity(comps)` is unsafe — bumps `nextEntityId` not `nextId` →
later ground `allocate` collision.)

### Phases (each leaves build + tests green)

1. **Foundation (additive).** Register `KINEMATICS`/`AIR_IDENTITY`/
   `SHUTTLE_MISSION`/`THRUSTER_FX`/`AIR_TURRETS` (the OBJECT set) + `has`-gated
   `World` accessors + `UnitRosterService.allocateAir`. Nothing calls it yet;
   pure registration. Focused test: `allocateAir` mints monotonically, shares
   `nextId` with `allocate` (no collision), and the entity is world-resident but
   absent from the dense roster (`getOrNull`→null, not in `liveCount()`).
2. **Adopt shuttles (serial path, aliasing).** `AirSystem.add` →
   `allocateAir({AIR_IDENTITY, KINEMATICS, SHUTTLE_MISSION})`, seed the columns
   with the **same** `AirBody`/`ShuttleMission`/type/faction instances the
   `Shuttle` handle holds (aliasing → all 7 consumers compile unchanged). Delete
   `nextAirId`. Closes the dual-mint trap.
3. **Re-key air FX + delete `ComponentStore<T>`.** `ThrusterFx`/`AirTurrets` →
   OBJECT columns (`has`-gated reads); `ThrusterFxSystem` walks the world; copy
   `DroneCrashSystem` as the attach/table-walk template. Delete `ComponentStore`
   + its test; rewrite `ThrusterFxSystemTest` against a real world harness.
4. **Drone KINEMATICS fold (scoped slice).** Add `Entity.seedBody` (mirror
   `seedSecondaryWeapon`); `allocate` conditionally adds `KINEMATICS` + seeds it;
   drop `Drone.body`; reroute `DroneSwarmAction` + `DroneRenderSystem` +
   `DroneCrashSystem.onDeath` + the `Drone` ctor; keep the body→cell/render sync
   **after** `steer` ([[air_unit_render_sync]]). Decide `KINEMATICS` corpse-mask
   membership (crash needs it post-death).
5. **Dissolve the handle + render-state + death.** Register `APPEARANCE`; move
   `altitudeT`/`flightPhase` off `Shuttle` into it (and the render/audio reads to
   `world` by id); migrate the 7 `getShuttles()` consumers to an air `Query`;
   `world.destroy(id)` at terminal `GONE` (one coherent change — supersedes the
   per-component removes; the multi-sortie re-arm must NOT destroy); delete the
   `Shuttle` handle class. Confirm occupancy/vision/win-counts never see air.

### Progress

- **Phase 1 ✓ (`02b5c55f`)** — air component types + `World` accessors +
  `UnitRosterService.allocateAir` registered; `AirEntityAllocationTest` (4) proves
  the single-mint + world-only invariants. Additive, suite green.
- **Phase 2 ✓ (`1e3f92fd`)** — `AirSystem.add` adopts shuttles into the world
  (shared mint, columns alias the handle's `AirBody`/`ShuttleMission`/type/faction);
  `nextAirId` deleted, dual-mint trap closed. Behavior-preserving, suite green at 775.
- **Phase 3 ✓ (`1ee6e8a0`)** — `ThrusterFx`/`AirTurrets` re-keyed off the two
  standalone `ComponentStore`s onto the world's `THRUSTER_FX`/`AIR_TURRETS` OBJECT
  columns (has-gated reads via the `World` facade). `ThrusterFxSystem.advance` now
  takes `EntityWorld` + `BattleComponents` (the `DroneCrashSystem` precedent —
  avoids a `battle.air.engine → battle.sim` cycle the `World` facade would add) and
  lazily attaches/drops the column. `ComponentStore<T>` was its last user →
  `ComponentStore.java` + `ComponentStoreTest.java` **deleted**, both package-info
  charters updated (archetype table = sole composition substrate),
  `ThrusterFxSystemTest` rewritten against a real world harness. The `GONE` seam
  still leaves the entity alive (Phase 5 adds `destroy`). Behavior-preserving,
  suite green at 768 (−7 `ComponentStoreTest`).
- **Phase 4 ✓ (`ffc368da`)** — drone `KINEMATICS` fold. `Entity.seedBody` (mirrors
  `seedSecondaryWeapon`) is the pre-allocate channel; `allocate` adds `KINEMATICS`
  to the spawn archetype iff `seedBody != null`, seeding the same `AirBody` instance
  (aliased, zero-churn). `Drone.body` **dropped**; `DroneSwarmAction` reads it once
  via `world.kinematics(id)` and threads it through engage/pursue/patrol (post-steer
  cell+render sync unchanged); `DroneRenderSystem` live pass reads by id.
  `KINEMATICS` kept OFF the `corpseRemove` mask (rides the transmute, like
  `CRASHING`/`MECH_LOADOUT`), so `DroneCrashSystem.onDeath` reads the body to seed
  the `CrashingComponent` then **detaches** it (lifecycle moves to the crash
  component — the `MECH_LOADOUT` survive-then-detach precedent; no `DeadBodySystem`
  change). New `DroneCrashSystemTest` case proves carry-while-alive + read-then-detach.
  Behavior-preserving, suite green at 777.
- **Phase 5 ✓ — the finale (the `Shuttle` handle is gone).** Shipped as five
  build-green sub-slices:
  - **5a (`118a08e8`)** — registered the `APPEARANCE` component (FLOAT,FLOAT =
    altitudeT/flightPhase, id 17) + has-gated `World` accessors; added it to the
    air spawn archetype, seeded at spawn. Additive, nothing read it yet.
  - **5b (`dd6b5549`)** — moved the render-state authority off `Shuttle` into
    `APPEARANCE`. New `AirAppearance` helper owns the air visual-feel constants +
    the pure derivations (`scaleMult`/`visualAltitudeOffsetCells`/
    `engineIntensity`); `scaleMult` is derived on read, never stored. Faithful
    (scaleMult at altitudeT=0 == 1.0; phase integrator unchanged).
  - **5c (`b70955ac`)** — promoted `Shuttle.State` → top-level `ShuttleState`
    (it had to outlive the handle). Mechanical rename across 7 files.
  - **5d-i (`c5e94f9b`)** — flipped the external API to entity ids:
    `getShuttles()`→`getAirEntityIds()` (walks the `airCraft` query),
    `addShuttle(Shuttle)`→`spawnShuttle(...)`→id, `getThrusterGlow`/
    `getAirTurretMounts`/`attachAirTurrets` retyped to `long`. All 7 consumers +
    the 4 construction sites + `CarrierDescentPlugin`'s own drop list migrated to
    ids; `Shuttle` became AirSystem-internal. Shared helpers extracted off the
    handle: `MountedTurret.worldX/worldY`, `ShuttleMission.isVisible()`. Audio
    loop key + light id rebased onto the (stable) `AirBody` instance / entity id.
  - **5d-ii (`f83605e4`)** — rewrote `AirSystem` internals to `List<Long>`,
    `entityWorld.destroy(id)` at terminal GONE via `reapGoneCraft()` at end of
    `tick()` (gather-then-apply; one destroy supersedes the Phase-3 per-component
    FX/turret removes — `releaseAirEntity` deleted; the reconciled GONE seam),
    moved the surviving tunables to `ShuttleMission`, and **deleted `Shuttle.java`**.
    Multi-sortie re-arm loops to PENDING (id reused), never reaped. Suite green.

**Epic complete.** Air craft are world entities — one id space, one `EntityWorld`,
no parallel handle / `ComponentStore` / `nextAirId`. "Air vs ground" is now purely
a difference in components (air has `{AIR_IDENTITY, KINEMATICS, SHUTTLE_MISSION,
APPEARANCE}` + optional FX/turrets and no grid/combat components, so every grid
system skips it for free).

### Watch-items (from the critic)

- `getObject` throws vs `ComponentStore.get`→null: `has`-gate every migrated air
  read (FX, turrets, render, **and the out-of-tick `BattleScreen` audio reads**).
- The `GONE` seam is touched by both Phase 3 (FX remove) and Phase 5 (destroy) —
  reconcile as one change so destroy supersedes the per-component removes.
- Multi-sortie re-arm reuses the entity id across sorties — never destroy+recreate.
- `KINEMATICS` is a *shared mutable* `AirBody` (aliased by handle + CrashingComponent);
  accepted for zero-churn — the column is storage, not sole owner, for now.

## Cross-references

- [`overview.md`](overview.md) — the air track; this is its storage/entity-world
  dimension.
- `roadmap/ecs-migration/` — the foundation (the archetype `EntityWorld`, the
  capability-as-component pattern, the membership-narrowing). Step 4 (dissolve
  `UnitRegistry`) is the prerequisite.
- [`hull-extraction.md`](hull-extraction.md) — the kinematics/geometry data model
  that the adopted air entities carry.
- Memory: [[air_vehicle_kinematics]], [[air_unit_render_sync]],
  [[user_artemis_ecs_framing]], [[feedback_components_by_capability_not_store]].
