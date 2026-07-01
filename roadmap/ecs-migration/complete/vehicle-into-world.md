# Convoy `Vehicle` into the EntityWorld — one world, not three

> **STATUS: ✓ SHIPPED — DONE (2026-07-01).** All four phases landed; `Vehicle.java` is
> deleted and the convoy vehicle is a world entity like every other unit. Commit chain:
> Phase 1 `321cc047` → 2 `730713d6` → 3 `963d7987` → 4a+4b `1e128ce0` → 4c `88bf85c6` →
> 4d-1 `80d2e55d` → 4d-2 `f1ad8753` (the deletion) → javadoc sweep `35840353`. Both 4d
> critiques cleared clean. This folded the live convoy `Vehicle` — the **last non-ECS
> storage space** in the battle tier — into the one archetype `EntityWorld`, following the
> shipped **air-into-world** template
> ([`../../air/air-entities-into-world.md`](../../air/air-entities-into-world.md)).
> Read [`../overview.md`](../overview.md) for the arc framing and
> [`../archetype-storage.md`](../archetype-storage.md) for the engine.

## The gap

The battle tier has **three** live entity spaces after the air migration closed one:

- **Ground units** — `Entity`, id-minted by `UnitRosterService.allocate`, stored in the
  dense roster **and** the archetype `EntityWorld` (`{IDENTITY, POSITION, HEALTH, …}`).
- **Air craft** — world entities, id-minted by `UnitRosterService.allocateAir`,
  world-resident but **disjoint** from the dense roster (`{AIR_IDENTITY, KINEMATICS,
  SHUTTLE_MISSION, APPEARANCE}` + optional FX/turrets). **Shipped.**
- **Convoy vehicles** — `Vehicle` (trucks/APCs), a fat POJO id-less and stored in
  `GroundSystem.List<Vehicle>`. A *third* storage home the air analog already closed on
  its side. **This epic.**

`Vehicle` is the ground twin of the deleted `Shuttle`: a lifecycle state machine
(`PENDING→INCOMING→LANDED→OVERWATCH→DEPARTING→GONE`), a `GroundBody` pose (the ground
`AirBody` analog — same `x`/`y`/`facingDegrees` field names), a `VehicleController`
motion owner, inline turret state, deboard config, and debug history buffers. It is a
world entity in all but storage.

### Not in scope: `MapVehicle`

`MapVehicle` (the `sim.getVehicles()` list) is **static map decoration** — a `VehicleKind`
anchored to a cell whose footprint `BattleSetup` flags non-walkable *before the sim
exists*. It has no id, never ticks, carries no mutable state, and only the renderer reads
it. Folding it into the world would be pure ceremony (a sprite masquerading as an entity)
with zero sim benefit — the [[feedback_gutcheck_debug_before_migrate]] "not everything
list-shaped is migration debt" rule. Leave it a render-only list. This epic is the **live
convoy `Vehicle`** (`sim.getConvoyVehicles()` / `GroundSystem`) only.

## Why this is worth doing (and what it is NOT)

Honest value framing, same discipline as the systems-to-columns terminus:

- **This is storage-topology / uniformity value, NOT perf.** N≈1–4 vehicles, ticked
  **serially** in the `GROUND_SYSTEM` phase (not the parallel dispatch). There is no
  frame-time win here.
- **But it closes a real structural asymmetry** the migration's own goal names ("one
  `EntityWorld` holds all per-unit state") — the air side proved the pattern works and is
  wanted; ground is the lone straggler. That puts it in the **build-the-storage-core-right**
  carve-out ([[feedback_storage_foundation_build_right]]), not the don't-over-build
  caution ([[feedback_ship_then_optimize]]) that governs perf work.
- It follows a **proven, five-phase template** with all forks already decided on the air
  side — low design risk.

## Component design

