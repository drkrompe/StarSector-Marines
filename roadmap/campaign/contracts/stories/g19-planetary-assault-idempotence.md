# G19 — Planetary Assault result idempotence

**Status:** CODE COMPLETE (2026-08-12)

**Implemented in:** `80f12862`

## Goal

Prevent duplicate or stale battle-result callbacks from advancing a
non-terminal Planetary Assault more than once.

## Locked rules

- Each assault mission has a parseable identity containing contract id, current
  zero-based phase, and current attempt.
- Resolver writeback must match all three values against the live contract row.
- A successful phase immediately makes its old result stale by advancing phase
  and resetting attempt.
- A non-final defeat immediately makes its old result stale by incrementing
  attempt.
- Malformed, negative, wrong-contract, duplicate, and stale identities no-op
  before state, reputation, or political impact mutation.

## Automated verification

- `PlanetaryAssaultMissionKeyTest` covers round-trip and malformed identities.
- `PlanetaryAssaultResolutionTest` covers duplicate victory and defeat
  suppression in addition to the G17 transition matrix.
- Full build green.
