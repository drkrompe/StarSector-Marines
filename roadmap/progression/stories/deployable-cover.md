# Deployable cover — a barricade a marine can put down

Status: PLANNED — the second deployable; reuses the shipped deployable
foundation and needs a mapgen amendment it must not make on its own.

Written: 2026-08-28

Read `progression-nouns.md` (the deployables and placed-emplacements section),
`mapgen-nouns.md`, `ballistics-nouns.md`, and `combat-durability-nouns.md`
before implementing this story.

## Problem

The deployable category shipped with exactly one member, the Palisade
interceptor pod, and one member is not a category. The obvious second is the one
a player reaches for constantly and cannot have: something to get behind. A
squad crossing an open plaza under fire has no way to make the ground it is
standing on any better than it was.

Cover is also the deployable that most tests whether the category generalizes.
The pod is an actor: it stands on a cell, takes damage, dies. A barricade is a
*property of a boundary* — cover is stored per facing, and it is the map's, not
an entity's. If the foundation only supports the actor shape, that is worth
knowing.

## Goal

A carried barricade that, once set down, grants directional cover across one
cell boundary, is destructible, and expires.

## Standing rules it inherits

The bounds law from `progression-nouns.md` applies unchanged: finite lifetime,
destructible, visible, and no permanent alteration of the map. A cover
deployable additionally must not make its cell safer from every direction —
cover is directional in the shipped model, and a barricade that protects the
whole cell is a durability increase wearing a costume.

## The unresolved question this story must answer first

`mapgen-nouns.md` states plainly that **runtime construction remains excluded**:
destruction may open an authored edge, but construction that closes an edge
under existing paths is a separate problem, deliberately unsolved. A barricade
is exactly that problem.

There are two candidate shapes, and picking one is the first task:

1. **A walkable feature, not a closed edge.** The barricade occupies its cell as
   a physical obstruction that intercepts rays crossing it, in the same way a
   walkable doodad already does, without ever closing a navigation edge. Nothing
   in mapgen has to change. The cost is that cover then belongs to the cell's
   contents rather than to the boundary, which is not where the shipped cover
   model puts it.
2. **A real runtime shared-edge barrier.** The barricade authors an actual
   directional cover profile on one edge, with its own small structure pool, and
   removes it on expiry or destruction. This is the honest model and matches
   where cover already lives — and it requires lifting the runtime-construction
   exclusion, with the path/zone/vantage invalidation that implies. That is a
   mapgen-owned amendment with its own acceptance, not something this story may
   smuggle in.

Do not start the carried item until that choice is made and, if it is (2),
until mapgen has agreed the amendment.

## Scope

- One carried special using the shipped `utility-deployable` activation, with
  its own AI policy for when a squad wants cover.
- The placement, lifetime, and destruction lifecycle, reusing the shipped
  deployable foundation.
- Full player-template and faction-source treatment.

## Out of scope

- Any change to the point-defence pod.
- Player-directed placement UI.
- Fortification of authored structures, sandbag lines as map generation, or
  anything a defender starts the battle already behind.

## Acceptance

- A marine behind a placed barricade takes measurably less fire from the
  covered facing and no less from an uncovered one.
- The barricade is destructible and expires; neither leaves the map altered.
- Paths and zones that existed before the placement still resolve afterwards.
- Defenders use it identically, through the same data.
