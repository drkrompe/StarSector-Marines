# Next session — ECS migration handoff

Read [`overview.md`](overview.md) for the arc's framing, [`archetype-storage.md`](archetype-storage.md)
for the live engine rules, and [`complete/`](complete/) for shipped stories.

**This file is the LIVE handoff only.** Per-slice detail, commit hashes, and shipped
narrative live in the story docs + `complete/` — not here. When a slice ships, log it
in its story doc (or move the story to `complete/`) and update the *pointers* below;
don't accrete another status block.

## Where we are (2026-06-30)

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

1. **The systems are not query-shaped.** The mainline N≈200 combatant/AI/vision/squad
   loops still iterate the dense `Entity[]` and read component data **by id** (one map
   probe per field per unit). Only a few tiny/optional populations column-walk a
   `Query`. The cache-locality win is uncollected. → epic
   [`stories/systems-to-columns.md`](stories/systems-to-columns.md).
2. **`Entity` still carries live behavior fields.** The per-unit *stats* moved to
   components; what the world does **not** yet own is `role`, `assignedObjective`,
   `equipmentDropTarget`, `homeCell`, `lastReprioTickIndex`, `deathPoseIdx`. → epic
   [`stories/entity-field-migration.md`](stories/entity-field-migration.md) (active).
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

## Active track — entity-field-migration (slices 1–5 shipped)

Story: [`stories/entity-field-migration.md`](stories/entity-field-migration.md) — full
live-field inventory, slice order, and per-slice shipped log with commit hashes.

Shipped: **1** `attackCooldown`→COMBAT · **2** `moveSpeed`→MOVEMENT · **3**
`visionRange`+`airLosRadius`→new VISION component (+ fog-class rename
`VisionService`→`FogOfWarService`) · **4** `primaryWeapon`→COMBAT (OBJECT) · **5**
`squadId`→new presence-based SQUAD component (`32a00239`, `0afb3c40` docs).

**NEXT — slice 6: `role` → dispatch component** (universal). Unblocks
`systems-to-columns` Phase 2 (dispatch by presence, not enum) and is the prerequisite
to keying the spatial indices by id. Then **slice 7: the decision cluster**
(`assignedObjective` / `equipmentDropTarget` / `homeCell` / `lastReprioTickIndex` — the
thorny optional-tail), and **slice 8: `deathPoseIdx`→SPRITE** (likely a fold, not a new
column).

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

1. **Convert the combatant hot loop to `Query` + column-array iteration** — epic (the
   stated justification; biggest unrealized win).
   [`stories/systems-to-columns.md`](stories/systems-to-columns.md). Phase 0 (measure)
   done; Phase 1 re-scoped to behavior-neutral *column scans* (per-role timers are NOT
   uniform sweeps — centralizing them is a behavior change, not a lift). First slice
   shipped: `NavigationService.rebuildOccupancyMap` column-walks
   `BattleComponents.gridOccupants`.
2. ~~Measure it (TickProfile A/B at N=200)~~ — **DONE** ([`phase0-measurement.md`](phase0-measurement.md)).
3. **Migrate the behavior-tier `Entity` fields onto components** — epic; the active
   track above (slices 1–5 shipped, 6–8 remain).
4. **Fold convoy `Vehicle`/`MapVehicle` into the world as a ground archetype** — L.
5. ~~Decide `CommandBuffer`'s fate~~ — **DECIDED (keep):** committed engine infra;
   the systems-half epic is its consumer.
6. ~~Combatant-narrow COMBAT membership~~ — **SHIPPED (`74c565d1`):** "has COMBAT" now
   defines a combatant.
7. **Live authored-appearance** (FacingSystem / ANIMATION / FX child entities) — epic,
   lower leverage; sequence after #1.

## Recent ECS-track commits

```
0afb3c40 docs(ecs-migration): record squadId→SQUAD (slice 5, presence) shipped
32a00239 ecs-migration: move Entity.squadId onto a presence-based SQUAD component
0862ab2e docs(ecs-migration): record primaryWeapon→COMBAT (slice 4) shipped
4835bd42 ecs-migration: move Entity.primaryWeapon onto the COMBAT component
b7ed44e8 ecs-migration: visionRange/airLosRadius → VISION component + VisionService
a171f12c ecs-migration: rename fog VisionService → FogOfWarService
```

Older history is in git + the `complete/` docs. Sibling tracks (battle-render,
goap, campaign) interleave on HEAD.

## Sanity check before resuming

- `gradlew.bat compileJava` clean, full suite green.
- `git log --oneline -5` shows `0afb3c40` (slice 5 docs) / `32a00239` (slice 5) or your
  own recent work at the top.
