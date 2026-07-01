# Battle-tier ECS migration

> **Status (2026-07-01): the storage/topology half AND the identity half are DONE; the
> systems half is CLOSED at its Phase-0 terminus; one storage gap (convoy `Vehicle`)
> remains.** The entity-field migration finished (all 8 slices) — every mutable per-unit
> field left the `Entity` heap object, so "entity = id" is now true for the systems layer
> too, not just storage. The systems-half epic (query-driven, column-walking systems)
> shipped its one genuine win (occupancy) and is otherwise
> [closed at terminus](stories/systems-to-columns.md#terminus-2026-07-01) — the remaining
> slices are lateral or Phase-0-parked (~0.02%/frame). **The active next epic is folding
> the ground `Vehicle`/`MapVehicle` POJO — the last non-ECS storage space — into the world
> as a ground archetype** ([`stories/vehicle-into-world.md`](stories/vehicle-into-world.md)),
> following the shipped air-into-world template. Read [`next-session.md`](next-session.md) §
> Status first for the live state and the leverage-ordered backlog. This file is the arc's *framing*: the *why*,
> the original **SoA-peel design rules** (historical — that peel is complete and its
> `UnitRegistry`/`local*`/`denseIdx` machinery is deleted, but the storage principles
> carry forward), and the naming north star (now *achieved* — `Unit` is `Entity`). The
> committed storage engine and its **live** rules are in
> [`archetype-storage.md`](archetype-storage.md).

Long-running refactor arc that moved the battle simulation from
god-class-with-context-interfaces toward a Services-and-Systems shape, and the
per-unit state from a fat `Unit` POJO into an archetype `EntityWorld` (one SoA
table per archetype, entity = `long` id). The first leg — the SoA *peel*, lifting
primitive columns off `Unit` onto a dense `UnitRegistry` — was the stepping stone;
it has since been **superseded by the archetype tables it was the prerequisite for**:
`UnitRegistry` is deleted, every per-unit column lives in the `EntityWorld`, optional
capabilities are archetype presence, and `Unit` was renamed `Entity`. What remains is
the **systems** half — turning the registry-shaped consumers that still iterate an
`Entity[]` and read columns by id into query-driven, column-walking systems (and
measuring the cache-locality win the storage move was justified on).

The campaign tier already runs under this shape ([`SoA primitives + behavior
in Systems`](../campaign/architecture.md)); this work brought the battle tier to the
same model on the storage side.

## Why

The legacy `BattleSimulation` was a 3000+ line god class that also
served as a `*SimContext` interface to every weapons / AI / squad
subsystem. Every subsystem reached into it for read access; every
state slice was inline. Hard to reason about who-owns-what, hard to
test in isolation, no path to systematic perf wins.

Two coupled goals:

- **Decompose into Services (state owners, constructor-injected
  dependencies) and Systems (stateless tick consumers).** Drop the
  context-interface pattern in favor of explicit per-class deps.
- **Migrate per-Unit primitives off the `Unit` POJO into SoA storage
  on `UnitRegistry`.** Establishes the seam for cache-friendly hot
  loops, dense iteration, and (eventually) component-storage refactor.

The user explicitly framed this as a "stepping stone toward ECS — not
the destination, but the prerequisite refactor so a real
component-storage move later has somewhere to land."

## Design rules (SoA-peel era — historical)

> **These governed the first leg** (peeling primitive-per-`Unit` columns onto the
> dense `UnitRegistry`). That registry and the `local*` / `denseIdx` / release-snapshot
> machinery they describe are **deleted** — columns now live in the archetype
> `EntityWorld`, and the file/symbol links below (`Unit.java`, `UnitRegistryTest`,
> `cellXArray()`/`hpArray()`) point at code that no longer exists. They are kept
> verbatim (numbering intact — [`archetype-storage.md`](archetype-storage.md) cites
> "rule 6") because the *principles* carry into the archetype world: **rule 5**
> (parallel primitive columns, not interleaved), **rule 6** (capture the raw column
> arrays once at the top of the hot loop — now per matched table), and **rule 7** (test
> parity for every column) are exactly the archetype-table rules. See
> [`archetype-storage.md`](archetype-storage.md) for their **live** form.

These were the rules every SoA promotion had to follow.

1. **Final accessors on `Unit`.** `getFoo() / setFoo()` are
   `public final` to keep HotSpot CHA monomorphic across subclasses
   (Drone, DroneHubUnit, MapTurret).
2. **`local*` transient on Unit.** Pre-allocation seed + post-release
   snapshot only. Never read directly except in registry's allocate /
   release; everything else routes through the accessor. Doc the
   xstream/Serializable caveat at the field site
   ([`Unit.java:118-126`](../../src/main/java/com/dillon/starsectormarines/battle/unit/Unit.java)).
3. **Release snapshots back.** Corpses on the legacy units list still
   need to report sane values for the post-death systems that haven't
   migrated yet (drone-crash sprite, legacy iteration paths).
4. **Tail-swap updates the moved unit's `denseIdx`.** Failing to do
   this is the load-bearing bug — caught by
   `releaseUpdatesDenseIdxOfTheSwappedTailUnit` in
   [`UnitRegistryTest`](../../src/test/java/com/dillon/starsectormarines/battle/unit/UnitRegistryTest.java).
   Every primitive promoted needs an equivalent test.
5. **Parallel arrays, not interleaved.** Default to separate arrays
   per axis (`int[] cellX, int[] cellY`). Pick interleaved only when
   the access pattern is genuinely always-paired AND you've
   surrendered single-axis kernel friendliness intentionally.
6. **Consumer migrations capture array refs once at the top of
   `tick()` or the hot method.** The registry's `denseArray() /
   cellXArray() / hpArray()` may reallocate on growth; safe to alias
   for the duration of a serial phase that doesn't allocate.
7. **Test parity.** New primitive promotion needs three tests on
   `UnitRegistryTest`: allocate-seed, release-snapshot, tail-swap.
   The hp/maxHp + cellX/cellY tests are the template.

## What's NOT in scope yet

- **Spatial index payload shape.** See
  [`spatial-index-options.md`](spatial-index-options.md).
- **Morton-sort the archetype columns periodically.** Not worth at N=200.
- **`MountedTurret` non-Unit migration.** Already done — targets route
  through the world's `COMBAT.targetId` column by entity id.

## Naming: `Unit` → `Entity` (DONE, with one caveat)

The rename followed the **rename-last, not first** discipline — the name `Entity`
should describe an *achieved* reality, not an aspiration — so it was held until the SoA
peel + the registry dissolution had hollowed the type, then shipped (`a708ce8`,
2026-06-02): `Unit.java` → `Entity.java`, 1729 usages across 185 files. Sibling types
kept their `Unit*` names (`UnitType`, `UnitRosterService`, `UnitRole`,
`UnitSpatialIndex`, `UnitBehavior`, `UnitUpdateSystem`).

**Caveat the naming north star did NOT fully reach:** `Entity` is *not yet* a bare
`long` id — it is still a ~305-line heap object held in the roster's `Entity[]`,
carrying immutable identity + `seed*` construction inputs + ~13 live behavior/capability
fields (`role`, `assignedObjective`, `equipmentDropTarget`, `primaryWeapon`, …). The
rename documents what the *storage* became (id-keyed columns in the `EntityWorld`),
while the handle itself is the subject of the still-open identity-collapse epic
([`next-session.md`](next-session.md) § Status, backlog item 3).

## What shipped, and what's next

The committed storage target — **archetype tables**
([`archetype-storage.md`](archetype-storage.md), engine core built + green: one SoA
table per archetype, entity = `long` id, structural change = row-move) — is **live and
consuming all per-unit state.** The SoA-peel, the
[facade-decoupling](complete/) (`BattleView`/`BattleControl`), the
`collapse-unit-handle` registry dissolution + rename, and the optional-capability
composition all shipped. [`component-model.md`](component-model.md) is the
composition-phase **history**; its "keep one wide table" framing is superseded by
archetype-storage.

The **identity + composition** gap is now *partially* closed — optional capabilities
ARE presence components — and *partially* open: the `Entity` handle still carries live
state, and the hot systems still read columns by id rather than walking a `Query`. The
next epics, by leverage (full backlog in [`next-session.md`](next-session.md) § Status):

- **Fold convoy ground `Vehicle` into the world** (the air analog shipped; ground didn't)
  — [`stories/vehicle-into-world.md`](stories/vehicle-into-world.md). **The active next
  step**: the last non-ECS storage space.
- ~~Migrate the behavior-tier `Entity` fields onto world components~~ — **DONE (2026-07-01)**
  (all 8 slices; finishes "entity = id" for mutable state).
- ~~[`stories/systems-to-columns.md`](stories/systems-to-columns.md) — the **systems
  half**~~ — **CLOSED at terminus (2026-07-01)**: Slice 1 (occupancy) collected the win;
  the rest is lateral or Phase-0-parked. Reopen only with the identity-collapse epic.
- Live authored-appearance (FacingSystem / `ANIMATION` component / FX child entities).

## Memory entries to read alongside

- [`battle_services_systems`](../../memory) — Service/System
  decomposition direction, dense-iter ECS seam, registry shape.
- [`feedback_skip_generation_bits`](../../memory) — why no generation
  bits.
- [`feedback_entity_for_loop_endgame`](../../memory) — default to
  ECS shape in battle-tier extractions.
