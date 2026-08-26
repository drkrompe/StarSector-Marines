# Compartment elevation

Status: PROPOSED

Written: 2026-08-26

Read `ship-interiors-nouns.md` before implementing this story, especially the
Elevation section and laws 1 and 9. Depends on `ship-deck-family.md`; it should
land before `facility-room-themes.md` fills a mech bay, because a bay designed
flat and raised later is a different room.

Give a compartment more than one level: a catwalk gallery ringing an open gantry
well, a raised control platform overlooking a bay floor. This is the answer to
two problems at once — the largest compartments read as unarticulated floor, and
nothing in a mech bay establishes that the machine in it is twelve metres tall.

## Why this is bounded work

The cheap version is the one worth building, and the constraint that makes it
cheap is **non-overlap**. A cell belongs to exactly one level. A gallery rings an
open well rather than roofing the floor beneath it, so there is still one cell per
map position, one occupancy entry, one fog-of-war bit, and one navigation node.
Per-cell topology keeps its current shape.

What actually changes is narrower than it first appears: a level attribute per
cell, transition cells that are the only places a level changes, a movement rule
that respects them, and a stated cross-level sight rule.

Do not let this become deck stacking. Overlapping levels would require a second
grid, cross-level shadowcasting, and vertical pathfinding, and law 1 keeps deck
stacking out of map topology entirely.

## Scope

- A level attribute on cells, authored at generation and published on the deck
  graph alongside frame, zone, and spine membership.
- Authored transitions — stairs, ladders, lifts — as the only cells where a level
  changes. They are ordinary chokepoints and appear in the graph as such.
- A stated line-of-sight rule for each level pair. A gallery overlooking a well
  can see and shoot down into it; a floor cell's view upward is bounded. The rule
  is declared, not derived from geometry.
- Navigation that treats a level change as passable only through a transition.
- Presentation that makes the level difference legible without relying on the
  existing relief shading, which is presentation-only and carries no tactical
  meaning.

## Constraints

- Levels never overlap. A fill or stage that produces a roofed cell is a bug, not
  a feature to support.
- Existing macro relief stays what it is: a shading signal that explicitly does
  not change navigation, collision, or targeting. A level is a tactical fact.
  Neither may be implemented in terms of the other.
- Fog of war keeps its per-cell shadowcast model and its ref-counted bitmap. If a
  level rule would require a second visibility pass per cell, re-scope rather
  than widening fog of war.
- Determinism holds: gallery extent and transition placement come from the
  deck's random stream.

## Acceptance

- A generated mech bay has a gallery over part of its footprint and an open well
  over its gantries, and the gallery is reachable only through authored
  transitions.
- Units on the gallery can engage the bay floor per the declared rule, and units
  on the floor cannot reach the gallery except through a transition.
- Fog of war, occupancy, and pathfinding each still see exactly one cell per map
  position; no structure gained a second entry per position.
- A seed sweep produces no roofed cell, no unreachable gallery, and no transition
  that strands a unit.
- Removing every level from a deck yields a valid flat deck, so elevation is an
  enrichment of the family rather than a requirement of it.

## Out of scope

Deck stacking, vertical movement between decks, unit relief and per-unit lighting,
and elevation on city or station maps. If the level model proves out here, a
station gallery is a later and separate question.
