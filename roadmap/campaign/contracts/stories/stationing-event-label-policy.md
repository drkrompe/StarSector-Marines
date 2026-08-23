# Stationing event label policy

Status: DRAFT

Written: 2026-08-23

## Goal

Give every Garrison trigger and Cadre incident type one shared presentation-key
policy so the local management screen and the player-event card cannot drift.

## Scope

- Move the mapping from event subtype to display-key authority behind one
  API-free shared policy.
- Have both local stationing management and player-event projection consume it.
- Preserve the existing translations, fallback labels, and presentation-only
  boundary.
- Add focused coverage for every known subtype and fallback.

## Constraints

The cleanup changes no event identity, deadline, acknowledgement, launch, or
resolution behavior. Presentation resolves the returned key through `Strings`;
the shared policy must stay headless-friendly.

## Acceptance

- Both surfaces use the same key for every Garrison and Cadre subtype.
- Adding a subtype has one mapping location and tests fail if it lacks a label.
