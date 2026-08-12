# G29 — Rival Strike Garrison trigger

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Connect player-launched rival Strike contracts to the shared reactive
Garrison-defense boundary.

## Locked rules

- An offered, active, or in-progress Strike arms Garrison assignments at the
  target house's market only when the player actually launches the operation.
- The Strike patron is persisted as the attacking house and its faction is
  carried into the defense payload.
- A namespaced event key derived from the unique Strike contract id keeps the
  launch idempotent across repeated UI calls or save/load recovery.
- Terminal, non-Strike, self-targeted, and orphaned contracts cannot arm a
  defense.
- The offensive battle still launches normally; the defense becomes a separate
  local obligation reachable at the attacked market.

## Automated verification

- `RivalStrikeGarrisonServiceTest` covers target-market routing, attacker
  identity, source type, stable event identity, consumed-event rejection, and
  invalid contract filtering.
- The full build covers the briefing launch integration and existing Garrison
  trigger/battle behavior.
