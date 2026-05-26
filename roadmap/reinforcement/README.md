# Reinforcement

The orchestration layer for "adding more to the fight" in the battle
sim. Sits above any single delivery means: many trigger sources
(garrison depletion, objective lost, scripted timer) post a generic
request, and the system picks a feasible **means provider** (walk-in,
convoy, shuttle) to fulfill it. Dispatch is resource-gated via
`BattleResources` — compound-driven ticket production (alive ARMORYs →
REINFORCEMENT tickets, alive COMMAND_POSTs → future AIRSTRIKE tickets).

## Contents

- [`architecture.md`](architecture.md) — system design: request shape,
  means-provider interface, trigger registry, rally-point selection,
  resource-gated dispatch (BattleResources), v1–v3 cuts, open questions.
- [`faction-roster.md`](faction-roster.md) — refactor doc: per-faction
  `UnitType` catalog so reinforcement-spawned units thematically match
  the requesting side instead of literal-typing MARINE / MILITIA in
  three different call sites.

## Related

- [`../convoy/`](../convoy/) — convoy (`ConvoyMeans`) dispatches
  HEAVY_APC. V1 polish + APC + wall constraints landed.
- All three means providers are landed: convoy → shuttle → walk-in
  (priority order). Walk-in is the always-feasible floor.
