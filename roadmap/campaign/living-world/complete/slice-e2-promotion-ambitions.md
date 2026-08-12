# Slice E2 — Promotion and throne-claim ambitions

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `f022d4ad`, `31bb6875`, `3c58964b`, `57be6214`

## Goal

Let established houses turn from horizontal consolidation toward the rank
ladder, and produce a persisted Tier-3 throne claimant without crossing the
living-world layer's vanilla faction-write boundary.

## Locked rules

- Long-horizon ambition review runs per house every 30 sector days. The last
  review tick persists, grows with the houses table, and legacy saves backfill
  it to `-1` for an immediate first review.
- Only `NONE` / system-owned `CONSOLIDATE_STAKE` / rank-targeted `PROMOTE`
  participate in automatic transitions. `DISPLACE_RIVAL`, externally authored
  targets, and other narrative state remain untouched.
- Tier 1 and Tier 2 consolidation becomes `PROMOTE` at 75% of the current
  rank-progress threshold, provided cached house power is at least the full
  threshold (T1: 75 progress + 100 power; T2: 225 + 300).
- A `PROMOTE` target stores the intended next `HouseRank` ordinal. It remains
  stable until a cadence review sees that rank reached, then returns through
  consolidation before another vertical transition is considered.
- Tier 3 consolidation becomes `CLAIM_THRONE` at 750 progress and 1000 power.
  Its target is the house's persisted faction-registry id: the throne being
  claimed, not a guessed incumbent house.
- `HousePowerSystem` remains before ambition review, so eligibility reads the
  current stake graph.
- `PROMOTE` and `CLAIM_THRONE` intentionally create no autonomous chain yet.
  They may be mapped only after their resolution payloads and the isolated T3
  vanilla-write handoff are specified.

## Automated verification

- `CampaignStateAmbitionColumnsTest` covers capacity growth and legacy-save
  backfill for the persisted cadence column.
- `HouseAmbitionSystemTest` covers exact progress/power boundaries, monthly
  cadence, next-rank target identity, Tier-3 faction target identity, and
  reached-target re-evaluation.
- `CampaignStateSystemOrderTest` locks fresh power before ambition review.
- `AutonomousChainCreationSystemTest` locks vertical ambitions out of the
  existing horizontal consolidation payload.
- Full `gradlew build` passes.
