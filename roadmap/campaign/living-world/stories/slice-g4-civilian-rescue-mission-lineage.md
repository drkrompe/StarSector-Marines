# Slice G4 — civilian-rescue mission lineage

**Status:** CONTRACT LOCKED (2026-08-12)

## Goal

Carry a committed rescue's identity and stakes through the mission/outcome
boundary, and provide an explicit exactly-once resolution seam without
pretending the current elimination placeholder measures evacuation.

## Hunk plan

1. Append `CAMPAIGN_EVENT` to `MissionSource`; add default-safe
   `campaignEventId` / `campaignEventMarketId` / `civiliansAtRisk` mission
   fields and matching outcome fields plus the `-1` no-report sentinel.
2. Add `CivilianRescueMissionKey` and a pure factory that validates a local
   committed row and creates a zero-payout/zero-salvage Extraction-shaped
   mission. Do not emit it in `MarineOpsContext` yet.
3. Add `CivilianRescueMissionResolution`: validate source, key, explicit event
   id, target market, stakes snapshot, and rescued range before resolving the
   event once.

## Acceptance

- Existing mission constructors retain `campaignEventId = -1`, market `-1`, and
  zero stakes; contract/stationing/story behavior remains unchanged.
- A committed rescue builds one stable mission snapshot; pending, terminal,
  wrong-market, malformed, and missing rows build nothing.
- Generic battle victory with `civiliansRescued = -1` cannot resolve a rescue.
- Explicit zero/partial/full reports resolve with the original event identity
  and are replay-safe; mismatched or out-of-range reports do not mutate state.
- No event mission receives credits, salvage, contract lineage, or industry
  disruption semantics.
- Focused tests and full build pass. Manual playtesting remains skipped.

## Deferred

- Distress Net client emission and briefing deployment.
- Swarm faction/units/AI/art.
- Evacuation objective, representative cohort spawning, shuttle boarding, and
  the battle-side `initial/evacuated/complete` metric.
