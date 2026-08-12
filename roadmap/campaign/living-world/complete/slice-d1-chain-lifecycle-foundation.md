# Slice D1a — Autonomous chain lifecycle foundation

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `3137f238`

## Goal

Give autonomous political chains enough persisted identity to advance and
resolve without pretending that their absent player patron is also their actor.

## Locked rules

- `chainPatron == -1` continues to mean that no player-contract patron exists.
- Every new chain separately records the house politically pursuing its
  outcome; legacy player-backed chains infer that actor from their patron.
- Autonomous chains bind their target market and industry when created.
- Chain lifecycle is append-safe: `ACTIVE`, `RESOLVED`, or `FAILED`.
- Last-advance and resolved ticks persist so daily advancement and terminal
  effects can be exactly-once across save/load and debug re-entry.
- Legacy autonomous rows have no recoverable actor and remain safely inert.

## Automated verification

- `CampaignStateChainColumnsTest` covers player-backed identity, autonomous
  identity/location, capacity growth sentinels, and legacy-save backfill.
