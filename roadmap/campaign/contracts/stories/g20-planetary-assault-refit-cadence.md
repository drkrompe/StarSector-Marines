# G20 — Planetary Assault refit cadence

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Enforce the designed between-phase refit window instead of allowing an entire
Planetary Assault sequence to be replayed immediately from the same visit.

## Locked rules

- Every non-final victory or defeat sets the next phase/attempt ready day to
  three campaign days after resolution.
- The current mission remains unavailable before that day and becomes available
  exactly on it; the IN_PROGRESS contract/client remains persisted meanwhile.
- Completion and final failure clear the ready-day clock.
- Ready-day state grows, legacy-backfills to `-1` (immediately ready), and
  remains aligned through contract-table compaction.
- Offer and first-phase availability remain immediate.

## Automated verification

- `PlanetaryAssaultResolutionTest` covers ready-day stamping and terminal clear.
- `PlanetaryAssaultMissionAvailabilityTest` covers before/on-boundary gating.
- `CampaignStateStationingColumnsTest` and `ContractTableCompactorTest` cover
  persistence mechanics.
- Full build green.
