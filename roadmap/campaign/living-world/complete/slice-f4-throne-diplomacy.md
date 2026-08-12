# Slice F4 — throne-claim diplomacy

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `ad6ff5fd`, `527535fb`

## What shipped

- Every throne claim persists a consequence lifecycle and applied tick separate
  from ownership state, including growth and legacy-save backfill.
- Applied claims make the Claimant League and source faction mutually hostile at
  a `-0.5` ceiling. Existing relationships worse than hostile are preserved.
- The diplomacy adapter checks both directions before mutation, repairs a partial
  prior application, verifies both postconditions, retries transient failure, and
  rejects invalid faction identity.
- Consequences run inside the existing isolated throne-resolution system after
  ownership succeeds. A diplomacy failure cannot roll back or replay a completed
  market transfer or Tier-4 promotion.
- Autonomous success is player-reputation-neutral. Player changes require an
  explicit, persisted kingmaker choice rather than inferred involvement.

## Verification

- Persistence tests cover initialization, capacity growth, and legacy backfill.
- Adapter tests cover first apply, already applied, partial repair, preservation
  of worse standing, unavailable state, invalid identity, and failed verification.
- System tests cover ownership ordering, independent retries, exactly-once local
  completion, and terminal diplomacy rejection.

## Deferred

- A faction-flip-specific Chronicle event and presentation.
- Three-band civil-war contracts, explicit player attribution, and resulting
  claimant/incumbent player-reputation deltas.
- The player-facing kingmaker capstone.
