# AiState onto the EntityWorld — empties the registry's last dense columns (step 3f)

**Shipped 2026-06-25** — `8001f78`. Full suite green at 761 (757 mine + 4 from
a sibling's `TileRegistryParityTest`).

Seventh live capability on the archetype engine
([`archetype-storage.md`](../archetype-storage.md) migration step 3, after
Health 3a / Position 3b / Combat 3c / SecondaryWeapon 3d / Movement 3e). The AI
decision-cadence cluster left `UnitRegistry`'s dense SoA arrays for the world's
new `AI_STATE` component. The live archetype is now `{IDENTITY, POSITION,
HEALTH, COMBAT, MOVEMENT, AI_STATE}` (+ optional `SECONDARY_WEAPON`).

**This is the last per-unit dense column.** `UnitRegistry` now holds only the
dense `Entity[]` + its id↔slot map (`indexById`) + the owned-for-transition
`EntityWorld`/`BattleComponents`/`RenderPositionService` — exactly the shape
step 4 dissolves.

## Component

`AI_STATE = register(9, "AiState", FLOAT, FLOAT, INT, INT, FLOAT)`, fields:

| idx | constant | column | default |
|---|---|---|---|
| 0 | `AI_STATE_REPOSITION_COOLDOWN` | `float repositionCooldown` | 0 |
| 1 | `AI_STATE_FALLBACK_TIMER` | `float fallbackTimer` | 0 |
| 2 | `AI_STATE_FALLBACK_CELL_X` | `int fallbackCellX` | **−1** |
| 3 | `AI_STATE_FALLBACK_CELL_Y` | `int fallbackCellY` | **−1** |
| 4 | `AI_STATE_WANDER_DWELL_TIMER` | `float wanderDwellTimer` | 0 |

## The non-zero default (the one subtlety)

The fall-back cell pair defaults to **−1/−1** ("no cached cell" — readers treat
any non-negative value as a live cached destination, e.g.
`TacticalScoring.fallbackDestinationNeedsRefresh` returns true on `fx < 0`). A
fresh world row appends **zero-init**, so `allocate` explicitly seeds −1/−1
into the AI_STATE row right after `createEntity`. The other four scalars default
to zero = the row append, so — like the MOVEMENT/COMBAT mid-combat scalars —
they need no seed, and the old per-slot-reuse reset block is **gone** (a fresh
per-id world row replaces dense-slot reuse).

## Universal-first, like Combat/Movement

The design has `AiState` as optional ("thinking entities" — a turret has no
decision cadence). Shipped **universal** (every live unit) for
behavior-preservation, the established precedent; membership-narrowing deferred.
Universal also keeps the ~40 facade readers + the
`FleeBehavior`/`FallbackBehavior`/`BreakLOS`/`MechBreakContact`/etc. writers
untouched (they'd otherwise each need a `hasAiState` presence gate, and
`SquadAlertSystem` reads `fallbackTimer` for every squad member).

## What landed

- **Registry**: the 5 dense fields + their grow-copy / allocate-reset /
  release-tail-swap + the by-index `get`/`set`/`*Array` accessors **deleted**
  (every `*Array()` view had zero callers). By-id adapters
  `repositionCooldownById` / `fallbackTimerById` / `fallbackCellX`+`YById` /
  `setFallbackCellById` / `wanderDwellTimerById` over the world AI_STATE columns
  (same transitional shape/fate as hp/cell/combat/movement). With no dense
  column left, `release`'s swap-and-pop now moves **only the `Entity[]` slot +
  fixes the tail's id↔slot mapping**.
- **Death**: corpse transmute removes `AI_STATE` (`DeadBodySystem.corpseRemove`
  gains it) — keeps the corpse in the canonical `{IDENTITY, POSITION,
  RENDER_POSITION, SPRITE, CORPSE}` archetype the `corpses` query matches.
- **Facade**: `World`'s 9 AI-state methods reroute to the by-id adapters;
  signatures unchanged, so every `sim.world().X` consumer is untouched.
- **Four by-index production sites → by-id**: `TacticalScoring
  .fallbackDestinationNeedsRefresh` (drops its `requireLiveIndex`),
  `HitResponseService.rollFallbackOnHit` (drops `tIdx`), `SquadAlertSystem`
  (dense loop → `registry.fallbackTimerById(u.entityId)`, one world probe per
  member — the accepted bounded-N step-back), `BattleSimulation
  .writeFallbackInline` (drops `idx`, and swaps the inline
  `…combat.HitResponseService.FALLBACK_DURATION` FQN for an import + simple
  name).
- **Tests**: the 8 AiState column tests rewritten to the by-id world-component
  contract (mirror the `cooldownTimer` pair: defaults + undisturbed-by-dense-
  tail-swap). The load-bearing `releaseUpdatesDenseIdxOfTheSwappedTailUnit`
  (design rule #4) **re-proves the swap via the dense `Entity[]` slot**
  (`assertSame(c, r.get(0))`) — there's no dense column left to move, so the
  `Entity[]` slot + index mapping *is* the whole swap contract now.
  `allocateResetsMidCombatColumnsWhenReusingAFreedSlot` reframed to
  `allocateGivesAFreshUnitDefaultsAfterReusingAFreedSlot` (the −1/−1 fall-back
  seed is the one non-zero default it guards). Net-zero on test count.

## Perf note

The 5 columns are now world location-probes instead of raw dense-array reads —
the accepted step-back ([[feedback_storage_foundation_build_right]]), bounded at
N≈200. The only dense-loop reader was `SquadAlertSystem` (one probe per squad
member); recovered later when bulk consumers walk world tables directly.

## Follow-ups

- **Movement membership-narrowing + path ref** (still open from 3e) — fold
  `int[] path` + `int pathIdx` into `MOVEMENT` and narrow it to actual movers
  (~66 sites/28 files, hot nav loops). The deferred kinematic-capability slice.
- **`AI_STATE` membership-narrowing** — same deferral: restrict to thinking
  entities (turrets/hubs don't decide), bundled with the reader audit.
- **Step 4 — dissolve `UnitRegistry`.** With the dense columns gone, the registry
  is id-mint + dense `Entity[]` + id↔slot map; fold the standalone
  `Crashing`/`MechLoadout` component stores into archetype membership, then hop
  id-mint + the `Entity[]` to the world / sim.
