# Cadre incident failure reputation

Status: DRAFT

Written: 2026-08-23

## Goal

Make a failed Cadre incident settle reputation through the same shared
contract-outcome authority as an equivalent failed Garrison response.

## Scope

- Trace the Cadre battle and no-force failure paths through the shared
  exactly-once settlement policy.
- Apply the intended failed-contract relationship and MRB consequence once,
  without changing successful Cadre continuation or lapse consequences.
- Add focused coverage for battle failure, no-force failure, lapse, and replay
  safety.

## Constraints

Do not introduce type-local reputation bookkeeping or make an employer breach
look like player failure. A lapse retains its deliberately stronger policy.

## Acceptance

- A Cadre incident that terminates its assignment records one failed outcome
  and the shared failure consequences.
- Replaying the resolution does not apply reputation twice.
- Successful incidents and employer-breach recovery keep their current distinct
  outcomes.
