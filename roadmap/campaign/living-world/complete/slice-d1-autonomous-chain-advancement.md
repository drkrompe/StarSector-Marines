# Slice D1c — Autonomous chain advancement and resolution

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `1d6ca71c`

## Goal

Let NPC political chains mature into exactly-once world-state changes without
advancing player-backed work behind the player's back.

## Locked rules

- Each ACTIVE autonomous chain gains one progress point per distinct sector
  day; its persisted last-advance tick prevents same-day re-entry.
- Player-backed chains never advance in the autonomous tick system.
- Reaching threshold seizes 40 share from target to actor through `StakeLedger`
  and awards 30 promotion progress through `HousePromotion`.
- Resolution writes terminal state and day after applying effects, so later
  Chronicle/discovery work has a persisted event seam and cannot replay it.
- A chain whose actor or target is no longer ACTIVE fails without effects.
- Legacy autonomous chains whose actor cannot be recovered remain ACTIVE but
  inert rather than attributing their outcome to a guessed house.

## Automated verification

- `ChainAdvancementSystemTest` covers daily exact-once behavior, player-chain
  exclusion, stake + promotion resolution, terminal replay prevention,
  participant failure, and legacy actorless rows.
