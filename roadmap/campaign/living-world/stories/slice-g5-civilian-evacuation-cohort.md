# Slice G5 — civilian evacuation cohort

**Status:** FOUNDATION CODE COMPLETE (2026-08-12)

**Implemented:** `cc34a2ab`, `1174cae9`, `03017229`

## Goal

Give a committed civilian-rescue mission a battle-owned, replay-safe metric
that measures evacuation rather than combat victory or civilian survival.

## Locked contract

- V1 represents the campaign population with eight mission evacuees.
- Registered mission evacuees are distinct from ambient civilian entities.
- Every registered identity begins `ACTIVE` and may transition exactly once to
  `EVACUATED` or `LOST`; duplicate and conflicting notifications are harmless.
- Only crossing the dedicated evacuation boundary counts as evacuation.
- Sealing requires the full expected cohort, is terminal, and converts remaining
  active members to lost. An incomplete or unsealed tracker supplies no report.
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

## Foundation checkpoint

- The isolated tracker enforces expected-count registration, unique positive
  identities, one-way transitions, replay safety, and terminal sealing.
- Its immutable report validates conservation and scales partial results using
  overflow-safe arithmetic while preserving exact full rescue.
- Every battle simulation owns the tracker, but generic battles leave it empty.
- Mission outcome computation consumes a sealed report only for
  `CAMPAIGN_EVENT`; unsealed remains `-1`, measured zero remains `0`, and battle
  victory does not enter the calculation.
- Focused tracker/outcome tests and the full Gradle build pass. Manual playtest
  was intentionally skipped for this session.

## Next hunk

Add controlled-fixture shelter and evacuation-zone placement, register exactly
eight spawned mission civilians, and drive escape/loss transitions through a
dedicated objective without touching ambient civilian accounting.