Mirror the air layout: dissolve the fat handle, **wrap big sub-states in OBJECT columns**
(not shred to float columns — tiny population, shared mutable POJOs, the
CRASHING/MECH_LOADOUT/`AirBody` precedent). Ground components are **separate from air's**,
not a generalization of `KINEMATICS` — the precedent is `AIR_IDENTITY` deliberately kept
disjoint from grid `IDENTITY` rather than widening it, and `GroundBody`/`AirBody` share no
base type.

| Component | id | kind | payload |
|---|---|---|---|
| `GROUND_IDENTITY` | 23 | OBJECT,OBJECT | `VehicleType`, `Faction` (separate from grid `IDENTITY`/`AIR_IDENTITY`) |
| `GROUND_KINEMATICS` | 24 | OBJECT | the existing `GroundBody` (`BicycleBody` today; shared with the renderer + turret loop — aliasing gives zero reader churn) |
| `GROUND_TURRET` | 25 | OBJECT | *(optional, presence == "armed")* a `GroundTurret` bag (facing/cooldown/ammo/target/burst). The `AIR_TURRETS` precedent. Only the APC has one; the truck carries no `GROUND_TURRET`. **Shipped Phase 3** as a separate component (not folded into the mission bag). |
| `VEHICLE_MISSION` | 26 | OBJECT | *(Phase 4)* a `VehicleMission` bag (extracted from `Vehicle`'s inline lifecycle state). Liveness is `mission.state == GONE`, no `HEALTH` (vehicles have no hp) |

Ground archetype: `{GROUND_IDENTITY, GROUND_KINEMATICS, VEHICLE_MISSION}` + optional
`GROUND_TURRET`. **No** `POSITION`/`HEALTH`/`COMBAT`/`MOVEMENT`/`AI_STATE` — so every grid
system (occupancy, pathfinding, spatial index, fog, `UnitUpdateSystem`) skips vehicles for
free by membership-narrowing, exactly as it skips air. (Vehicles drive in fractional world
space along roads and never reserve grid cells today — disjoint-no-POSITION matches current
behavior.)

`groundCraft` query = `{GROUND_IDENTITY, GROUND_KINEMATICS, VEHICLE_MISSION}` (registered
in Phase 2, the query the renderer/consumers materialize a `long[]` snapshot from).

## Access model — a `ConvoyService`, not `World` delegators

The air migration routed component access through the `World` facade
(`world.setMission(id)`, `world.kinematics(id)`). That predates the **World-facade
deprecation** ([[feedback_world_facade_deprecated]]): a NEW `world.<x>(id)` for a migrated
field is now a regression. So ground component access lands on a **`ConvoyService`** (data
owner: owns the vehicle-id `List<Long>` backbone + by-id accessors + `spawn`/`reap`),
constructor-injected, reached via `sim.convoy()` / `roster.convoy()`. `GroundSystem` stays
the **System** (stateless per-tick lifecycle logic) reading the service — the
Services-own-state / Systems-consume split ([[battle_services_systems]]). This is cleaner
than air, where `AirSystem` conflated owner + processor.

## Id authority — the shared-mint trap

`UnitRosterService.allocateVehicle(archetype)` mints from the **single `nextId`** authority
(shared with `allocate` and `allocateAir`) and adopts via `createEntity(id, archetype)`
**world-only — no dense `Entity[]` insert, no `unitIndex.add`**. Self-mint
`createEntity(comps)` is unsafe (bumps `nextEntityId`, not `nextId` → later ground
`allocate` collision — the same trap `allocateAir`'s javadoc calls out).

## Phases (each leaves build + tests green)

1. **Foundation (additive). ✓ SHIPPED (2026-07-01).** Register `GROUND_IDENTITY` (23) +
   `GROUND_KINEMATICS` (24); add `UnitRosterService.allocateVehicle(archetype)` (shares
   `nextId`, world-only). Nothing calls `allocateVehicle` yet except the test.
   `VehicleEntityAllocationTest` (3) mirrors `AirEntityAllocationTest`: shared monotonic
   mint (vehicles interleave ground ids, no collision), world-resident but absent from
   the dense roster (`getOrNull`→null, `liveCount()`==0, `isAliveById`==false), and the
   ground OBJECT columns round-trip by id. Additive, suite green. `ConvoyService` is
   **deferred to Phase 2** — no service before it has a consumer
   ([[feedback_ship_then_optimize]]); the test seeds/reads columns via `entityWorld`
   directly.
2. **Adopt (serial, aliasing). ✓ SHIPPED (2026-07-01).** `Vehicle` gets `entityId`;
   `ConvoyService` (in `battle.sim`, owned by the roster, reached via `roster.convoy()`)
   is its own first consumer — `spawn(v)` mints via `allocateVehicle({GROUND_IDENTITY,
   GROUND_KINEMATICS})` and seeds the columns with the **same** `VehicleType`/`Faction`/
   `GroundBody` instances the handle holds (aliasing → all consumers compile unchanged),
   stamps `v.entityId`; `despawn(id)` destroys the entity. `GroundSystem.add` calls
   `convoy.spawn`; the `DEPARTING→GONE` transition calls `convoy.despawn` (the one GONE
   site — serial, so `destroy` at the barrier needs no `CommandBuffer`; the GONE handle
   lingers inert in the list). `ConvoyServiceTest` (4) proves aliased adoption,
   world-resident-but-off-roster, has-gated reads, destroy-drops-columns.
   **The `VehicleMission` bag extraction + `VEHICLE_MISSION` + `groundCraft` query are
   deferred to Phase 4** — extracting the bag now, then re-pointing every reader at
   dissolution, would touch each reader twice; the handle stays authoritative for
   lifecycle state until dissolution, which is exactly when its readers get migrated to
   ids. So the world entity carries identity + live pose only for now (dormant until a
   consumer switches to it, the air-Phase-2 pattern). A background critique verified no
   correctness defects; its one actioned finding is the fail-loud double-adopt guard on
   `spawn` (the list-removal + reap-sweep findings are Phase-4-coupled — see Phase 4).
3. **Turret onto a component. ✓ SHIPPED (2026-07-01).** Extracted the 7 inline `turret*`
   fields into a `GroundTurret` bag (`battle.vehicle`) held in a new `GROUND_TURRET`
   OBJECT component (id 25, **presence == armed** — the `AIR_TURRETS` precedent; an unarmed
   truck carries none). `Vehicle.turret` is `null` for `!hasTurretWeapon()`, else the bag;
   `ConvoyService.spawn` conditionally includes `GROUND_TURRET` in the archetype and seeds
   it (aliased), with a has-gated `convoy.turret(id)` accessor. `GroundSystem.tickVehicleTurrets`
   now drives the turret **by id** (`convoy.turret(v.entityId)`) — the first tick consumer
   of a ground-craft component, so the world entity is no longer dormant. The three
   presentation readers stay on the aliased handle bag (`v.turret.*`, a field rename) and
   migrate to by-id at Phase 4: `ConvoyRenderSystem` + `VehicleStateDumper` null-guard the
   bag (their gates — `turretFrame >= 0`, ungated dump — are broader than `hasTurretWeapon`),
   `BattleRenderer`'s debug overlay reads it under the `hasTurretWeapon` gate. `ConvoyServiceTest`
   +2. Full suite green.
4. **Dissolve the handle — decomposed into 4a–4d, ALL SHIPPED ✓ (2026-07-01). `Vehicle.java` deleted.**
   - **4a ✓** promoted `Vehicle.State` → top-level `VehicleState` (outlives the handle).
   - **4b ✓** re-keyed vehicle selection off positional index onto **entity id**
     (`Selection.selectedVehicleId`; `WorldPicker`/`BattleScreen`/`BattleRenderer` resolve by
     id) and added the end-of-tick `reapGoneVehicles()` sweep (despawn + remove GONE) — closes
     the Phase-2 critique A/B (list-removal was blocked on the positional-index coupling).
   - **4c ✓** converted `GroundSystem`'s `List<Vehicle>` → `List<Long> vehicleIds`; the handle
     lives in a new `VEHICLE_MISSION` component (id 26, always present), reached via
     `convoy.vehicle(id)`. `getVehicles()` materializes from ids; tick/turret/reap resolve by
     id. **Vehicles are now fully world-resident — no separate handle-list storage (the
     epic's PRIMARY goal).** Consumers unchanged.
   - **4d (finale, dissolution) — split into 4d-1 / 4d-2, each green.** The air end-state
     precedent (verified against `ShuttleMission`): the mission bag is **pure data** — no
     `AirBody`, no `ShuttleType`/identity, no `entityId`, no controller; consumers resolve
     those by id off a `long[]` snapshot (`getAirEntityIds()`). Ground mirrors it, but
     **`ConvoyService`-direct, not through the deprecated `World` facade** the air renderer
     still uses ([[feedback_world_facade_deprecated]]).
     - **4d-1 ✓ SHIPPED (`80d2e55d`)** — migrated the read-only `getConvoyVehicles()`
       consumers onto entity ids: `BattleSimulation.convoy()` + `getConvoyVehicleIds()`
       (backed by `GroundSystem.vehicleEntityIds()`); `ConvoyRenderSystem`, `WorldPicker`,
       `BattleRenderer` (docking-paths + selected-vehicle debug), `VehicleStateDumper`,
       `BattleScreen` F5 walk `long[]` ids and resolve type/faction/body/turret via
       `convoy.vehicleType/faction/body/turret(id)`; mission-ish reads stay on the
       `convoy.vehicle(id)` handle. `Vehicle` untouched — isolates the broad presentation
       churn. (`TurretFireSystem` is a **non**-consumer — it targets grid `Entity`s by id.)
     - **4d-2 ✓ SHIPPED (`f1ad8753`)** — extracted the pure-data `VehicleMission` bag (state,
       countdowns, in/outbound paths, LZ, `marinesRemaining`, overwatch, `marineLoadout`,
       `deboardUnitType`, `squadId`, route inputs, `controller`, debug history) with **no**
       `type`/`faction`/`body`/`turret`/`entityId` (the `ShuttleMission` shape); re-pointed
       `VEHICLE_MISSION`'s payload → `VehicleMission` (`convoy.vehicle(id)` → `convoy.mission(id)`).
       `ConvoyService.spawn` is a **factory** (builds body + turret from the variant, seeds
       columns, returns id — the double-adopt guard fell out, missions are single-use).
       `GroundSystem` tick/deboard/reap/turret resolve mission + identity + kinematics by id;
       `add(type, faction, mission)`. `VehicleController` holds `VehicleMission`/`GroundBody`/
       `VehicleType` refs (a ref-swap, **not** statelessification). `getConvoyVehicles()` →
       `getConvoyVehicleIds()` + `convoyMission(id)` on `BattleView`;
       `addConvoyVehicle(type, faction, mission)`. `ConvoyMeans` + `BattleSetup` build the
       mission; `ConvoyServiceTest` reworked to the factory shape. **`Vehicle.java` deleted.**
       Full suite green; a background critique cleared all six vectors (field-swap aliasing,
       marinesRemaining seeding, the removed guard, null-invariants, sole `BattleView`
       implementor, factory teleport). Stale `{@link Vehicle}` javadoc swept (`35840353`).
     - **Follow-up (NOT this epic):** statelessify `VehicleController` into components + a
       system — the air side has no per-craft controller (stateless `AirSteeringSystem` over
       `AirBody`). A separate epic; 4d-2 keeps the controller stateful, only swapping its back-ref.
   - **This phase also unblocks the reap sweep + list-removal (Phase-2 critique A/B).**
     `WorldPicker`/`Selection`/`BattleScreen` select a vehicle by its **positional index**
     into `getConvoyVehicles()` (`getSelectedVehicleIdx()` → `.get(idx)`), so Phase 2
     deliberately did NOT remove GONE vehicles from the list (index-shift would re-target
     the selection) and inlined `despawn` at the single `DEPARTING→GONE` site instead. Once
     selection is keyed by entity id here, replace the inline despawn with an end-of-tick
     `reapGoneVehicles()` sweep that despawns **and** removes GONE handles (the air
     `reapGoneCraft` shape) — bounding the list and covering any future second terminal path
     in one place.

## Watch-items (carried from the air critic)

- **`getObject` throws on absent id** (unlike `ComponentStore.get`→null): `has`-gate every
  vehicle read, including out-of-tick UI reads (picker, dumper, render).
- **Serial subsystem** → structural change (`destroy`) is safe at the end-of-tick barrier
  with gather-then-apply; do **not** reach for `CommandBuffer` (that's for mid-`Query`-walk
  destroys, which this isn't).
- **`GroundBody` in `GROUND_KINEMATICS` is shared mutable** (aliased by the renderer +
  turret loop). Accepted for zero-churn — the column is storage, not sole owner, through
  the aliasing phases.
- **`GroundSystem.add` currently constructs the `VehicleController`** (`v.controller = new
  VehicleController(v, navigation)`). The controller holds a back-ref to `Vehicle`; when the
  handle dissolves (Phase 4), the controller reads pose/mission via the service or keeps its
  own refs. Sequence the controller's `Vehicle` back-ref removal into Phase 4.
- **Selection is by list-position, not id** — `WorldPicker`/`Selection`/`BattleScreen`
  hold a positional index into `getConvoyVehicles()`. This is why GONE handles must linger
  in the list (removing them shifts indices) until Phase 4 re-keys selection by entity id.
  Any pre-Phase-4 change that removes from the list will silently mis-target the selection.
- **`ConvoyService.spawn` is fail-loud on double-adopt** (a second `spawn` on the same
  handle would orphan the first world entity → leak). Parity with `allocate`'s guard.
- **Deboard spawns marines into the dense roster** mid-tick (`addUnitSink` → `addUnit`);
  that path is unchanged — vehicles are disjoint, but their *product* (militia) is a normal
  ground unit.
- **Stale-doc lag** — expect `VehicleController`/planner javadocs to reference the `Vehicle`
  handle after dissolution; sweep them (the `AirProvider` stale-`Shuttle`-doc wart).

## Acceptance criteria

- Convoy vehicles are world entities: one id space (shared `nextId`), one `EntityWorld`, no
  `GroundSystem.List<Vehicle>` of handle objects — the backbone is a `List<Long>` of ids.
- `Vehicle.java` is deleted; its state lives in `{GROUND_IDENTITY, GROUND_KINEMATICS,
  VEHICLE_MISSION}` (+ optional `GROUND_TURRET`).
- Component access is Service-direct (`ConvoyService`); **no** new `World.<x>(id)` delegator.
- Grid systems still skip vehicles for free (occupancy/vision/spatial-index membership
  never matches the ground-craft archetype) — a test asserts a spawned vehicle is absent
  from the dense roster + spatial index.
- `MapVehicle` untouched (explicitly out of scope).

## Cross-references

- [`../../air/air-entities-into-world.md`](../../air/air-entities-into-world.md) — the
  shipped template this mirrors phase-for-phase (all forks decided there).
- [`../overview.md`](../overview.md) — the arc; this is its last storage gap.
- [`../archetype-storage.md`](../archetype-storage.md) — the `EntityWorld`/`Query` engine.
- Memory: [[air_vehicle_kinematics]] (GroundBody/AirBody sibling abstraction),
  [[ground_vehicle_kinematics]] (GroundBody + PurePursuit), [[battle_services_systems]]
  (Service owns state, System consumes), [[feedback_world_facade_deprecated]] (Service-direct,
  no World delegator), [[feedback_storage_foundation_build_right]] (this is storage-core,
  build the end-state), [[feedback_components_by_capability_not_store]] (capability-presence).
