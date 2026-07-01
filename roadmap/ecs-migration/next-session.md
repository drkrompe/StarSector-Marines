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
3. **Convoy ground `Vehicle` never entered the world** — a plain POJO in
   `GroundSystem.List<Vehicle>` (a third storage space; the air analog closed, ground
   didn't).
4. **Authored-appearance is corpse-only** — SPRITE is real, but live units derive
   facing per-frame; no FacingSystem / AnimationSystem / FX child entities.

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

**`Entity` now carries NO mutable per-unit state** — it's the `long` id + immutable
identity (`id`/`faction`/`type`/`rng`) + write-only `seed*` construction inputs + the
path/burst methods. Every migrated field's by-id access is Service-direct
([[feedback_world_facade_deprecated]]).

**ACTIVE — fold convoy `Vehicle` into the world** ([`stories/vehicle-into-world.md`](stories/vehicle-into-world.md)):
the live convoy `Vehicle` POJO in `GroundSystem`'s `List<Vehicle>` is the **last non-ECS
storage space** in the battle tier — a third storage home the air analog (shuttles/drones
as `{AIR_IDENTITY, KINEMATICS, …}` entities) already closed on its side. Bring ground
vehicles into the one `EntityWorld` as a ground archetype, following the shipped air
template phase-for-phase. (`MapVehicle` — static render-only map decoration — is explicitly
out of scope.)

- **Phase 1 ✓ SHIPPED (2026-07-01):** `GROUND_IDENTITY` (23) + `GROUND_KINEMATICS` (24)
  registered; `UnitRosterService.allocateVehicle` mints from the shared `nextId`, world-only
  (mirrors `allocateAir`); `VehicleEntityAllocationTest` (3). Additive, suite green.
- **Phase 2 ✓ SHIPPED (2026-07-01):** `Vehicle.entityId` + `ConvoyService` (`battle.sim`,
  owned by roster, `roster.convoy()`). `spawn(v)` adopts as a world entity aliasing the
  handle's type/faction/body into `{GROUND_IDENTITY, GROUND_KINEMATICS}`; `despawn(id)`
  destroys it. `GroundSystem.add`→`spawn`, `DEPARTING→GONE`→`despawn` (serial; no
  CommandBuffer). Handle stays authoritative for lifecycle. `ConvoyServiceTest` (4). Full
  suite green. **The `VehicleMission` bag + `VEHICLE_MISSION` + `groundCraft` query moved to
  Phase 4** (dissolution) — extracting the bag now then re-pointing readers at dissolution
  would touch each reader twice.
- **NEXT — Phase 3:** turret state (`turretFacingDeg`/cooldown/ammo/target/burst, inline on
  `Vehicle`) → a `GROUND_TURRET` component (presence == armed, APC only) or folded into the
  bag; `tickVehicleTurrets` reads by id. Then Phase 4: extract `VehicleMission`, migrate the
  `getConvoyVehicles()` consumers (render/picker/dumper/BattleView) to `groundCraft` query +
  by-id `ConvoyService` (add `sim.convoy()`/`BattleView.convoy()` there), delete `Vehicle`.

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
4. **Fold convoy `Vehicle`/`MapVehicle` into the world as a ground archetype** — L.
   **← ACTIVE (2026-07-01).** The last non-ECS storage space; follows the shipped
   air-into-world template. [`stories/vehicle-into-world.md`](stories/vehicle-into-world.md).
5. ~~Decide `CommandBuffer`'s fate~~ — **DECIDED (keep):** committed engine infra;
   the systems-half epic is its consumer.
6. ~~Combatant-narrow COMBAT membership~~ — **SHIPPED (`74c565d1`):** "has COMBAT" now
   defines a combatant.
7. **Live authored-appearance** (FacingSystem / ANIMATION / FX child entities) — epic,
   lower leverage; sequence after #1.

## Recent ECS-track commits

```
730713d6 ecs-migration: vehicle-into-world Phase 2 — adopt vehicles as world entities
321cc047 ecs-migration: vehicle-into-world Phase 1 — ground archetype foundation
1d5ce956 docs(ecs-migration): close systems-to-columns at terminus, open vehicle-into-world
6f528fc8 ecs-migration: fold Entity.deathPoseIdx into the DeathEvent (slice 8, FINALE)
7537de69 ecs-migration: move Entity task fields onto a TASK component (slice 7c)
84d0625c ecs-migration: lift the reprio gate off Entity into HitResponseSystem (slice 7b)
```

Older history is in git + the `complete/` docs. Sibling tracks (battle-render,
goap, campaign) interleave on HEAD.

## Sanity check before resuming

- `gradlew.bat compileJava` clean, full suite green (`:test` BUILD SUCCESSFUL at
  vehicle-into-world Phase 2; `ConvoyServiceTest` +4, `VehicleEntityAllocationTest` +3).
- `git log --oneline -5` shows `6f528fc8` (slice 8 — deathPoseIdx fold, epic finale) or
  your own recent work at the top.
