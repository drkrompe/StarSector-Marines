# Mission offer acceptance

Status: DRAFT

Written: 2026-08-23

## Goal

Make accepting an ordinary mission offer a persisted contract transition before
the operation launches. A selected Strike, Escort, or Planetary Assault must
stop being an offer as soon as the player commits, rather than first changing
state when its result returns.

## Scope

- Add one shared acceptance boundary for ordinary mission offers immediately
  before dispatch.
- Validate the offered row and shared eligibility, persist the accepted campaign
  day and negotiated terms, clear offer expiry, and transition it to active
  work exactly once.
- Preserve the Civil War participation side-lock and opposing-offer withdrawal
  policy without creating a competing acceptance path.
- Keep stationing acceptance in its existing assignment authority.
- Cover ordinary success/failure, Planetary Assault phase handoff, stale or
  duplicate acceptance, and no operation result before acceptance.

## Constraints

Acceptance is neither a mission result nor a second launcher. It must not alter
political settlement, reputation, loot entitlement after terms are locked, or
the response rules for stationing work.

## Acceptance

- An ordinary mission row records acceptance and is no longer eligible to
  expire before the battle result.
- The selected terms remain the contract's terms across all phases.
- Repeated dispatch or replay cannot accept the same offer twice or apply a
  terminal outcome twice.
