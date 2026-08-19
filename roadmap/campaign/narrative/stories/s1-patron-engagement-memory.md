# Narrative S1 — Patron Engagement Memory

**Status:** ACTIVE (2026-08-19)

## Goal

Let the comms officer acknowledge what actually happened the last time the
company worked for a returning patron. The callback should make persistent
houses feel continuous without turning the briefing into an omniscient recap
or a procedural biography.

## Locked first slice

- Persist one immutable engagement row per terminal source contract.
- Snapshot the source contract id, patron, contract type, market, measured
  terminal outcome, and happened day before contract compaction can erase the
  source row.
- Distinguish `COMPLETED`, `FAILED`, `WITHDREW`, and `EMPLOYER_BREACHED`.
  Offer expiry, opposing-offer withdrawal, campaign events, political
  reputation shifts, and debug-only reputation pokes are not engagements.
- Source-contract identity is the exactly-once key. Replay or repeated daily
  ticks return the first row without rewriting its snapshot.
- A new offer from the same patron may receive one deterministic comms-officer
  memory line based on the newest valid prior engagement. The line names only
  persisted facts and composes between the officer's opening frame and the
  current patron brief.
- Memory text lives in a data-authored comms-officer voice bank. It may mention
  the patron, prior contract type, and number of prior engagements, but not
  casualties, motives, hidden politics, or facts that were not snapshotted.
- Legacy saves backfill empty/sentinel storage and naturally show no callback
  until a new engagement is recorded.

## Acceptance

- Completion, failure, voluntary withdrawal, and employer breach each append
  the correct immutable snapshot through their existing terminal authority.
- Multi-phase contracts append only at final completion/failure, not after an
  intermediate phase or reroll.
- Save/load, terminal replay, daily replay, and contract-table compaction cannot
  duplicate or replace a memory.
- A first-time patron gets the unchanged briefing composition.
- A returning patron gets stable text across rerender and save/load; different
  current contract ids can select different variants without changing history.
- Malformed source rows or invalid memory rows fail closed in presentation.

## Non-goals

- No full conversation tree, relationship meter, patron personality mutation,
  captain observation feed, Chronicle copy, or omniscient narrative summary.
- No callback for black-swan events unless a later story explicitly connects an
  event to a patron.
- No numeric balance, reputation, payout, offer eligibility, or contract
  lifecycle changes.
- No manual UI redesign; this slice adds one prose layer to the existing brief.
