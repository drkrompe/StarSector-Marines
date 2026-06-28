# Story: component grouping + optional capabilities as presence

> **PARTIALLY SUPERSEDED — the "Context" below is stale.** When this was written the
> world was "~22 SoA columns on `UnitRegistry` + nullable capability refs on `Unit`";
> both are **gone** (`UnitRegistry` deleted, columns live in the archetype `EntityWorld`,
> the nullable refs are now presence components). Its **B1** (group columns into named
> components like `Position`/`Combat`) is **realized** by the archetype migration; the
> capability axis it follows is recorded in
> [`components-by-capability.md`](../complete/components-by-capability.md). What's still
> **open** is its **B2 payoff** — making hot loops *touch only the columns they need*
> (cold-column avoidance / value-type packing). That is unrealized because nothing
> column-walks the hot path yet; it is now owned by
> [`systems-to-columns.md`](systems-to-columns.md). Kept here for the Valhalla framing;
> do not trust the storage description below as current.

Phase B of the [component model](../component-model.md) — the conceptual
centerpiece. Read that doc for the engine-vs-game framing and the Valhalla
nuance.

## Context

Two shapes coexist on `Unit` today:

- **~22 SoA columns** on `UnitRegistry` — a flat field-bag on one implicit
  archetype. Every unit has every column.
- **Nullable capability refs** on the `Unit` object — `primaryWeapon`,
  `secondaryWeapon` + `secondaryAmmo`, `mech` (`MechLoadoutState`),
  `assignedObjective`, `equipmentDropTarget` — each guarded by scattered
  `if (u.x != null)` checks across systems.

The incoming vehicle work (HP + ground/air bodies + vehicle-mounted weapons
on ground/flying entities) is about to add a wave of new optional state. On
the current model each addition is *another nullable field every unit
carries and every system null-checks*. That's the smell composition exists
to remove.

## Goal

Two complementary moves:

### B1 — Group columns into named component structs

Turn the flat field-bag into logical components — `Position` (cellX/cellY,
renderX/renderY, moveProgress), `Combat` (attackDamage/range/accuracy,
cooldownTimer), `AiTimers` (reposition/fallback/wander), `Targeting`
(targetId + secondary/burst target ids), etc. **Same arrays, named
grouping** today; **value-type component arrays** (`value class`) the day
Valhalla lands, with no API change. Design the component boundaries now so
that future swap is layout-only.

This is mostly an accessor/organization refactor over existing SoA — low
behavioral risk, sets up the vocabulary.

### B2 — Model optional capabilities as component *presence*

A unit *has a* `SecondaryWeapon` / `VehicleBody` / `MechLoadout` component,
or it doesn't. Replace nullable-field + if/else with:
- a **sparse component store** (only entities that have the component
  occupy it) keyed by entity id / denseIdx, and
- a **system that iterates only the entities that have it** — no
  null-check, no branch-on-role.

**The vehicle HP/weapons work is the first real case — model it as a
component from day one** instead of more nullable fields. That validates
the pattern on live, motivated work rather than a speculative refactor.

## Decisions to pin (before code)

- **Presence representation:** sparse set (map/array of present entities)
  vs a presence bit on a dense column vs a packed secondary table. Start
  with whatever is simplest for the *first* optional component (likely a
  sparse list + denseIdx lookup); don't generalize yet.
- **Where component stores live:** more columns on `UnitRegistry`, or a
  per-component store owned by the relevant Service? (Lean: hot/universal
  components on the registry; optional/sparse components in their owning
  service, e.g. a `VehicleBodyStore`.)
- **How a system queries "entities with component X":** explicit per-store
  iteration for now. A generic `Aspect` query is **Phase C**, gated on
  having enough optional components to justify the machinery.
- **`MechLoadoutState`** is the existing precedent for an optional,
  stateful capability — decide whether it migrates to the new pattern in
  this story or rides along later.

## Naming & placement convention (locked 2026-06-03)

Every component data class is named `XxxComponent` and lives in a per-domain
`components` subpackage (`battle.<domain>.components`); `ComponentStore<T>`
infra and the processing systems stay in their existing packages. Full rule +
the retrofit of the four existing components is in
[`component-model.md`](../component-model.md#component-class-convention-locked-2026-06-03).
Apply it to every component extracted from here on.

## Approach

1. B1 grouping first (vocabulary, no behavior change) — or interleave with
   the first B2 component if grouping the relevant columns helps.
2. Pick **one** optional capability — the vehicle body/HP if that work is
   active, else `SecondaryWeapon` as the cleanest existing nullable — and
   stand it up as a presence-based component + its system. Delete the
   nullable field + its if/else guards.
3. Stop and assess: did composition beat nullability for that case? Only
   then generalize to the next capability. **No generic World/Aspect engine
   until ≥3 components prove the shape** (skip-stopgap / ship-then-optimize).

## Acceptance

- At least one optional capability is modeled as component presence with a
  system over its set — **zero nullable-field if/else for that capability**.
- The SoA columns carry named component-group identity (at minimum in the
  accessor/organization layer), designed as Valhalla value-class boundaries.
- The vehicle HP/weapons work (if landed) uses the component pattern, not
  new nullable `Unit` fields.
- Full suite green; SoA design rules (overview.md) intact.

## Priority

The centerpiece, but **gated** — pairs with / follows
[`collapse-unit-handle`](../complete/collapse-unit-handle.md) (don't add component
stores on top of the local* duality) and is **pulled by the vehicle work**.
If vehicles land first, do the minimal B2 for the vehicle body as the
forcing case and backfill B1/the rest after.
