# Narrative S1 — Patron Engagement Memory

**Status:** SHIPPED (2026-08-19, `1b950e48`)

## Goal

Let the comms officer acknowledge what actually happened the last time the
company worked for a returning patron, without turning briefings into an
omniscient recap or procedural biography.

## What shipped

- An append-only `patronEngagements[]` SoA ledger snapshots source contract,
  patron, type, market, measured outcome, and happened day.
- `COMPLETED`, `FAILED`, `WITHDREW`, and `EMPLOYER_BREACHED` are captured by
  the existing production terminal authorities. Source-contract identity makes
  the combined reputation-and-memory writeback replay-safe.
- Offer expiry, generated extraction completion, campaign events, political
  reputation changes, and debug-only reputation edits do not create memories.
- The ledger survives source-contract compaction and save/load; legacy saves
  receive empty sentinel-backed columns.
- The comms officer selects one deterministic line for the newest valid prior
  engagement and composes it between the opening frame and current patron body.
  Text is data-authored in `comms_officer_voice.json` and limited to persisted
  facts.
- First-time patrons, the current source contract, and malformed memory rows
  fail closed to the unchanged briefing.

## Verification

- Focused ledger, voice parser, callback composition, briefing ordering,
  lifecycle, withdrawal, breach, and reputation-authority tests pass.
- Full `gradlew test` passes.
- Manual UI validation was explicitly deferred for this session.

## Deferred

- Richer patron personality evolution, target-specific sequences, captain
  observations, Chronicle/event memories, conversation trees, and cross-patron
  references remain future stories.
