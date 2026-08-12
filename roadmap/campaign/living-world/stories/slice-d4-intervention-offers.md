# Slice D4b — Threatened-house intervention offers

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Turn a learned active plot into one time-bounded, correctly attributed player
offer, then withdraw it if the political moment passes.

## Locked rules

- After discovery, each active autonomous threat can create at most one Strike
  offer. Any prior intervention row, including terminal history, deduplicates it.
- Threat target is the patron; plotting actor is the contract target. The offer
  snapshots the chain's market and industry.
- `contractChainId` remains `-1`; `contractOpposedChainId` binds the hostile
  source chain.
- Standard patron eligibility still applies. Ineligible discovered threats stay
  queryable and can generate later if eligibility changes before resolution.
- Offer lifetime is at most seven days and shortens to end before the remaining
  chain threshold. A terminal or missing source expires any still-OFFERED row.
- Accepted interventions are not silently withdrawn; their mission-resolution
  semantics land in the next hunk.

## Automated verification

- `ThreatInterventionOfferSystemTest` covers payload, deduplication, bounded
  expiry, terminal withdrawal, and eligibility/discovery/player-chain filters.
- `CampaignStateSystemOrderTest` locks discovery before intervention generation.
