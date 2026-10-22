# Cordon battle

Status: IN PROGRESS

Written: 2026-10-22

Updated: 2026-10-22 — redoubts, director, sequence, company commander and evidence landed; the rest below is open.

Model: `cordon-nouns.md`. The map (`CordonPlan`, `CordonFront`, `CordonCorridor`) has landed, and so
has the battle's skeleton: `CordonSetup.create` and `CordonBattleFixture`, the redoubts dressed and
manned (`CordonGarrisons`), the director bore-only with a pressure share, the cordon sequence
(`CordonSequence`: states, tides on the timetable, beats, the end as an objective), the minimum
company commander (`CordonCommand`), and `commanderEvidence -Pmission=cordon-line|cordon-road`.
The noun doc holds all of it. The scope below is what remains.

## Scope

What the battle still needs, both variants:

- **Picket courses** between neighbouring redoubts with a team each, gaps left
  at the roads (the redoubts themselves are manned).
- **A paved relief road.** The corridor is found on the map and wanders; a
  single paved road from the rear edge through the line to the compound wants
  the generator to keep a stated supply route between places rather than
  prune it.
- **The Line's end on its refugees and the Road's on its convoys.** The
  sequence ends both variants on the window today; the people-are-the-score
  ends, and their beats, come with the refugees and convoys.
- **The Line's refugees.** Groups of civilians leave the rear hamlets on the
  timetable and walk the country road off the rear edge.
- **The Road's convoys.** Allied truck columns on the timetable drive the
  corridor to the compound, wait while the townspeople board, and drive out;
  they halt while the swarm blocks the road.
- **Allied columns (the Line).** Scripted reinforcement squads walking up a
  rear road to the redoubt holding with the least garrison.
- **The company screens the convoy (Road).** The commander answers calls;
  screening the convoy comes with the convoys. Posture and strikes are
  `cordon-company-command.md`.
- **The town's garrison (Road).** The compound in the cut-off town dressed
  and manned as the redoubts are.

## Acceptance

Mechanics asserted (a calling redoubt is called, a tide surges every living
bore, a convoy halts on a blocked road and moves on when it clears, refugees
leave by the road); outcomes read in the evidence report.
