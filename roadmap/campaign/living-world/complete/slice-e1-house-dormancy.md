# Slice E1a — Empty-house dormancy

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `975db3ba`, `f736a7d6`

## Goal

Let the political board consolidate by making houses that lose their final
stake inert, without deleting rows or guessing about never-seeded actors.

## Locked rules

- Only ACTIVE houses are evaluated.
- “Empty” means the house has at least one historical stake row and every one
  of its stake rows, across all markets and industries, has non-positive share.
- A house with even one positive share remains ACTIVE.
- A house with no stake rows remains ACTIVE; creation/seeding may still be in
  progress and no historical ownership has been lost.
- Empty houses transition to `DORMANT`; ids, rows, and lookup indexes remain
  stable. Later ticks are naturally idempotent because only ACTIVE rows qualify.
- Consolidation runs after chain resolution and before ordinary contract
  generation so same-day political losses can suppress new offers.
- Dormancy fails every ACTIVE chain where the house is actor or target, recording
  the consolidation day without overwriting an existing terminal outcome.
- The dormant patron's OFFERED contracts expire and accepted ordinary work
  defaults without a player-reputation penalty. EXTRACTION remains available so
  already-stranded personnel can still be recovered.

## Automated verification

- `HouseConsolidationSystemTest` covers all-market emptiness, one-share survival,
  never-seeded/non-active exclusions, stable identity, repeated ticks, chain
  termination, contract closure, and extraction preservation.
- `CampaignStateSystemOrderTest` locks advancement → consolidation → generation.
