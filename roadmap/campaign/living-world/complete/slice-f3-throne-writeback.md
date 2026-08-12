# Slice F3 — throne-claim writeback

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `42f00725`, `ae35056d`, `870d5b96`, `5174f44c`

## What shipped

- `ThroneClaimResolutionSystem` consumes persisted `PREPARED` handoffs through
  an injectable writeback port with `APPLIED`, `ALREADY_APPLIED`, `RETRY`, and
  `REJECTED` outcomes.
- The predeclared `starsector_marines_claimants` Claimant League supplies a
  supported result-faction identity. Public Starsector APIs expose faction
  lookup and market transfer, but not arbitrary runtime faction registration.
- `StarsectorThroneClaimWriteback` owns the sole vanilla mutation boundary. It
  validates source/result factions and target market, checks the postcondition
  first, transfers market plus primary/connected entities, repairs a partial
  transfer, and verifies the result.
- Successful or already-applied writeback marks the claim `APPLIED`, sets the
  claimant Tier 4 with zero progress, records its new local faction, and clears
  its ambition. Transient failures remain prepared for retry.
- Rejected or structurally invalid handoffs become `FAILED` and fail their
  source civil-war chain without granting rank or reputation effects.
- System order is locked after chain advancement and before consolidation and
  discovery. Chronicle processing skips civil wars with a prepared claim, then
  emits their epic terminal dispatch after writeback succeeds.

## Verification

- Unit coverage exercises validation, first application, already-applied
  recovery, partial-transfer repair, retry, rejection, rank finalization, source
  chain failure, and Chronicle gating.
- Claimant faction data is parsed and checked against its CSV registration.
- Full `gradlew build` passed without a manual playtest.

## Deferred

- Sector-wide reputation consequences, persisted behind their own exactly-once
  boundary.
- A faction-flip-specific Chronicle composition pass.
- Three-band contract choices and the player-facing kingmaker capstone.
