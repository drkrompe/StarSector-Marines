# Slice C2b — Weekly stake drift

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `daf3ef1b`

## Goal

Make local industry shares breathe on their own at a slow, deterministic tempo.

## Locked rules

- Drift runs every seventh sector day and evaluates each house at most once on
  that day; the last evaluated day persists across save/load and debug re-entry.
- Only ACTIVE houses with `CONSOLIDATE_STAKE` and a positive foothold in their
  targeted home-market industry act.
- An actor siphons 3–5 byte-share from the strongest ACTIVE local rival whose
  share is positive but weaker than its own.
- When no weaker rival exists, the actor claims from the industry's unclaimed
  faction-baseline remainder. This lets a contender build until it can
  challenge the incumbent instead of freezing the seeded hierarchy forever.
- Amount is deterministic from house, industry, and sector day. Stake movement
  routes through `StakeLedger`, preserving ceilings and tombstone revival.
- Ambitions assign before drift; drift updates stakes before the day's
  autonomous-promotion majority calculation.

## Automated verification

- `StakeDriftSystemTest` covers cadence, exact-once re-entry, rival choice,
  unclaimed expansion, filtering, deterministic magnitude, and bounds.
- `CampaignStateDriftColumnsTest` covers capacity growth and legacy backfill.
- `CampaignStateSystemOrderTest` locks ambition → drift → promotion ordering.
