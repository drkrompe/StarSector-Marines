# G22 — Cadre incident trigger foundation

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `d07fd3c3`

## Goal

Give active Cadre assignments a durable, exactly-once incident trigger before
choosing the player-facing mission payload and consequences.

## Locked rules

- Cadre incidents use a deterministic 24–36 day cadence derived from contract
  identity and the prior scheduling day.
- The next incident day and pending flag are persisted contract-table columns,
  including capacity growth, compaction, and legacy-save backfill.
- A due incident flips to pending once and remains pending until a later payload
  system consumes it; daily ticks cannot duplicate it.
- No incident is armed on or after the Cadre term end. Garrison contracts do not
  use this Cadre-specific clock.
- Terminal Cadre rows clear any unconsumed trigger so stale incidents cannot
  outlive their assignment.

## Deliberate boundary

This slice does not invent an incident battle, silently borrow troops outside
the stationing detachment, or assign outcome consequences. The next slice can
consume the pending flag once that mission contract is explicit.

## Automated verification

- `StationingIncidentSystemTest` covers deterministic legacy scheduling,
  due/pending idempotence, term bounds, Garrison exclusion, and terminal cleanup.
- Existing SoA growth/backfill and compaction tests cover both new columns.
- Stationing acceptance initializes the persisted incident clock.
