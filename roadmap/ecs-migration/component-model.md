# North star: the component model (ECS migration, next phase)

Where the migration is *heading* once the SoA-peel + facade-decoupling
work settles. Read [`overview.md`](overview.md) first for the locked-in
direction and the SoA design rules; this doc is the next-phase target, not
yet a set of in-flight slices (the slices live in [`stories/`](stories/)).

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
contract`](../../memory)) is **done**. What remains is a modeling layer.

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

**Keep the single-archetype table** — the population is homogeneous
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

## Staged plan

- **Phase A — [`collapse-unit-handle`](stories/collapse-unit-handle.md).**
  Finish hollowing `Unit` to an id + thin accessor shim; make the registry
  the sole truth; retire the `local*` duality down to the minimal lifecycle
  seed/snapshot. Unblocks the `Unit` → `Entity` rename
  (overview.md's naming north star). *Do this before the optional explosion
  ossifies the duality.*
- **Phase B — [`component-grouping`](stories/component-grouping.md).**
  Group the SoA columns into named component structs; introduce the first
  genuinely-*optional* capability (the incoming vehicle body/HP, or
  `SecondaryWeapon`) as a **sparse component with presence**, processed by a
  system that iterates only entities that have it. Hand-rolled presence per
  optional component — **no generic `Aspect`/`World` machinery yet.**
- **Phase C — deferred (gated on heterogeneity).** Generic aspect /
  presence-bitset queries and (if ever) multi-archetype storage. Only when
  branch-on-`role` + sentinel columns measurably cost more than a bitset
  skip. Until then, "fair, but not yet."

## Guardrails

- **Ship-then-optimize / skip-stopgap** ([`feedback_ship_then_optimize`](../../memory),
  [`feedback_no_stopgap_dev`](../../memory)). Don't build the generic World
  / Aspect engine speculatively. **Let the vehicle work pull the component
  model into existence** — model one optional capability as a component, get
  it working, generalize only when a second and third capability prove the
  shape.
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
