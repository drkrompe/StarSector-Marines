# Health onto the EntityWorld — death is a row-move (retrofit step 3a)

**Shipped 2026-06-03** — `adb4bc9` (game) + `e720e98` (engine seams). Full
suite green at 760.

First live-side capability on the archetype engine
([`archetype-storage.md`](../archetype-storage.md) migration step 3, opener).
hp/maxHp left the `UnitRegistry` dense columns for the world's `HEALTH`
component, and death became the committed model end-to-end: **one row-move
from the live archetype to the corpse archetype, same entity id.**

## Engine seams (`e720e98`)

- `createEntity(long id, ComponentType...)` — **id adoption**: create under an
  externally-minted id (the registry still mints); bumps the internal mint past
  it so future world-minted ids (FX children) can't collide.
- `transmute(long, add[], remove[])` — multi-component archetype change as
  **one** row-move to the combined mask (Artemis `EntityTransmuter` analogue);
  chained add/remove would pay a move each and register every intermediate
  archetype as a permanent table. Mask no-op → idempotent on double death.
- `getFloat(e, ct, f, orElse)` — single-probe tolerant read for liveness-style
  checks over maybe-dead ids.

## Game (`adb4bc9`)

- **Spawn**: `UnitRegistry.allocate` adopts the minted id into the world as
  `{IDENTITY, HEALTH}` — identity written once (persists alive→dead), hp/maxHp
  seeded from the `seed*` fields. The registry **owns** the `EntityWorld` +
  `BattleComponents` for the transition (the `RenderPositionService`
  owned-sub-store precedent); `BattleSimulation` aliases them. Ownership hops
  to the sim when step 4 dissolves the registry.
- **Death**: `DeadBodySystem.onDeath` `transmute`s to
  `{IDENTITY, POSITION, RENDER_POSITION, SPRITE, CORPSE}` — IDENTITY rides the
  shared-column copy (proven by the test: it's only ever written at spawn),
  HEALTH removed, cell/draw-position/pose authored as before. The corpse **is**
  the unit's entity id — the medic/revive seam needs no correlation table.
- **Liveness redefined world-side**: `isAliveById` = has `HEALTH` && hp > 0.
  A corpse fails on the missing component, a just-killed-not-yet-transmuted id
  fails on hp (every release path zeroes hp first — resolve, hub cascade,
  `TestUnits.kill`), never-allocated/0L misses the world. Registry presence
  dropped out of the definition; the 14 liveness call sites unchanged.
- **Registry hp columns deleted**; `hpById`/`setHpById`/`maxHpById` remain as
  transitional world-backed adapters so call sites keep their receivers
  (`DamageResolver`, hub cascade, rocket-commit gate, mech morale pass, both
  hp-bar renders). `World` facade hp surface unchanged for its callers.
  `DamageResolver` now derives `died` from the locally computed `newHp`.

## Semantic shift (deliberate)

Registry release alone no longer makes hp unreadable — `HEALTH` stays on the
world entity until the death drain's corpse transmute removes it (then hp is
fail-loud again). The old release-instant fail-loud window narrowed to
post-drain; in the release→drain gap a stale reader sees hp ≤ 0, which is the
honest answer. `WorldTest.hotFaceIsFailLoudOnceHealthIsGoneOrTheIdIsUnknown`
documents the new boundary.

## Follow-ups

- Single id authority: registry still mints; air entities (`Shuttle.entityId`)
  mint separately — converge on the world's mint before air entities join it.
- Next capabilities: Position (largest consumer surface), Combat, Movement,
  AiState; fold `Crashing`/`MechLoadout` stores into archetype membership.
