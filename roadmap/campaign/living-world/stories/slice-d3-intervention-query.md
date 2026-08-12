# Slice D3c — Discovered-threat intervention seam

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Give future counter-contract generation a stable way to find a threatened
house's learned, unresolved plot without coupling it to Chronicle rendering.

## Locked rules

- `ChainDiscovery.findActiveThreatAgainst` returns only discovered, ACTIVE,
  autonomous chains targeting the requested house.
- Multiple threats choose earliest discovery day, then lowest stable chain id.
- Unknown, terminal, player-backed, and differently targeted chains are absent.
- Debug Chronicle rows now render event type and confidence explicitly: active
  rumors say an actor is “moving against” a target; outcomes remain confirmed.
- This hunk exposes the intervention query but does not generate an offer; offer
  lifecycle and deduplication remain the next contract-layer decision.

## Automated verification

- `ChainDiscoveryTest` covers deterministic selection and all exclusion rules.
- `CampaignDebugIntelChronicleTest` covers confirmed and rumor wording.
