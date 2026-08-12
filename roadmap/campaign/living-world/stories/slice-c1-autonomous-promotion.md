# Slice C1 — Autonomous stake-majority promotion

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `11987f9a`

## Goal

Turn the first living-world stub into a real, deliberately slow political
progression loop using the already-seeded stake board.

## Locked rules

- Only ACTIVE Tier-1 through Tier-3 houses accrue autonomous progress.
- Holdings are evaluated on the house's home market; expansion stakes on other
  markets do not inflate its local rank claim.
- A strict majority of all currently claimed share across that market's
  industries grants exactly one promotion-progress point per daily tick.
- A tie, minority, or empty market grants none.
- Threshold crossing routes through `HousePromotion`, preserving the shared
  carry, cascade, logging, and Tier-4 terminal policy.
- The one-point rate makes autonomous T1→T2 promotion take about 100 days of
  uninterrupted majority control, keeping player contract gains decisive.

## Automated verification

- `AutonomousPromotionSystemTest` covers majority, tie/minority, home-market
  locality, threshold promotion, inactive houses, and the Tier-4 terminal.
- Existing `HousePromotionTest` remains the policy-level test net.
