# Narrative S2 — Relationship Pattern Callbacks

**Status:** SHIPPED (2026-08-19, `cbfebaef`)

## Goal

Let the comms officer recognize a short factual pattern in the company's
history with a returning patron, rather than always describing only the most
recent job.

## What shipped

- `PatronEngagementMemory.history` selects the newest two valid snapshots in
  one pass, ordered by happened day and then immutable engagement id, while
  counting all valid prior engagements.
- Every ordered pair of `COMPLETED`, `FAILED`, `WITHDREW`, and
  `EMPLOYER_BREACHED` maps to one of seven patterns: success streak, recovery,
  player setback, repeated player trouble, breach after success, repeated
  patron breach, or mutual trouble.
- Pattern lines are data-authored in `comms_officer_voice.json`, selected
  deterministically from the current contract and both remembered engagement
  ids, and limited to persisted facts.
- One-history patrons retain the S1 latest-outcome callback. Missing or invalid
  S2 content falls back to S1; malformed ledger rows are skipped independently.
- Added an explicit current-engagement ordinal token and corrected the S1
  authored lines that previously described the prior count as that ordinal.

## Verification

- All sixteen ordered outcome pairs are covered.
- Out-of-order insertion, malformed rows, save/load, source-contract
  compaction, deterministic rendering, token replacement, S1 fallback, and the
  authored JSON file are covered by automated tests.
- Focused campaign narrative tests and the full `gradlew test` suite pass.
- Manual UI validation remains deferred by user direction.

## Deferred

- Trust or relationship scores, inferred motive or emotion, captain
  observations, cross-patron references, chain/event memories, longer
  sequences, conversation trees, and new UI remain future stories.
