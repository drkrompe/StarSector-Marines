# Narrative S3 — Local Cross-Patron Echoes

**Status:** SHIPPED (2026-08-19, `53cda364`)

## Goal

Make a first meeting with a patron feel embedded in an already-lived local
campaign by letting the comms officer mention one recent, measured engagement
with a different house at the same market.

## What shipped

- `PatronEngagementMemory.latestOtherAtMarket` selects the newest valid
  different-house snapshot at a market within an inclusive age window. It
  rejects same-house, different-market, future, stale, and malformed rows.
- `PatronBriefingContextComposer` establishes explicit precedence: S1/S2 direct
  patron history first, S3 local echo only for a first-time patron.
- S3 uses a fixed 180-day window anchored to the frozen offer/acceptance day, so
  rerenders do not drift with the campaign clock.
- Four data-authored local pools state only completion, failure, player
  withdrawal, or employer breach for the other patron. The rendered context may
  include the other patron, contract type, measured outcome, and formatted age.
- Missing or malformed local content fails closed to the unchanged first-time
  briefing. No additional persistence or UI layer was added.

## Verification

- Automated coverage locks eligibility filters, exact age boundary, newest-row
  and id tie-breaking, all four outcome pools, token replacement, deterministic
  rerender, direct-history precedence, missing-content fallback, save/load, and
  source-contract compaction.
- The authored JSON file and full `gradlew test` suite pass.
- Manual UI validation remains deferred by user direction.

## Deferred

- Captain observations, Chronicle/event references, cross-house political
  inference, rumor propagation, trust scoring, longer conversations, and new UI
  remain future stories.
