# Narrative S2 — Relationship Pattern Callbacks

**Status:** ACTIVE (2026-08-19)

## Goal

Let the comms officer recognize a short factual pattern in the company's
history with a returning patron, rather than always describing only the most
recent job.

## Locked slice

- Read the newest two valid S1 engagement snapshots for the current patron.
  Ordering remains happened day, then immutable engagement id.
- Classify every possible outcome pair into exactly one factual pattern:
  `SUCCESS_STREAK`, `RECOVERY`, `PLAYER_SETBACK`,
  `REPEATED_PLAYER_TROUBLE`, `BREACH_AFTER_SUCCESS`,
  `REPEATED_PATRON_BREACH`, or `MUTUAL_TROUBLE`.
- Render one data-authored comms-officer line for that pattern. Lines may name
  the patron, the two contract types/outcomes, prior engagement count, and the
  ordinal number of the current engagement.
- A patron with only one valid prior engagement keeps the S1 newest-outcome
  callback. A first-time patron remains silent.
- Classification and selection are derived at presentation time. No relationship
  score, mutable summary row, or second persistence table is added.
- Invalid ledger rows are skipped independently. If fewer than two valid rows
  remain, presentation falls back naturally to S1 or silence.

## Acceptance

- The two newest valid rows are deterministic across insertion order,
  save/load, replay, and source-contract compaction.
- All sixteen ordered outcome pairs map to the locked seven-pattern vocabulary.
- Two-history patrons use a stable pattern-specific line with all supported
  tokens replaced; one-history patrons still use their S1 pool.
- Adding older history changes the count but cannot change which two outcomes
  determine the pattern.
- Parser/fallback behavior remains fail-closed when continuity content is
  missing or malformed.

## Non-goals

- No inferred trust, forgiveness, patience, motive, patron emotion, or hidden
  political knowledge.
- No captain observations, cross-patron references, chain/event memories,
  authored conversations, new UI, or economic/reputation changes.
