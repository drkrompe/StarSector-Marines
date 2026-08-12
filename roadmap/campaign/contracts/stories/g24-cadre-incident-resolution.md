# G24 — Cadre incident resolution contract

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Define the exactly-once campaign writeback that a later response choice or
stationing-aware battle result must call.

## Locked rules

- Resolution keys on contract id, incident due day, and persisted archetype.
  Stale, mismatched, and duplicate results cannot mutate the assignment.
- Casualties debit `contractMarinesCommitted`, never player fleet cargo.
- An incident with surviving marines and captain keeps the parent Cadre ACTIVE,
  clears the payload, and schedules the next deterministic incident within the
  remaining term.
- A wiped detachment or lost captain fails the assignment and schedules no
  further incident.
- If the next cadence point falls on or after term expiry, no new incident is
  scheduled.

## Deliberate boundary

This domain service is independent of the ordinary mission bridge, which would
remove fleet cargo casualties and complete the parent contract. A later launch
hunk must identify stationing incidents explicitly and route their outcome here.

## Automated verification

- `StationingIncidentResolutionTest` covers survivor continuation, casualty
  clamping, detachment/captain loss, term bounds, and stale/duplicate rejection.
