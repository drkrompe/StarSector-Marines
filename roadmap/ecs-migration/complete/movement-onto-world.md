# Movement onto the EntityWorld — moveProgress as a MOVEMENT component (step 3e)

**Shipped 2026-06-25** — `42cc723`. Full suite green at 757.

Sixth live capability on the archetype engine
([`archetype-storage.md`](../archetype-storage.md) migration step 3, after
Health 3a / Position 3b / Combat 3c / SecondaryWeapon 3d). The per-unit
movement lerp factor `moveProgress` left `UnitRegistry`'s dense float column
for the world's new `MOVEMENT` component (single `FLOAT`). The live archetype
is now `{IDENTITY, POSITION, HEALTH, COMBAT, MOVEMENT}` (+ optional
`SECONDARY_WEAPON`).

## Universal-first (behavior-preserving), like Combat 3c

The design ([`archetype-storage.md`](../archetype-storage.md), the decomposition
table) has `Movement` as an **optional** capability — turrets are `{…,Combat}`
with no Movement. This slice ships it **universal** instead: every live unit
spawns with `MOVEMENT`, `moveProgress` starting at zero by the world row's
append. That is exactly the precedent Combat (3c) set — ship the
designed-optional capability universal-first so the slice is
behavior-preserving (moveProgress was already a universal registry column; even
a non-moving turret carried a 0 it never advanced), and defer
membership-narrowing. Narrowing to path-executing (kinematic) entities only is
genuinely coupled to the **path ref** fold-in (a turret has no path; a mover
does), so both are deferred to that slice, where "has a path capability" is what
truly defines a mover. A universal MOVEMENT also keeps the ~40 reader sites
untouched (they'd otherwise each need a `hasMovement` presence gate, and the
all-units debug dump in `SquadStateDumper` reads it unconditionally).

## What landed

- **Component**: `BattleComponents.MOVEMENT = register(8, "Movement", FLOAT)`,
  field index `MOVEMENT_MOVE_PROGRESS = 0`.
- **Spawn**: `allocate` adds `MOVEMENT` to the `createEntity` archetype (both
  the secondary and no-secondary branches); no explicit seed — the appended row
  is zero-init. The registry's dense `moveProgress` field + its grow-copy /
  allocate-reset / release-tail-swap and the by-index
  `getMoveProgress`/`setMoveProgress`/`moveProgressArray` accessors are
  **deleted** (`moveProgressArray` had zero callers). Transitional by-id
  adapters `moveProgressById` / `setMoveProgressById` over the world `MOVEMENT`
  column (same shape and fate as the hp/cell/combat adapters — strict/fail-loud
  on a gone-or-transmuted entity).
- **Death**: the corpse transmute removes `MOVEMENT` (`DeadBodySystem.corpseRemove`
  gains it) — a corpse does not move. Corpse archetype unchanged.
- **Facade**: `World.moveProgress(id)` / `setMoveProgress(id, v)` reroute to the
  by-id adapter; signatures unchanged, so every `sim.world().moveProgress`
  consumer (~40 sites across infantry postures, GOAP actions, mech behaviors,
  flee/fallback, debug) is untouched.
- **Non-facade readers** (the only by-index sites): `Entity.advanceAlongPath`
  goes by-id and **drops its now-unused `requireLiveIndex(entityId)`** (the
  resolved index was used for moveProgress alone — cell already by-id);
  `InfantryWeapons` burst continuation reads `moveProgressById(id)` for its
  `FireStance.stanceFor`.
- **Doc re-anchor**: the "mid-combat-only lifecycle (the anchor the other such
  columns point to)" doc moved off the deleted `moveProgress` onto
  `repositionCooldown`; the remaining four AiState columns repoint their
  "Same lifecycle as …" links to it.
- **Tests**: the two `UnitRegistryTest` moveProgress tests rewritten to the
  by-id world-component contract (mirror the `cooldownTimer` tests):
  allocate-defaults-and-accessors + undisturbed-by-dense-tail-swap. Net-zero on
  test count.

## Perf note

`moveProgress` reads are now one world location-probe per access instead of a
raw array read — the accepted step-back
([[feedback_storage_foundation_build_right]]), bounded at N≈200 and recovered
later when bulk consumers walk world tables directly. No moveProgress consumer
iterated `moveProgressArray()` densely (it had zero callers), so nothing hot
regressed beyond the per-access probe.

## Follow-ups

- **Movement membership-narrowing + path ref** — fold `int[] path` + `int
  pathIdx` (still `Entity` fields, ~66 sites / 28 files incl. hot nav loops and
  the static `NavigationService.pathDestX/Y`) into `MOVEMENT` as an OBJECT +
  INT column, and narrow membership to actual movers (audit the ~40 readers +
  the `SquadStateDumper` dump for non-mover gating). This is the slice that
  makes Movement the optional kinematic capability the design intends.
- **Next capability: AiState** — `repositionCooldown` + the fallback group
  (`fallbackTimer`/`fallbackCellX`/`fallbackCellY`) + `wanderDwellTimer`, the
  last `UnitRegistry` dense columns. Then fold `Crashing`/`MechLoadout`
  ComponentStores into archetype membership; then step 4 dissolves
  `UnitRegistry`.
