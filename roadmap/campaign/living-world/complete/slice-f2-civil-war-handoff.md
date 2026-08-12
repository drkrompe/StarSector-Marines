# Slice F2 — Civil-war handoff preparation

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `76c7579a`, `49f057ad`, `9bf2356b`, `bdb45f7b`,
`35ec0ccf`, `77657bb8`, `51fad0ca`

## Goal

Carry a Tier-3 throne claimant through a discoverable, interruptible autonomous
civil war into a persisted exactly-once handoff without allowing the
living-world layer to mutate vanilla faction state or set Tier 4.

## Locked rules

- Shared promotion math stops Tier-3 houses at 1000 progress. Large cascades
  also stop at Tier 3; only the future endgame consumer may set Tier 4.
- `throneClaims[]` persists source-chain-unique prepared handoffs with claimant,
  source/result faction, target market, lifecycle, and preparation/application
  ticks. IDs, capacity growth, legacy backfill, and index reconstruction are
  stable.
- A capped ACTIVE Tier-3 `CLAIM_THRONE` house creates one 180-day `CIVIL_WAR`
  against the strongest ACTIVE same-faction rival by cached power. The claimant
  home market is the whole-market location and discovery risk is 128.
- Resolution interns deterministic result faction identity, prepares one
  handoff, and closes the chain. It does not change rank, stake, market owner,
  faction reputation, or any vanilla object.
- Tier-3 civil wars enter the epic rumor path. Qualified players receive the
  existing threatened-house intervention as a market-wide Strike; success
  before preparation fails the chain and produces no handoff.
- An existing handoff prevents later monthly passes from creating another civil
  war for the claimant.

## Automated verification

- `HousePromotionTest` covers the Tier-3 cap and oversized cascade boundary.
- `CampaignStateThroneClaimColumnsTest` covers identity snapshots, source-chain
  uniqueness, growth sentinels, legacy backfill, index rebuild, and ID recovery.
- `AutonomousChainCreationSystemTest` covers cap/faction/rival eligibility,
  deterministic target selection, whole-market identity, and replay exclusion.
- `ChainAdvancementSystemTest` covers preparation without faction/rank mutation,
  terminal replay prevention, and invalid-faction failure.
- Discovery/intervention tests cover epic market-wide rumor and successful
  pre-handoff interruption.
- Full `gradlew build` passes.
