# Entity-field migration — move the behavior-tier fields onto world components

> **Status: design + in flight (seeded 2026-06-29).** The sibling epic the
> [`systems-to-columns`](systems-to-columns.md) audit kept pointing at: the storage
> half made *columns* the home for per-unit state, but ~12 live mutable fields still
> ride the `Entity` heap object the roster holds. This epic moves them onto the
> `EntityWorld` so `Entity` collapses toward "just an id + immutable identity," and
> the per-tick scans that currently need the `Entity` object (spatial index, vision)
> become column-walkable. Read [`overview.md`](../overview.md) for the SoA rules and
> [`archetype-storage.md`](../archetype-storage.md) for the engine.

## Why (the two payoffs)

1. **Finish "entity = id."** The rename shipped (`Unit`→`Entity`) but the handle is
   still a ~305-line heap object carrying live behavior/capability state the world
   doesn't own. Until those move, the storage migration's "the entity is its id" is
   true for storage and false for the systems layer.
2. **Unblock the systems-half scan conversions.** Most per-tick all-unit scans
   (`UnitSpatialIndex`, `UnitDestinationSpatialIndex`, `FogOfWarService`) can't
   column-walk today because they need the `Entity` object (for `role`, faction,
   etc.; `visionRange`/`airLosRadius` are now off it — slice 3). Move those reads
   onto components and the object is no
   longer needed mid-scan — the column-walk falls out (with a follow-on payload
   change for the indices; directly for vision).

## The live fields (ground truth from `Entity.java`, 2026-06-29)

Immutable identity stays on the handle (`entityId`, `id`, `faction`, `type`, `rng`);
the `seed*` / `local*` construction inputs stay (write-only, consumed by
`UnitRosterService.allocate`). The **migration targets** are the live mutable fields:

| field | type | population | proposed home |
|---|---|---|---|
| `attackCooldown` | float | combatant | **COMBAT** (new stat field, beside attackDamage/Range/Accuracy) |
| `primaryWeapon` | MarineWeapon | combatant | **COMBAT** (new OBJECT field) |
| `moveSpeed` | float | mover | **MOVEMENT** (new stat field) |
| `visionRange` | float | universal | **VISION** (new component) |
| `airLosRadius` | float | universal (0 default) | **VISION** (new component) |
| `squadId` | int | universal (NO_SQUAD sentinel) | **SQUAD** (new component) or membership |
| `role` | UnitRole | universal | **dispatch component** (universal; not AI_STATE — turrets have a role but no AI_STATE) |
| `lastReprioTickIndex` | volatile int | mech/turret | decision cluster (NOT AI_STATE — turrets lack it) |
| `assignedObjective` | Objective | role-specific | decision cluster (optional/presence) |
| `equipmentDropTarget` | EquipmentDrop | KIT_RETRIEVER | decision cluster (optional/presence) |
| `homeCellX/Y` | int,int | GARRISON | decision cluster (optional/presence) |
| `deathPoseIdx` | int | rolled at death | fold into **SPRITE** / death authoring |

**Design rule** ([[feedback_components_by_capability_not_store]]): group by
lifecycle-stable capability, not by current storage. A field's home is "what
capability is it part of," and presence = membership for genuinely-optional ones
(the `SECONDARY_WEAPON` precedent) rather than a nullable field on a shared bag.

## Access model — per-component Services, not the `World` god-facade (decided 2026-06-29)

The user drew the distinction that governs HOW migrated fields are reached:

- **System** = a per-tick processor over *all entities matching an aspect* (the
  column-walk; stateless). The Artemis `IteratingSystem`.
- **Service** = a *data owner* — it holds a component's state and exposes the
  read/mutate methods for it. The Artemis `ComponentMapper`, with mutators.

The flat `battle.sim.World` is **neither** — it's a ~60-method passthrough that owns
nothing and spans every component (`hp`, `cellX`, `attackCooldown`, `moveSpeed`, …).
Piling the behavior-tier fields onto it makes the god-facade worse. **So each
migrated field's by-id access lands on a per-component Service** (`CombatService`
owns COMBAT, `MovementService` owns MOVEMENT, …; one per component). Consumers are
**constructor-injected** with the Service they need (or reach `sim.combat()` /
`roster.movement()` where they only hold the sim/roster handle) and call
`combat.attackCooldown(id)` — **no `World` hop, no stutter**. This is the existing
constructor-injected Services pattern ([[battle_services_systems]]); `World` was a
storage-migration expedient and is being **retired incrementally**: it now
*delegates* its COMBAT/MOVEMENT accessors to the Services, so the ~hundreds of
existing `world.<x>(id)` callers stay green while consumers move off it per-slice.

Per-tick bulk consumers eventually become real **Systems** that column-walk the
component table (the [`systems-to-columns`](systems-to-columns.md) epic); the
Service by-id methods are for the random-access / held-ref / cold paths.

**Shipped (`b77e45a8`):** `CombatService` + `MovementService` (own all COMBAT /
MOVEMENT access), `World` delegates, wired through `UnitRosterService.combat()/
movement()` + `BattleView`/`BattleSimulation`. Slices 1–2's consumers reroute onto
them next; slice 3 (VISION) **shipped** as `VisionService` from the start
(`b7ed44e8`) — never touching `World` — after the pre-existing fog-of-war
`VisionService` was renamed `FogOfWarService` (`a171f12c`) to free the name.

## Slice order (cleanest first; scan-unblockers prioritized)

Each slice follows the storage-migration playbook: add the column, seed it at
`allocate`, expose a by-id accessor on `World`, convert the readers (compiler
backstop; fan mechanical sweeps to Sonnet), delete the `Entity` field, suite green.

