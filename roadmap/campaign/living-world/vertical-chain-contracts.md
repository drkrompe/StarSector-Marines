# Vertical chain contracts

This document fixes the resolution boundary for ambition-driven vertical
schemes before those ambitions are admitted to autonomous chain creation.
`CONSOLIDATE_STAKE` remains the horizontal baseline; vertical chains must earn
their own identity and payload rather than borrowing it.

**Status:** ordinary `PROMOTE` chain CODE COMPLETE (2026-08-12);
`CLAIM_THRONE` still gated.

**Implemented:** `0a00ceb0`, `156ac5b1`, `a4cd42c8`

## Ordinary promotion — locked v1 contract

`PROMOTE` represents a Tier-1/2 house manufacturing the political legitimacy
for its next rank when raw home-market majority is not already carrying it up
organically.

- **Archetype:** append `PROMOTE` to `ChainArchetype`; never repurpose the
  existing `SABOTAGE_PROMOTION` placeholder, whose actor goal is suppressing a
  rival rather than advancing itself.
- **Actor gate:** ACTIVE Tier-1/2 house with `houseAmbition=PROMOTE`, a valid
  next-rank ordinal target, and no active chain. A strict home-market majority
  does not create this chain because `AutonomousPromotionSystem` already gives
  that house a complete organic route.
- **Location:** one contested industry on the actor's home market where both
  actor and an ACTIVE same-faction rival hold positive share. Choose the rival
  with the greatest share; ties choose the lowest industry id, then lowest
  house id. Persist all actor/rival/market/industry identity in the chain row.
- **Tempo:** 60 daily progress against a 60-point threshold. Discovery risk 64,
  higher than the ordinary consolidation plot's 32.
- **Resolution:** seize 20 share from rival to actor in the bound industry,
  grant actor 90 promotion progress through `HousePromotion`, and suppress 30
  promotion progress from the rival. The smaller stake movement says the plot
  was about legitimacy, while still requiring a material local base.
- **Already achieved:** if the actor reaches a rank above the chain's persisted
  starting tier before resolution, resolve the chain without replaying its
  political payload. Another route already accomplished the objective.
- **Failure:** missing/inactive participants, invalid location, a cross-faction
  target, or a Tier-3/4 actor fails the active scheme exactly once.

The +90 value decisively crosses a Tier-1 house entering `PROMOTE` at 75/100 and
crosses a Tier-2 house entering at 225/300, while preserving the shared rank
ladder's remainder-carry semantics. Suppression floors at zero.

## Throne claim — still gated

`CLAIM_THRONE` remains an inert persisted handoff marker. Before mapping it to
`CIVIL_WAR`, the T3-endgame thread must specify:

- its multi-stage chain and contract composition;
- the persisted, exactly-once handoff record consumed by the sole
  vanilla-writing system;
- market/faction selection and reversibility rules;
- what caps ordinary Tier-3 progress before the handoff resolves;
- Chronicle, discovery, intervention, and player-choice outcomes.

No living-world system may infer or perform the vanilla faction flip.
