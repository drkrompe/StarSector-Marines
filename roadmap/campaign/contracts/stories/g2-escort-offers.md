# G2 — Rank-gated Escort offers

**Status:** CODE COMPLETE — in-game smoke pending (2026-08-12)

**Implemented in:** `df0a5d19`

## Goal

Generate the first non-STRIKE contract end to end without sending unsupported
stationing or multi-phase types through the one-battle mission path.

## Locked vertical

- Tier 1 patrons continue to offer STRIKE only.
- Tier 2 and Tier 3 patrons roll a 35% Escort / 65% Strike mix within the
  mission-mode branch. G5 later added a separate stationing branch ahead of it.
- Escort uses the one-phase mission path as `MissionType.EXTRACTION`.
- Escort baseline is Cr. 30,000 multiplied by patron tier (T2 1.5x, T3 3x),
  with 10% salvage and no retainer.
- Escort victories advance the patron but do not seize an industry stake from
  the hostile house.
- Existing STRIKE baseline is Cr. 25,000 and uses the same tier multiplier.
- Tier 4, Garrison, Cadre, Planetary Assault, and system-generated Extraction
  contracts remain outside the standard offer generator.

## Acceptance

- Offer type/profile selection is pure and deterministic from the supplied RNG.
- Tier gates and payout/salvage shapes have direct tests.
- Contract-to-mission mapping rejects unsupported types instead of rendering
  them as raids.
- Contract impact policy keeps territorial transfer on Strike/Planetary Assault.
- Existing Tier-1 generator invariants remain green.
- Full build green; Tier-2 patron UI/battle smoke remains the shipping gate.

## Automated verification

- `ContractOfferTemplateTest` covers rank gates, type mix branches, tier payout,
  and salvage defaults.
- `ContractMissionProfileTest` covers supported mappings and rejects every
  unsupported contract type.
- `ContractImpactPolicyTest` freezes territorial/non-territorial effects.
- `ContractGeneratorTest` keeps the shipped Tier-1 behavior and caps green.
- `gradlew.bat build` passes on 2026-08-12.
