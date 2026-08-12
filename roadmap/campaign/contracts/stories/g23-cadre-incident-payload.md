# G23 — Cadre incident payload

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `77ce5ead`

## Goal

Turn the generic pending bit into a stable, player-visible incident whose
future resolver knows exactly which stationed detachment is involved.

## Locked rules

- Every armed incident persists one of three design-specified archetypes:
  factory accident, live-fire raid, or defector lead.
- Archetype selection is deterministic from contract identity and due day;
  save/load and repeat ticks cannot reroll it.
- The payload binds contract, due day, market, stationed captain, and committed
  marine count. It never consults fleet cargo or the active-captain pool.
- Active-assignment management shows the pending incident and its on-site
  detachment.
- Terminal and non-pending rows expose no incident payload.

## Deliberate boundary

The standard battle launcher only accepts fleet-available captains and cargo
marines. This slice does not misrepresent the stationed detachment by sending
different personnel. Response choices and a stationing-aware battle handoff
remain the next integration slice.

## Automated verification

- `StationingIncidentPayloadTest` locks payload identity and eligibility.
- `StationingIncidentSystemTest` locks deterministic type selection and
  repeat-tick stability.
- Existing SoA growth/backfill and compaction tests cover the new type column.
