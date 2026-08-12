# Slice E1c — Selective dormancy dispatches

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Make politically meaningful consolidation visible without flooding the
Chronicle with every low-tier house that quietly disappears.

## Locked rules

- `HOUSE_DORMANT` appends after existing Chronicle event ordinals and snapshots
  the dormant house, home market, transition day, and confirmed confidence.
- A house with a player-reputation row produces an `INTIMATE` dispatch.
- Otherwise Tier-3+ dormancy produces an `EPIC` dispatch; untouched Tier-1/2
  dormancy remains silent.
- The event appends during the ACTIVE → DORMANT transition, so repeat ticks
  cannot duplicate it.
- Debug Chronicle formatting describes departure from active politics without
  rendering irrelevant chain target/industry fields.

## Automated verification

- `CampaignStateChronicleColumnsTest` covers dormancy snapshot payload.
- `HouseConsolidationSystemTest` covers intimate/epic/silent bands and replay.
- `CampaignDebugIntelChronicleTest` covers dormancy wording.
