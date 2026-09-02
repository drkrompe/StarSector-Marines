# Approach standoff

Status: IN PROGRESS

Written: 2026-09-01

## Why

Conquest generates as places at `MapScale.CONQUEST` — 560x336, twice the old
map on each axis. `BattleSetup.conquestPlanFor` puts the objective in the far
third of the traversal axis and the attacker in the near third, and
`PrecinctLandingAreaStage` seats the beachhead on the map edge that band sits
against. Measured on that map, nothing changes hands for the first 6840 ticks —
about four battle-minutes — because the marines walk two thirds of a map that
is twice as long as the one the balance was judged on. The battle still
resolves (reinforced-south reaches a marine terminal at tick 16560), so the map
is fine; the walk is the cost.

The map stays large — it will carry much larger things later, ships from the
fleet standing on the field. What moves is where the marines land.

## The model

**Standoff is mission vocabulary, like `Sprawl`.** A mission states how far its
beachhead lies from the objective, measured in cells along the traversal axis
between the attacker region's objective-facing side and the objective
precinct's claim boundary on the attacker-facing side. Three values, an enum in
`battle/world/gen/precinct` beside `MapPlacement`: `CLOSE` (40 cells),
`STANDARD` (80), `FAR` (the stated band unchanged, which is today's behaviour).

The numbers are cells rather than fractions, because a standoff is a walk and a
walk does not scale with the map. 80 is roughly the approach the 280x168 map
had — the attacker band's far side at x≈93 against an objective claim edge near
x≈170 — which is the point: the same walk on a bigger map.

Conquest's default is `STANDARD`. Everything else passes `FAR`, and `FAR`
reproduces today's maps to the cell.

**A standoff is resolved after growth, not at derivation.** The objective's
claim is only known once the places have grown, so `PrecinctPlan` carries the
stated `attackerFrom` and the `standoff`, and one function turns them into the
effective attacker region: the stated band, kept at its own depth and lateral
extent, slid along the axis toward the objective until its objective-facing
side is `standoff` cells short of the claim; never past the map edge it came
from, and never into the claim. A map that cannot afford the standoff slides as
far as it can and records what it achieved.

Both `SpawnAnchorStage` and `PrecinctLandingAreaStage` read the region through
that one function, so the spawn anchor and the beachhead cannot disagree.

**The approach is carried, not inferred.** The landing stage reads its approach
off which map edge the region touches; a region slid inward touches none, and
the nearest-edge fallback would hand an interior band a side approach. The
approach comes from the *stated* band — the edge it sat against — and berths are
scanned inward from the region's side that faces it.

## Scope

- `Standoff` enum and `ApproachRegion` resolution in
  `battle.world.gen.precinct`, with `MapPlacement`-level unit tests.
- `PrecinctPlan` carries a standoff; `SpawnAnchorStage` and
  `PrecinctLandingAreaStage` read the resolved region.
- `BattleSetup.conquestPlanFor` states `STANDARD`; `precinctPlanFor` states
  `FAR`.
- Mission vocabulary on the sprawl precedent: nullable `Mission.standoff`,
  `MissionLaunch`, the fixture root key, the briefing debug stepper.
- Measurement of the Conquest matrix at all three settings.

Out of scope: the shuttle entry bearing and the reinforcement rear edge, which
key on the axis and do not move; any change to `MapScale.CONQUEST`.

## Acceptance

- `FAR` generates the identical map to today, so every existing precinct test
  and checked-in fixture is unchanged under it.
- The marine spawn anchor and every authored landing area lie inside the
  resolved region, and the region's objective-facing side is `STANDARD` cells
  from the objective claim on both canonical Conquest fixtures.
- The band slides to the exact cell for each value, clamps at the edge it came
  from, and carries its approach.
- An absent fixture `standoff` decodes to null, so existing fixture files
  replay unchanged.
- Measured on both canonical fixtures at `CLOSE` / `STANDARD` / `FAR`: wall
  time, the first `compound-state` tick reading `CONTESTED` and the first
  reading `MARINE_HELD`, captures and losses.

## Plan

1. `Standoff`, `ApproachRegion` and its unit tests; the two stages; Conquest
   states `STANDARD`.
2. Mission vocabulary wiring and its tests.
3. Measure the matrix at all three settings and report.
4. Fold into `precincts.md`, ledger, delete.
