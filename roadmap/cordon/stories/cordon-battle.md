# Cordon battle

Status: PLANNED

Written: 2026-10-22

Model: `cordon-nouns.md`. The map (`CordonPlan`, `CordonFront`, `CordonCorridor`) has landed.

## Scope

`BattleSetup.createCordon` and a `CordonBattleFixture` (variant, line posture,
seed, bores), the minimum battle both variants need:

- **Redoubts manned.** Each field redoubt dressed the way the civil-defence
  compound is (gun towers at its gates, slits and wall posts, floodlights) and
  manned by an `ALLY` wall team under garrison authority; picket courses
  between neighbours with a team each, gaps left at the roads.
- **The director bore-sourced from tick zero** over the front's grown bores (no
  perimeter seepage, no evacuation placement).
- **A paved relief road.** The corridor is found on the map and wanders; a
  single paved road from the rear edge through the line to the compound wants
  the generator to keep a stated supply route between places rather than
  prune it.
- **The cordon sequence.** Redoubt states (holding, calling, relieved,
  fallen), tides on the timetable, every beat in the noun doc's table, and
  the end conditions as objectives.
- **The Line's refugees.** Groups of civilians leave the rear hamlets on the
  timetable and walk the country road off the rear edge.
- **The Road's convoys.** Allied truck columns on the timetable drive the
  corridor to the compound, wait while the townspeople board, and drive out;
  they halt while the swarm blocks the road.
- **Allied columns (the Line).** Scripted reinforcement squads walking up a
  rear road to the redoubt holding with the least garrison.
- **The company commander.** The minimum autonomous Marine commander for
  evidence: answer calling redoubts (Line) and screen the convoy (Road).
  Posture and strikes are `cordon-company-command.md`.
- **Evidence.** `commanderEvidence -Pmission=cordon-line` and
  `-Pmission=cordon-road`, a fixture per variant, beats in order.

## Acceptance

Mechanics asserted (a calling redoubt is called, a tide surges every living
bore, a convoy halts on a blocked road and moves on when it clears, refugees
leave by the road); outcomes read in the evidence report.
