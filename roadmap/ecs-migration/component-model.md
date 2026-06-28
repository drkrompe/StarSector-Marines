# The component model (ECS migration — Phase B, IN FLIGHT)

> **Storage target updated (2026-06-03) → full archetype tables.** The
> "keep the single-archetype table" target and the "Phase C — deferred"
> multi-archetype stance in this doc are **superseded** by
> [`archetype-storage.md`](archetype-storage.md): the committed end-state is one
> SoA table per archetype, entity = bare `long` id, per-component primitive
> columns, structural change = row-move — and the engine core is built + green.
> This doc remains the **history** of the composition phase and its shipped slices;
> read `archetype-storage.md` for the live target. The `ComponentStore<T>` those
> slices shipped on is now **transitional**, to be replaced by archetype tables in
> the game retrofit.

The composition half of the ECS migration. Read [`overview.md`](overview.md)
first for the locked-in direction and the SoA design rules.

**Phase B has started — composition is being built, not deferred.** The first
component shipped 2026-06-01: `battle.component.ComponentStore<T>` (presence-
based, entity-id-keyed) + the `Crashing` component, with the drone crash
reworked into a system over that component-set (FX as a side effect of the
component). See the spine story's slice 2b. The direction has been settled
across many sessions — **do not re-litigate it as a future "pivot"; reach for
a component + a system over its set whenever new entity capability or per-tick
side-effect work lands.** ([[feedback_build_composition_now]].)

Framed in the engine-vs-game lens the project runs under
([`user_engine_game_framing`](../../memory)): **World + Systems +
Components is the engine; the specific systems — morale, GOAP, recapture —
are the game.** This doc is about closing the gap on the *engine* half.

## Where we actually are (vs an Artemis-style ECS)

We've built the **storage + transform** half of ECS and stopped at the
**identity + composition** half:

| ECS concept | What we have today | Gap |
|---|---|---|
| Entity = bare id | `long` id **+ a `Unit` object handle** in `UnitRegistry.dense[]` | Still an object per entity |
| Component (composed per entity, matched by aspect) | ~22 hand-declared SoA columns on **one implicit archetype** (`UnitRegistry`: `float[] hp`, `int[] cellX/cellY`, `long[] targetId`, …); optional capabilities are nullable fields on `Unit` | **The real miss** — no component identity, no composition |
| System (subscribes to an aspect, processes the subscription) | Stateless `*System` classes that iterate `[0, liveCount())` and **branch on `role`/`type`** (`UnitUpdateSystem.behaviorFor(u.role).update(u, sim)`) | Manual phase buckets stand in for system scheduling |
| Generic `World` + `world.process()` | `BattleSimulation` — hand-wired services + a hand-ordered tick | A bespoke World, not a declarative one |

