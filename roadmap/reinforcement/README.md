# Reinforcement

The orchestration layer for "adding more to the fight" in the battle
sim. Sits above any single delivery means: many trigger sources
(garrison depletion, objective lost, scripted timer) post a generic
request, and the system picks a feasible **means provider** (walk-in,
convoy, shuttle) to fulfill it.

## Contents

- [`architecture.md`](architecture.md) — system design: request shape,
  means-provider interface, trigger registry, rally-point selection,
  strength scaling, v1 cut, open questions.
- [`faction-roster.md`](faction-roster.md) — refactor doc: per-faction
  `UnitType` catalog so reinforcement-spawned units thematically match
  the requesting side instead of literal-typing MARINE / MILITIA in
  three different call sites.

## Related

- [`../convoy/`](../convoy/) — convoy is the first concrete means
  provider. V1 polish landed; Conquest integration ([`stage2.md`
  item 1](../convoy/stage2.md)) becomes the first consumer of this
  layer.
- Future shuttle / walk-in providers will get their own folders or
  notes as they're built.
