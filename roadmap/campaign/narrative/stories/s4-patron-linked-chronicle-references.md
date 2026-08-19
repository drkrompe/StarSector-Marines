# Narrative S4 — Patron-Linked Chronicle References

**Status:** ACTIVE

## Goal

Let the comms officer connect a patron to one recent Chronicle fact the player
has already learned, so political events can surface naturally in contract
briefings without inventing motives or hidden knowledge.

## Contract

- Briefing context precedence is direct S1/S2 patron history, then one S4
  patron-linked Chronicle reference, then the S3 same-market engagement echo.
- S4 considers only `CONFIRMED` Chronicle rows whose actor or target is the
  current patron and whose event type is one of:
  - terminal `CHAIN_OUTCOME` (`RESOLVED` or `FAILED`),
  - `THRONE_CLAIM_APPLIED`, or
  - `KINGMAKER_TESTAMENT`.
- The event must have happened and become known no later than the frozen offer
  day. Its happened day must fall within the inclusive prior 365 days.
- Both linked houses must still resolve to valid persisted house rows with
  display names. Rumors, dormancy, Silent Colony reports, malformed rows,
  future knowledge, stale events, and self-links are ineligible independently.
- Selection is deterministic: newest learned day, then happened day, then
  immutable Chronicle id.
- Data-authored lines may state only the persisted event type, the patron's
  actor/target role, the other house, the terminal chain result where relevant,
  and measured age. They must not infer intent, trust, culpability, or a
  relationship between the event and the offered contract.
- Missing or malformed S4 content fails closed to S3/local or an unchanged
  briefing. S4 adds no persistence, reputation, economy, or UI changes.

## Verification

- Query tests cover every eligibility filter, both patron roles, the exact
  365-day boundary, newest ordering, and immutable-id tie-breaking.
- Composition tests lock precedence, deterministic replay, every supported
  reference pool, token replacement, save/load stability, and missing-content
  fallback.
- Authored JSON validation and the full Gradle test suite pass.
- Manual UI validation remains deferred by user direction.
