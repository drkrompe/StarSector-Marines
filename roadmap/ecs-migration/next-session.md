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
8. **FiringSystem** — extract the duplicated fire mechanics (~12 GOAP postures + turret/drone
   aim variants; the canonical cooldown-gate → `fireShot` → reset → burst → reposition block,
   plus the scattered cooldown *decrement* across ~5 sites) into one System reading COMBAT.
   **Unblocked** now that entity-field-migration consolidated the COMBAT state it reads; also
   fixes the `HoldPost` double-tick cooldown bug (see `backlog.md` § Bugs) and targets the
   fattest sim CPU path. The concrete motivating case of the (closed) systems-to-columns epic.
   Design: [`stories/firing-system.md`](stories/firing-system.md). Candidate — high ready value.
9. **Identity-collapse (`Entity` handle → bare `long` id)** — dissolve the ~305-line `Entity`
   heap object (now only immutable identity + `seed*` inputs + path/burst methods) so entity =
   id everywhere; the spatial index goes id-native and **reopens systems-to-columns as a
   byproduct**. Large, foundational; touches many call sites. See
   [`spatial-index-options.md`](spatial-index-options.md). Candidate.
10. **Statelessify `VehicleController`** — turn the stateful per-vehicle controller (the last
    per-craft handle with mutable motion state) into components + a stateless system, the air
    `AirSteeringSystem`-over-`AirBody` shape. Self-contained follow-up from vehicle-into-world;
    low leverage (N≈1–4). Candidate.

## Recent ECS-track commits

```
35840353 docs(vehicle-into-world): sweep stale {@link Vehicle} javadoc after handle deletion
f1ad8753 ecs-migration: vehicle-into-world 4d-2 — dissolve Vehicle into VehicleMission (handle DELETED)
80d2e55d ecs-migration: vehicle-into-world 4d-1 — convoy read consumers to by-id
88bf85c6 ecs-migration: vehicle-into-world Phase 4c — List<Long> backbone + VEHICLE_MISSION
1e128ce0 ecs-migration: vehicle-into-world Phase 4a+4b — VehicleState + id-selection + reap sweep
963d7987 ecs-migration: vehicle-into-world Phase 3 — turret onto a GROUND_TURRET component
730713d6 ecs-migration: vehicle-into-world Phase 2 — adopt vehicles as world entities
321cc047 ecs-migration: vehicle-into-world Phase 1 — ground archetype foundation
```
(Doc hash-fill + critique micro-commits are elided from this window.)

Older history is in git + the `complete/` docs. Sibling tracks (battle-render,
goap, campaign) interleave on HEAD.

## Sanity check before resuming

- `gradlew.bat compileJava` clean, full suite green (`:test` BUILD SUCCESSFUL at
  vehicle-into-world 4d-2 / javadoc sweep). `Vehicle.java` is **gone**: convoy vehicles are
  world entities; mission state is `VehicleMission` in `VEHICLE_MISSION`, reached via
  `convoy.mission(id)`; identity/kinematics/turret are their own columns read by id.
- `git log --oneline -5` shows `35840353` (javadoc sweep) / `f1ad8753` (the deletion) or your
  own recent work at the top.
- **live authored-appearance: core loop CLOSED 2026-07-01** — Phases 1+2 shipped
  (`9f1c33f0` + `ee215e14` critique fixes + `9bd3c7fa`; suite 843 green). Remaining phases
  are art/scope-gated (Phase 3 needs walk-cycle sheets; Phase 4 scopes on its own), so the
  epic is **parked, not active**. **Next actionable candidate: FiringSystem** (item 8 —
  unblocked, high ready value, fixes the HoldPost double-tick cooldown bug); then
  **identity-collapse** (item 9), **statelessify `VehicleController`** (item 10).
- Working model note (2026-07-01): implementation delegated to Sonnet 5 subagents from
  prescriptive specs; planning/review/suite/commit on the main thread.
