# G4 — Stationing assignment lifecycle

**Status:** CODE COMPLETE (2026-08-12)

**Implemented in:** `644b0a1f`

## Goal

Make stationing acceptance and successful term release one exactly-once domain
operation before exposing Garrison/Cadre offers in the UI.

## Locked rules

- Acceptance requires an OFFERED Garrison/Cadre row, an ACTIVE captain, an
  active patron below Tier 4, and enough player marines.
- Acceptance removes the selected marines, binds the captain id, freezes the
  term/retainer, clears offer expiry, and sets the captain `GARRISONED`.
- A second acceptance attempt is a no-op.
- Successful term completion returns all committed marines and restores the
  captain to ACTIVE exactly once.
- DEFAULTED/FAILED/ABANDONED contracts do not auto-return personnel; those need
  extraction/withdrawal consequence flows.

## Acceptance

- Purely testable adapters cover acceptance validation, mutation, duplicate
  suppression, successful release, and failed-release retry.
- `GARRISONED` is appended to the persisted captain status enum.
- Full build green.

## Automated verification

- `StationingAssignmentServiceTest` covers validation, frozen terms, personnel
  removal, captain binding, and duplicate suppression.
- `StationingReleaseSystemTest` covers exactly-once completion return, failed
  delivery retry, and default retention for extraction.
- `gradlew.bat build` passes on 2026-08-12.
