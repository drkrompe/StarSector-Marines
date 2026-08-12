# Slice C2a — Minimal horizontal ambitions

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Give the weekly drift loop a deterministic persisted target without prematurely
shipping the larger hidden-trait and vertical-loyalty design.

## Locked rules

- An ACTIVE house whose ambition is `NONE` adopts `CONSOLIDATE_STAKE` toward
  the home-market industry where it currently has its largest positive share.
- Equal shares choose the lowest interned industry slot, making assignment
  deterministic across repeated ticks and saves.
- Tombstoned and off-market stakes do not qualify. Houses with no local
  foothold remain `NONE`, providing intentionally static background actors.
- Existing ambitions are never overwritten; future trait/chain systems retain
  ownership of re-evaluation.
- Assignment runs before autonomous promotion and works for already-seeded
  saves, not only new genesis boards.

## Automated verification

- `HouseAmbitionSystemTest` covers strongest-stake selection, deterministic
  ties, locality, tombstones, inactive houses, and preserved ambitions.
- `CampaignStateSystemOrderTest` locks assignment before promotion/drift work.
