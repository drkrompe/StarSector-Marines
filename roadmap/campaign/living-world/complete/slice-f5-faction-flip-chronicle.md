# Slice F5 — faction-flip Chronicle

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `d7be2649`, `31be86ed`

## What shipped

- `THRONE_CLAIM_APPLIED` is an append-only Chronicle event discriminator.
- The immutable event snapshot records source chain, claimant, displaced rival,
  source faction, result faction, market, ownership-write day, and learned day.
- Chronicle faction columns grow with the table and backfill to `-1` for legacy
  saves and non-faction events.
- Discovery continues to wait while a handoff is `PREPARED`. An `APPLIED` civil
  war now emits the dedicated faction-flip event exactly once; failed handoffs
  retain the ordinary failed-chain path.
- Debug intel renders the concrete transition instead of a generic `RESOLVED`
  line: claimant, rival, source → result, market, happened day, and learned day.

## Verification

- Chronicle storage tests cover immutable identity, sentinels, growth, and
  legacy-save backfill.
- Discovery tests cover prepared blocking and applied special-event creation.
- Debug-intel tests lock the faction-flip dispatch summary.

## Deferred

- Player-facing Chronicle delivery beyond the current debug-intel surface.
- Three-band civil-war contracts and explicit player attribution.
- The kingmaker capstone and its player-reputation consequences.
