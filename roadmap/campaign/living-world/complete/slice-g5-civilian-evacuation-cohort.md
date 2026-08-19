# Slice G5 — civilian evacuation cohort

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `cc34a2ab`, `1174cae9`, `03017229`, `a2fa2a70`,
`5d6257f5`, `c870193f`, `d1dae861`, `2cd8416a`, `bee0c6b4`,
`84e9e175`

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

## Excluded from this slice

- Persisting battle entity ids in campaign state.
- Treating generic Extraction victory as rescue success.
- Counting ambient civilians or merely surviving evacuees.
- Swarm faction/roster/AI/art and manual playtesting.

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

## Production payload checkpoint

- Placement selects a residential shelter, eight unique reachable cells, and a
  distant reachable outer-band lift deterministically; invalid attempts are
  discarded before player visibility.
- The installer spawns and registers exactly eight mission-only civilians and
  attaches the objective. Ambient civilians remain outside the identity set.
- A serial system authors their lift paths and removes boarded evacuees from
  live rendering without death/corpse semantics.
- Campaign-event launch uses the dedicated rescue factory. Only the matching
  market exposes one committed mission under an always-open Distress Net client.

## Objective checkpoint

- Controlled tests spawn eight real civilian entities and register only those
  identities; an unregistered ambient civilian standing inside the lift zone
  does not count.
- The objective marks live boundary crossings evacuated, missing identities
  lost, positive partial/full results complete, and measured zero failed.
- Registered deaths flow through the existing once-only death dispatcher.
- Any other terminal battle outcome seals remaining active representatives as
  lost before mission outcome computation.
- Focused lifecycle/objective/outcome tests and the full Gradle build pass.
  Manual playtesting remains intentionally skipped for this session.

## Next

Add the swarm faction, roster, approach/attack behavior, and held art as the
threat payload. The evacuation objective and writeback no longer need to change
to support that content.

## Escort-screen follow-up

`ad11debf` replaces the cohort-wide escort permission with an independent
five-cell leash for each evacuee. Civilians cannot lead their nearest marine by
more than two cells toward the lift, and visible nearby enemies make them seek a
walkable position behind that marine relative to the threat. See
[`swarm-reinforcement-civilian-screen.md`](swarm-reinforcement-civilian-screen.md).