This is a legitimate **single-archetype SoA table**: one wide table, every
entity has every column. The hard-to-retrofit part (data layout, dense
iteration, stateless systems, the [`BattleView`/`BattleControl`
contract`](../../memory)) is **done**. What remains is a modeling layer — now the
committed **archetype-tables** design ([`archetype-storage.md`](archetype-storage.md)):
the single wide table is the *starting* state we migrate off, not the target.

## The two frictions driving this now

1. **Dual representation.** `Unit` carries `local*` fields *and* the
   registry holds the canonical columns, bridged by delegating accessors
   (`getHp() → registry.getHp(denseIdx)`) with a `registry == null`
   fallback for the unregistered/corpse window. The "don't read these
   directly" hazard on every promoted field is the **tax of doing SoA
   without a language-level value type** — we maintain two stores and hope
   they stay in sync.

2. **Optional-capability explosion.** `Unit` already carries
   `primaryWeapon`, `secondaryWeapon` + `secondaryAmmo`, `mech`
   (`MechLoadoutState`), `assignedObjective`, `equipmentDropTarget` — each
   a nullable ref guarded by `if (u.secondaryWeapon != null)` checks
   scattered across systems. **The vehicle work about to land (HP + ground
   / air bodies + vehicle-mounted weapons on ground/flying entities) will
   multiply this.** Every new capability becomes another nullable column
   that every unit carries and every system null-checks. That is exactly
   the smell ECS composition exists to kill.

## The target

> **Reversed (2026-06-03) → one SoA table per archetype**, not a single wide table.
> Under the build-it-right mandate ([[feedback_storage_foundation_build_right]]) we
> commit to real archetype tables up front; the original "keep one table" argument
> is struck through below but kept for the record. See
> [`archetype-storage.md`](archetype-storage.md). The two *modeling* moves below
> still hold — they're exactly what archetype tables realize (each component
> contributes its field-columns to every table whose archetype includes it):

~~**Keep the single-archetype table**~~ — the population is homogeneous
(infantry / mech / drone / turret / vehicle all share position / hp /
target / cooldown), so one wide table beats multi-archetype migration
overhead. The change is *modeling*, not storage strategy:

1. **Group columns into named component structs.** Turn the flat field-bag
   into `Position`, `Combat`, `Render`, `AiTimers`, … — logical groupings
   over the same arrays today, **value-type component arrays** the day
   Project Valhalla lands (`value class Position { int x, y; }` → flat
   `Position[]`, struct-ergonomic access, no boxing). This is the
   "Component" concept *without* the boxing cost, and it collapses the
   dual-representation duality: the component array becomes the single
   store.

2. **Model optional capabilities as component *presence*, not
   nullability.** A unit *has a* `SecondaryWeapon` / `VehicleBody` /
   `MechLoadout` component or it doesn't. Adding a behavior becomes a
   **composition** problem (attach a component + a system that processes
   that component's set) over a **nullability** problem (`if (x != null)`
   in a mega-update). The vehicle HP/weapons work is the first real case —
   model it as a component from the start instead of more nullable fields
   on `Unit`.

### The Valhalla nuance (so we don't overclaim)

The parallel primitive arrays *already are* the packing for scalar
columns — Valhalla doesn't unlock packing we lack there. What value
classes unlock is making **the Component the unit of packing**: a named
struct array, by-value, composed without boxing. So the component-grouping
work is the right shape *today* (logical groups over existing arrays) and
becomes literally a value-type array later with no API change. We design
the component boundaries now so Valhalla is a layout swap, not a rewrite.

## Component class convention (locked 2026-06-03)

Pinned with the user before the next wave of extractions, so a component is
identifiable on sight and lives near what it models:

- **Suffix `Component`.** Every component *data* class is named
  `XxxComponent` (`CrashingComponent`, `RenderPositionComponent`,
  `DeadBodyComponent`, `MechLoadoutComponent`). The suffix makes a component
  recognisable by type name alone, anywhere it appears — even where the
  package isn't visible. The slight `components.XComponent` stutter is
  accepted in exchange for that at-a-glance signal.
- **`components` subpackage, per-domain.** Component data classes live in a
  `components` subpackage of their *related domain* package
  (`battle.air.components`, `battle.unit.components`, `battle.mech.components`),
  not one central bag. Place each by the **capability it models**, not the
  system that happens to process it today — e.g. `CrashingComponent` is
  air-generic (composes `AirBody`, drone-agnostic by design) so it lives in
  `battle.air.components`, even though the only processor today is
  `DroneCrashSystem` in `battle.drone`.
- **Infra and processors stay put.** `ComponentStore<T>` is the generic store,
  not a data component — it remains in `battle.component`. The systems/services
  that read or mutate a component (`DroneCrashSystem`, `RenderPositionService`,
  `DeadBodySystem`, `MechWreckSystem`, …) stay in their domain package; only
  the data class moves into `components`.

Existing components were retrofitted to this convention (2026-06-03):
`Crashing → battle.air.components.CrashingComponent`,
`RenderPosition → battle.unit.components.RenderPositionComponent`,
`DeadBody → battle.unit.components.DeadBodyComponent`,
`MechLoadoutState → battle.mech.components.MechLoadoutComponent`.

> Archetype-era note: in [`archetype-storage.md`](archetype-storage.md) a component
> is a registered `ComponentType` (id + field-kind schema) + field-index constants,
> **not** an `XxxComponent` POJO. These `XxxComponent` data classes are the
> **transitional** `ComponentStore<T>` form; the retrofit folds them into archetype
> columns. The "name it for the capability, place it by domain" intent carries over.

## Staged plan

- **Phase A — [`collapse-unit-handle`](complete/collapse-unit-handle.md).**
  Finish hollowing `Unit` to an id + thin accessor shim; make the registry
  the sole truth; retire the `local*` duality down to the minimal lifecycle
  seed/snapshot. Unblocks the `Unit` → `Entity` rename
  (overview.md's naming north star). *Do this before the optional explosion
  ossifies the duality.*
- **Phase B — [`component-grouping`](stories/component-grouping.md). IN FLIGHT.**
  Group the SoA columns into named component structs; model optional
  capabilities as **sparse components with presence**, processed by systems that
  iterate only entities that have them. Hand-rolled presence per optional
  component — **no generic `Aspect`/`World` machinery yet.**
  - **B2 first component SHIPPED:** `ComponentStore<T>` + `Crashing` (the drone
    crash) — `40fa668`. Validated capability-as-presence on real, motivated work
    (the crash) exactly as the guardrails prescribe.
  - **B2.5 first column decomposed out of the kitchen-sink SHIPPED:** render
    position (`renderX/renderY`) lifted from the dense `UnitRegistry` into a
    standalone `RenderPositionService` (in `battle.unit`), a thin float API over
    a `ComponentStore<RenderPosition>` (new `RenderPosition` component). Keyed by
    `entityId`, **survives registry release** — so a corpse keeps its death-pose
    location for free, and "where do I draw" is one shared component instead of a
    column redefined per entity. Render was the natural first cut: an audit found
    **zero** dense-array readers of `renderXArray()/renderYArray()` (every reader
    goes through the `getRenderX()` accessor), so the move is zero-perf-cost and
    zero-behavior-change for the living, and it collapses the render half of the
    `local*`/release-snapshot duality. Hot-dense columns (`cellX/cellY`, hp,
    combat/AI timers) stay in the dense table — pulled only as a consumer demands.
  - **B2.6 dead-sprite render decomposed SHIPPED — corpse home complete.**
    `UnitRenderService.sweepDeadSprites` no longer scans `getUnits()`: a `DeadBody`
    component (render identity: `type` + `deathPoseIdx`) is recorded on every
    `DeathEvent` by `DeadBodySystem`, and the sweep iterates that store paired
    with the surviving `RenderPositionService` entry. A corpse is now literally an
    entity present in the dead-body + render-position stores and absent from the
    live registry's health/AI — the composition target, realized. All four
    Bucket-B corpse-readers are off the legacy list.
  - **B2.7 first BEHAVIORAL optional capability SHIPPED — `mech` (2026-06-02).**
    `Entity.mech` (nullable `MechLoadoutState` field) → a presence component in
    `ComponentStore<MechLoadoutState>`. Where the corpse/render components were
    death/draw state, this is the first *live-behavior* capability done as
    composition: the mech-fire pass iterates the component-set (`HeavyWeapons`
    over the store's `entries()`, not a registry scan for `u.mech != null`), and
    every nullable-field null-check (`DamageResolver`, `HitResponseService`,
    `SquadMoraleSystem`, the mech GOAP behaviors, `CombatantBehavior` dispatch) is
    now a store presence lookup. Meets the `component-grouping` acceptance: **zero
    nullable-field if/else for the mech capability.** Validated the cold-face
    guardrail in practice — added zero-alloc `World.component(id, type)` /
    `hasComponent(id, type)` for the per-tick decide paths, reserving the
    allocating `world.id(id).getOrNull` sugar for incidental reads. Field deleted;
    spawn attaches the component post-`addUnit` (keyed by entity id),
    `UnitType.isMech()` carries mech-ness where the component isn't attached yet
    (squad-type at mint), `MechWreckSystem` detaches it on wreck spawn.
  - **Next:** Bucket-A sweep — the ~20 live-iterators move `getUnits()` → dense
    registry; then Bucket-C, delete the list, revert Group-N accessors to
    fail-loud. Plus B1 grouping for the dense columns (Position/Combat/… structs)
    when a consumer pulls it. Remaining optional fields (secondaryWeapon/ammo,
    assignedObjective, equipmentDropTarget) get the `mech` treatment when motivated.
- **Phase C — archetype tables (COMMITTED 2026-06-03, no longer deferred).** The
  earlier "defer multi-archetype until heterogeneity proves it" stance was
  reversed: the committed end-state is **one SoA table per archetype** + a
  `long`-mask `Query` (cached matched-table list) — see
  [`archetype-storage.md`](archetype-storage.md); the engine core is built + green.
  The retrofit moves the game off the transitional `ComponentStore<T>` and the
  dense `UnitRegistry` onto the archetype world.

## Guardrails

- **Build the storage foundation right, up front** ([[feedback_storage_foundation_build_right]])
  — a scoped carve-out from ship-then-optimize (2026-06-03). The archetype storage
  engine is built deliberately as the committed end-state
  ([`archetype-storage.md`](archetype-storage.md)), not pulled into existence
  piecemeal. Ship-then-optimize ([`feedback_ship_then_optimize`](../../memory),
  [`feedback_no_stopgap_dev`](../../memory)) still governs gameplay/feature work;
  it no longer governs the storage core.
- **Default to the ECS shape** in new battle-tier work
  ([`feedback_entity_for_loop_endgame`](../../memory)): when the vehicle
  HP/weapons land, reach for "component + system over its set," not "another
  nullable field + if/else."
- Every storage change still obeys the SoA design rules in
  [`overview.md`](overview.md) (final accessors, tail-swap denseIdx test,
  release snapshots, parallel arrays).

## Related memory

- [`battle_view_control_contract`](../../memory) — the read/mutate sim
  access contract (the facade phase, now shipped).
- [`battle_services_systems`](../../memory) — Service/System decomposition;
  the dense-iter ECS seam.
- [`feedback_skip_generation_bits`](../../memory) — ids are monotonic;
  no generation bits.
