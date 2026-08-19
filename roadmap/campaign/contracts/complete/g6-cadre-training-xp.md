# G6 — Cadre training XP

**Status:** CODE COMPLETE (2026-08-12)

**Implemented in:** `68a3673d`

## Goal

Deliver Cadre's defining non-cash value: passive but meaningful captain growth
while the officer is unavailable for field missions.

## Locked rules

- Cadre awards 200 captain XP per completed 30-day month.
- Missed daily ticks catch up whole months exactly once and cap at term expiry.
- Training uses its own persisted clock, independent of retainer delivery.
- Rank thresholds, remainder carry, cascades, and GENERAL cap match battle XP.
- Missing captain/roster state does not advance the training clock, allowing a
  later retry.
- Garrison and terminal contracts award no training XP.

## Acceptance

- Training-clock SoA growth and legacy backfill are covered.
- Catch-up, duplicate suppression, promotion, expiry cap, missing-captain retry,
  and excluded rows are directly tested.
- Full build green.

## Automated verification

- `CampaignStateStationingColumnsTest` covers training-clock growth/backfill.
- `CadreTrainingSystemTest` covers monthly catch-up, duplicate suppression,
  remainder-carry promotion, expiry cap, retry, and excluded rows.
- `StationingAssignmentServiceTest` remains green with clock initialization.
- `gradlew.bat build` passes on 2026-08-12.
