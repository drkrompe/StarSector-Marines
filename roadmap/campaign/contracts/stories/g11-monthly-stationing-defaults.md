# G11 — Monthly stationing defaults

**Status:** CODE COMPLETE (2026-08-12)

**Implemented in:** `0516486c`

## Goal

Turn stationing non-payment from a deposition-only event into a deterministic,
power-priced monthly risk that feeds the shipped extraction flow.

## Locked rules

- Default checks occur once per completed 30-day stationing month and catch up
  missed daily ticks exactly once from a persisted clock.
- The monthly chance starts at 8% for a zero-power patron, falls one percentage
  point per 100 power, and floors at 1%.
- Rolls are deterministic from contract id and checkpoint day for save/reload
  stability.
- Patron deposition defaults immediately; mission-mode contracts are excluded.
- Defaults run before retainer and Cadre XP delivery. Normal term expiry remains
  after both, preserving the successful final-month payment/training behavior.
- Legacy assignments with no default clock baseline on first load without
  retroactive rolls.

## Automated verification

- `StationingDefaultSystemTest` covers deposition, due-boundary behavior,
  catch-up/exactly-once checks, legacy baseline, and the power curve.
- `CampaignStateStationingColumnsTest` covers clock growth/backfill.
- `ContractTableCompactorTest` covers clock alignment during cleanup.
- `CampaignStateSystemOrderTest` locks default/payment/training/expiry ordering.
