# Slice E1b — Consolidation ambition re-evaluation

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Prevent a vanished foothold or dormant house from continuing to drive stake
drift and autonomous-chain creation through stale persisted ambition data.

## Locked rules

- An ACTIVE `CONSOLIDATE_STAKE` ambition remains valid only while the house has
  positive share in its targeted industry on its home market.
- Invalid consolidation first clears to `NONE`, then immediately reuses the
  existing strongest-home-industry selector. This deterministically retargets a
  surviving foothold; no local foothold leaves the house at `NONE`.
- DORMANT houses clear consolidation ambition and target.
- Other inactive narrative states and non-consolidation ambitions remain
  untouched for their owning future systems.
- Re-evaluation runs in the existing ambition phase before drift and chain
  creation, preventing stale targets from acting even for one extra tick.

## Automated verification

- `HouseAmbitionSystemTest` covers deterministic retargeting, no-replacement
  clearing, dormant clearing, and preservation of deposed narrative state.
