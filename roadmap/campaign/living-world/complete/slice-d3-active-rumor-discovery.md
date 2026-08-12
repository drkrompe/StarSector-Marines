# Slice D3b — Deterministic active-chain discovery

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `b006dc3`

## Goal

Let relevant autonomous plots surface as uncertain Chronicle rumors before
resolution, without evaluating or printing every background chain.

## Locked rules

- Only ACTIVE autonomous chains involving a player-touched house or Tier-3+
  actor enter rumor evaluation; player-backed and untouched low-tier rows do not.
- The first roll occurs seven days after initiation, then at most once per
  chain-relative seven-day window. The persisted check day prevents re-entry.
- Rolls are deterministic from stable chain id + window in `0..255`.
- Effective chance starts at unsigned `discoveryRisk` and grows linearly by up
  to another base-risk amount as progress approaches threshold, capped at 255.
- Success records first discovery day and appends one immutable `RUMOR` event.
  The later confirmed outcome remains a distinct D2 dispatch.

## Automated verification

- `ActiveChainDiscoveryTest` covers minimum age, intimate/epic bands, silent
  eligibility, relative-window exactness, player-chain exclusion, risk scaling,
  deterministic rolls, and single-rumor behavior.
