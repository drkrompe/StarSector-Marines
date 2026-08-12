# G15 — Planetary Assault offers

**Status:** CODE COMPLETE (2026-08-12)

**Implemented in:** `d567c838`

## Goal

Put the Tier-3 anchor contract into the production and debug offer pipelines
with an explicit multi-phase economic shape.

## Locked rules

- Only active Tier-3 patrons can offer Planetary Assault; Tier 1/2 and Tier 4
  standard offers reject it.
- Tier-3 generation has a 15% Planetary Assault branch before its existing
  stationing/one-shot mix.
- Production assaults roll 3–5 phases deterministically from the patron/day
  RNG. Debug-forced assaults use the stable four-phase midpoint.
- Total base payout is Cr. 180,000 and baseline salvage is 80%.
- Assaults require an active target house and retain the ordinary per-patron
  and global offer caps.
- Debug intel exposes an explicit local Planetary Assault spawn control.

## Automated verification

- `ContractOfferTemplateTest` covers rank gates, economy, and 3–5 phase shape.
- `DebugContractOfferSpawnerTest` covers the forced four-phase production shape.
- Existing generator tests lock the unchanged lower-tier offer behavior.
