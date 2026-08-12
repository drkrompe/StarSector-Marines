# G16 — Planetary Assault phase model

**Status:** CODE COMPLETE (2026-08-12)

**Implemented in:** `58a3715f`

## Goal

Define the deterministic mission sequence and staged economy for a 3–5 phase
Planetary Assault before wiring recurrence into the mission resolver.

## Locked rules

- Phase roles are Recon (Sabotage), Softening Strike (Raid), Main Assault
  (Conquest), then optional Mop-up/Consolidation (Assault).
- Each non-final phase pays 15% of total base payout; the final phase pays the
  exact remainder, so staged payouts sum to the offered total.
- Salvage entitlement grows toward the final phase; phase entitlement never
  exceeds the contract-wide negotiated amount.
- A persisted current-phase attempt counter supports deterministic rerolls after
  non-final defeat and resets when the phase advances.
- Attempt data grows, legacy-backfills to zero, and survives contract-table
  compaction aligned with every other SoA column.

## Automated verification

- `PlanetaryAssaultPhaseTest` covers 3/5-phase roles, exact payout sum, salvage
  progression, and invalid shapes.
- `CampaignStateStationingColumnsTest` covers attempt growth/backfill.
- `ContractTableCompactorTest` covers attempt alignment.
