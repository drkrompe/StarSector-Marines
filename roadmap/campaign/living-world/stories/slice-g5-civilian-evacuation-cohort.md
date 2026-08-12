# Slice G5 — civilian evacuation cohort

**Status:** CONTRACT LOCKED (2026-08-12)

## Goal

Give a committed civilian-rescue mission a battle-owned, replay-safe metric
that measures evacuation rather than combat victory or civilian survival.

## Locked contract

- V1 represents the campaign population with eight mission evacuees.
- Registered mission evacuees are distinct from ambient civilian entities.
- Every registered identity begins `ACTIVE` and may transition exactly once to
  `EVACUATED` or `LOST`; duplicate and conflicting notifications are harmless.
- Only crossing the dedicated evacuation boundary counts as evacuation.
- Sealing is terminal and converts remaining active members to lost. An empty or
  unsealed tracker supplies no valid report.
- A valid report preserves `initial = evacuated + lost` and scales to campaign
  stakes as `floor(atRisk * evacuated / initial)`, with exact full rescue.
- Winner, marine casualties, ambient civilians, and survivors still on the map
  never manufacture a rescue count.

## Delivery hunks

1. Add the isolated cohort tracker, immutable report, and scaling tests.
2. Let a battle simulation optionally own that tracker and let mission outcome
   computation consume only a sealed report for campaign-event missions.
3. Add controlled-fixture spawning, shelter/evacuation geometry, terminal rules,
   and dedicated objective behavior.
4. Emit committed rescue missions only after the objective can report honestly;
   keep swarm faction/roster/AI/art in the following payload slice.

## Excluded from this foundation

- Persisting battle entity ids in campaign state.
- Treating generic Extraction victory as rescue success.
- Counting ambient civilians or merely surviving evacuees.
- Distress Net mission emission, swarm content, or manual playtesting.
