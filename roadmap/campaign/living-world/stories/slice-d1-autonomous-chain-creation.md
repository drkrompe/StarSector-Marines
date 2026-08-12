# Slice D1b — Autonomous chain creation

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Turn persisted consolidation ambitions into occasional, concrete NPC plots.

## Locked rules

- Creation evaluates every 30 sector days, after ambition assignment and
  relationship interaction but before chain advancement.
- Only ACTIVE `CONSOLIDATE_STAKE` actors with a positive home-market foothold
  and no other active chain qualify.
- The target is the strongest ACTIVE rival holding positive share on that
  industry; ties choose the lowest stable house id.
- Off-market, dormant, tombstoned, and uncontested candidates do not qualify.
- Created rows are autonomous (`patron == -1`), retain the real actor and bound
  market/industry, inherit actor tier, and start at a fixed 45-point threshold.
- An active player-backed chain also occupies the actor, preventing parallel
  autonomous plots; terminal chains do not prevent a future monthly plot.

## Automated verification

- `AutonomousChainCreationSystemTest` covers cadence, rival selection and ties,
  row payload, active-chain exclusion, terminal replacement, and eligibility.
- `CampaignStateSystemOrderTest` locks creation before advancement.
