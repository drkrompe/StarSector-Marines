# G26 — Garrison defense trigger foundation

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Turn concrete attacks against a protected market into one persisted reactive
Garrison defense, with a shared boundary for every campaign event producer.

## Locked rules

- An active Garrison at the attacked market transitions to IN_PROGRESS and
  persists the event key, source, day, attacker house/faction, captain, and
  committed marine pool.
- Event keys make triggering idempotent even if a producer reports the same
  attack on multiple daily ticks or after the defense returns to ACTIVE.
- Rival strikes cannot target their own patron. The shared trigger accepts
  rival-strike, vanilla-raid, and internal-flip sources.
- The vanilla adapter watches active `GenericRaidFGI` payload phases only when
  the raid exposes an explicit `allowedTargets` market list. It does not guess
  which market broad any-hostile-target raids will choose.
- Every active Garrison at the attacked market arms; Cadres and assignments at
  other markets remain untouched.

## Automated verification

- `GarrisonDefenseTriggerTest` covers market/type filtering, persisted payload,
  rival validation, multiple defenders, and duplicate-event rejection.
- `VanillaRaidGarrisonSystemTest` covers producer handoff and stable event keys.
- Existing SoA growth/backfill, compaction, assignment, and system-order tests
  cover the new persisted columns and daily placement.
