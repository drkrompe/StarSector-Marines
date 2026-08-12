# G17 — Planetary Assault mission loop

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Turn a Planetary Assault offer into a recurring 3–5 mission sequence with
staged rewards and distinct non-final/final failure semantics.

## Locked rules

- OFFERED and IN_PROGRESS Planetary Assault rows emit the current phase mission;
  accepted assaults remain visible as local patron clients even if relations
  become hostile.
- Mission id and RNG include contract, phase, and persisted attempt, so a
  non-final defeat produces a stable but genuinely rerolled retry.
- Returning from Results invalidates cached mission lists so the next phase or
  rerolled attempt replaces the resolved dossier immediately.
- Phase mission type, title, payout, and salvage come from G16 policy.
- Victory advances one phase, resets attempts, and applies the normal political
  shift. Only final victory completes and awards completion reputation/MRB.
- Non-final defeat stays IN_PROGRESS on the same phase and increments attempt;
  it applies no contract-failure reputation. Final-phase defeat fails normally.
- Ordinary one-shot and system-generated Recovery behavior is unchanged.

## Automated verification

- `PlanetaryAssaultResolutionTest` covers advancement, retry, reset, completion,
  final failure, and invalid-state rejection.
- `PlanetaryAssaultMissionAvailabilityTest` covers recurring visibility without
  reopening ordinary IN_PROGRESS contracts.
- G16 policy tests cover the staged mission shape consumed here.
