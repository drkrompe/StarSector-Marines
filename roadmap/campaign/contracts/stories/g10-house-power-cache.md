# G10 — House power cache

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Populate the existing `housePower[]` cache from the stake ledger so default
risk and autonomous progression can consume actual political holdings.

## Locked rules

- Power is the non-negative sum of every stake share owned by the house.
- The cache is fully rebuilt each campaign tick, so transfers and tombstones
  are reflected without incremental bookkeeping drift.
- Orphaned stake rows are ignored safely and stale cache values are cleared.
- Power is available before autonomous promotion and contract-default systems
  run in the daily schedule.

## Automated verification

- `HousePowerSystemTest` covers multi-stake aggregation, stale-cache rebuild,
  orphan handling, and subsequent updates.
- Full build green.
