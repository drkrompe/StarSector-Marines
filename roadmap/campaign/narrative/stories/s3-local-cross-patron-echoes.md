# Narrative S3 — Local Cross-Patron Echoes

**Status:** ACTIVE (2026-08-19)

## Goal

Make a first meeting with a patron feel embedded in an already-lived local
campaign by letting the comms officer mention one recent, measured engagement
with a different house at the same market.

## Locked slice

- Only patrons with no valid prior player engagement are eligible. Returning
  patrons keep the more relevant S1/S2 direct-history callback.
- Select the newest valid engagement whose patron is a different house, whose
  snapshotted market matches the current contract's origin market, and whose
  happened day is between the current offer day and 180 days before it.
- Order candidates by happened day, then immutable engagement id. Insertion
  order, contract-table survival, and unrelated reputation do not participate.
- Render one deterministic, data-authored comms-officer line specific to the
  other engagement's measured `COMPLETED`, `FAILED`, `WITHDREW`, or
  `EMPLOYER_BREACHED` outcome.
- The line may name only the other patron, prior contract type/outcome, and
  measured age. It may not claim the current patron knows, cares, approves, or
  is politically connected.
- No qualifying local fact means the existing first-time briefing remains
  unchanged.

## Acceptance

- Same-patron, different-market, future-dated, more-than-180-day-old, and
  malformed rows are ignored independently.
- The 180-day boundary is inclusive and the newest valid candidate wins.
- First-time patron text is deterministic across rerender and save/load.
- Adding direct history for the current patron suppresses the local echo and
  restores S1/S2 precedence.
- Missing or malformed local-echo content fails closed to no echo.

## Non-goals

- No cross-house relationship inference, rumor propagation, Chronicle/event
  reference, trust score, captain observation, longer conversation, or new UI.
- No change to engagement persistence, contract lifecycle, reputation,
  economy, targeting, or offer generation.
