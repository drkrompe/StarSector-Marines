# G8 — Default extraction foundation

**Status:** CODE COMPLETE (2026-08-12)

**Implemented in:** `1051c22a`

## Goal

Turn a stationing default into one durable, linked recovery obligation without
losing the committed captain or marines before the extraction resolves.

## Locked rules

- Patron deposition defaults active stationing contracts only; mission-mode
  contracts keep their existing resolution path.
- A defaulted assignment with committed personnel creates exactly one OFFERED
  EXTRACTION row at the stationing market.
- The extraction row links back through a persisted source-contract id and
  carries one missed monthly retainer as recoverable payment.
- The defaulted parent remains the authoritative owner of committed personnel.
- Contract cleanup cannot remove a defaulted stationing parent while it still
  owns a captain or marines.

## Automated verification

- `StationingDefaultExtractionSystemTest` covers shape, linkage, duplicate
  suppression, and empty/non-defaulted rejection.
- `ContractLifecycleStationingDefaultTest` locks the stationing-only deposition
  rule.
- `CampaignStateStationingColumnsTest` covers source-link growth/backfill.
- `ContractTableCompactorTest` covers source-link alignment and stranded-parent
  retention.
