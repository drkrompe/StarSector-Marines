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
   (`UnitSpatialIndex`, `UnitDestinationSpatialIndex`, `VisionService`) can't
   column-walk today because they need the `Entity` object (for `visionRange`,
   `role`, faction, etc.). Move those reads onto components and the object is no
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

## Slice order (cleanest first; scan-unblockers prioritized)

Each slice follows the storage-migration playbook: add the column, seed it at
`allocate`, expose a by-id accessor on `World`, convert the readers (compiler
backstop; fan mechanical sweeps to Sonnet), delete the `Entity` field, suite green.

1. **`attackCooldown` → COMBAT** — the proving slice. Exact mirror of the existing
   COMBAT seed-stats; ~13 `setCooldownTimer(id, attackCooldown)` readers. Lowest risk,
   unambiguous home. **← start here.**
2. **`moveSpeed` → MOVEMENT** — mover-only stat, read in `advanceAlongPath`.
3. **`visionRange` + `airLosRadius` → new VISION component** (universal `{float,
   float}`). **The first scan-unblocker:** with `visionRange` a column, `VisionService`'s
   per-tick sweep can column-walk a VISION query. Convert the sweep in the same slice.
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