1. ~~**`attackCooldown` → COMBAT**~~ **— SHIPPED (`93602bbf`, 2026-06-29).** The
   proving slice; exact mirror of the COMBAT seed-stats. Field 8
   `COMBAT_ATTACK_COOLDOWN`; `Entity.attackCooldown`→`seedAttackCooldown`; ~15 readers
   (the `setCooldownTimer` resets + render gate + turret/drone aim-state seed) → `world.attackCooldown(id)`.
2. ~~**`moveSpeed` → MOVEMENT**~~ **— SHIPPED (2026-06-29).** Mover-only stat (field 3
   `MOVEMENT_MOVE_SPEED`); `Entity.moveSpeed`→`seedMoveSpeed`; readers `advanceAlongPath`
   + `TacticalScoring.findFallbackPositionImpl` (audited: all `findFallbackPosition`
   callers are movers — the `HitResponseSystem` one is AI_STATE-gated, and AI_STATE
   ⟺ MOVEMENT) → `world.moveSpeed(id)`. Valid mover-narrowing.
3. ~~**`visionRange` + `airLosRadius` → new VISION component**~~ **— SHIPPED
   (`b7ed44e8`, 2026-06-30).** Universal `{float, float}` component (id 18), seeded
   at `allocate`, removed on the corpse transmute (live-only — a corpse does not
   see). Data owner is a new **`battle.sim.VisionService`** mirroring
   `CombatService`/`MovementService` (roster-owned; `roster.vision()` / `sim.vision()`
   / `BattleView.vision()`). `Entity.visionRange`/`airLosRadius` →
   `seedVisionRange`/`seedAirLosRadius`. ~20 reader sites across 7 files went by-id:
   `TacticalScoring` (14 sites — the loop-invariant `self`/`target` air radius hoisted
   out of the firing-position + vantage scans, so the hot loops do **zero**
   per-candidate probes), `HitResponseSystem`, `SquadAlertSystem`, `EngagePosture`,
   `DroneSwarmAction`, and `TurretAim` (gains a `VisionService` param; its 4 callers
   thread it). **The name collision** with the pre-existing fog-of-war `VisionService`
   was resolved by renaming that class **`FogOfWarService`** (`a171f12c`) — it owns the
   reveal bitmap + visibility sweep, which that name describes; `VisionService` is now
   free for the per-component data owner.
   - **Finding — the "scan-unblocker" was narrower than billed.** The `visionRange`/
     `airLosRadius` reads are NOT an all-units sweep: they live in the *curated
     contributor cohort* path (keyed by held unit id) + held-ref decision/combat LoS
     code. So there is no "all units with VISION" `Query` to column-walk (the big
     `FogOfWarService.sweepUnitVisibility` loop reads POSITION, not VISION), and **no
     VISION `Query` was added** (no consumer). The realized payoff: every reader is by-id
     off the Service, and `FogOfWarService.tickFogCohort` drops its per-contributor
     `Entity` materialization (`isAliveById` gates; sight stats by id) — the genuine
     "object no longer needed mid-scan" win, just on the cohort path rather than a
     headline column-walk.
4. **`primaryWeapon` → COMBAT** (OBJECT field) — the combatant loadout; read by
   `beginBurst` + `InfantryWeapons.fireShot`.
5. **`squadId` → SQUAD component** — universal; large reader set (dispatch, squad
   systems). Big mechanical sweep.
6. **`role` → dispatch component** — universal. Unblocks `systems-to-columns` Phase 2
   (dispatch by presence, not enum) and is the prerequisite to keying the spatial
   indices by id (the index column-walk follow-on).
7. **Decision cluster** (`assignedObjective`, `equipmentDropTarget`, `homeCellX/Y`,
   `lastReprioTickIndex`) — the thorny tail; each is optional with a distinct small
   population, so each is a presence component or a field on a narrow decision
   component. Do last, refine the grouping when reached.
8. **`deathPoseIdx` → SPRITE / death authoring** — cleanup; the corpse pose already
   lands in `SPRITE.index`, so this may be a fold rather than a new column.

## Acceptance criteria

- Each migrated field: column seeded at `allocate`, by-id `World` accessor, all
  readers converted, the `Entity` field deleted, suite green. No nullable-capability
  field left behind (optional ones become presence components).
- At least one scan (VISION sweep, slice 3) actually column-walks after its field
  moves — proving the second payoff, not just asserting it.
- `Entity` shrinks measurably toward "id + immutable identity + seed inputs."

## Risks / notes

- **Behavior-preserving, always.** These are live reads in the hot loop; every slice
  is a like-for-like relocation, suite-green-gated. No behavior change rides along.
- **Mutability.** Some "stats" are mutable post-spawn (captain traits / mission
  mods adjust `moveSpeed`, `attackCooldown`). The COMBAT stats already are mutable
  columns — same shape; the seed is just the initial value.
- **`role` is universal, not thinker-only.** Don't fold it into AI_STATE; a turret
  has a role (`TURRET`) but no AI_STATE. Same trap for `lastReprioTickIndex`.
- **Spatial-index column-walk is a follow-on, not free.** The field migration removes
  the *reason* the index holds `Entity` refs, but flipping the bucket payload
  `Entity`→id (and the `gather` callers) is its own slice in `systems-to-columns`.

## Cross-refs

- [`systems-to-columns.md`](systems-to-columns.md) — the consumer epic; its Phase 1
  "second finding" is what gates on this.
- [`next-session.md`](../next-session.md) § Status backlog item #3.
- Memory: [[feedback_components_by_capability_not_store]],
  [[feedback_appearance_authored_component]] (SPRITE for deathPoseIdx),
  [[battle_component_naming_convention]] (XxxComponent in a per-domain `components/`).
