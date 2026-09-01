# Fortress-first conquest

Status: IN PROGRESS — the ward is separated from the band that placed it and
the program is asked for; the district itself is next and must be grown.

Written: 2026-09-01

## What this is

A conquest map built outward from the installation being taken, rather than
sliced into percentile bands along a traversal axis.

Today a conquest map is a stock crossroad with a uniform city on it and a
fortress band across one end. The grown junction graph produces a far more
legible settlement — a town with an edge, country beyond it, a road running out
— and the comparison render at 280x168 seed 42 shows the difference plainly. But
grown growth ignores the axis, so the percentile bands cut across a settlement
that is not where they assume, and a third of the map's points of interest
disappear because the growth budget does not scale with map area.

The answer is to stop deriving the map's structure from an axis at all. The
fortress is placed first, the city is grown around it from its own gates, and
the arteries out of those gates are the roads the whole map hangs from.

## What it owes

1. **A larger map.** Conquest at `MapScale.LARGE` is 280x168. A fortress with a
   city around it on more than one side needs about twice that.
2. **A fortress district that is a program, not a band.** Wrapped by its own
   wall with entrances, holding the lots it owes: airfields, barracks, mech
   bays, command centres. **Counts are authored and may be zero or many** —
   that is the point of stating it this way, because mission design wants a
   two-airfield forward base and a no-airfield depot from the same generator.
3. **City districts grown around it**, seeded from the fortress's sides. Three
   sides or four is a knob, because which sides carry city is exactly the sort
   of thing that makes one map different from another.
4. **Main artery roads out of the fortress**, wide enough to drive. Vehicles
   need a route off the installation or the defender's armour is scenery.

## Where the seams already are

Most of this is placement, not new machinery, and the existing model is already
the right shape:

- `FortressBuilding` carries a **count**, so 0..many is the data model that
  already ships. `FortressProgram.garrison()` is one hardcoded list.
- `FortressWardStage` already packs a program into an envelope, cuts its
  roadways, furnishes it, reserves a `WardAirbase` lot through `AirbaseLot`, and
  has the wall drawn around the result. That order — interior first, wall
  second — is the law `compound-programs.md` establishes and it is already
  obeyed.
- `GrownTrunkPlan` grows a junction graph from a seed junction and returns
  hinterland as well as sub-rects.
- `VehicleCorridor` already owns an authored drivable road with a width
  contract, and the ward is already *told* which road it keeps rather than
  working it out.

## The one thing in the way

**The ward derives where it goes.** `FortressWardStage` reads `BspKeys.AXIS`
and the biome map, returns early without them, and computes its rect from the
fortress band. Every other input it takes is supplied from outside — including,
deliberately, the road it keeps. Placement is the exception, and it is the
exception that pins the whole map to an axis.

Splitting that is the first slice and it changes no output: the band becomes one
supplier of a rect among others, and conquest keeps passing the same rect it
computes today.

## The fortress is grown too, and the wall comes last

The trap in this story is to move the fortress around while leaving it the
shape it has: a rectangle, packed inside a rectangular envelope, with a wall
stamped around the rectangle. That would put an organic city around a
rectilinear keep and make the fortress the least interesting thing on the map —
the exact inversion of what a conquest climax should be.

**The district is grown with the same junction-graph growth the city uses, and
the fortification wall is then drawn around what grew.** That is the order
`compound-programs.md` already argues for in its own terms — interior first,
wall second, the wall a consequence rather than a frame — carried one level up
from a building program to a whole district.

This is a smaller change than it sounds, because **the packer is already
shape-agnostic**. `FortressInterior.pack` takes a buildable mask and a
circulation mask, not a rectangle; `Bounds.of` reads its extent off the mask.
The rectangle enters only through `wardRect`. So growing the fortress means
handing the packer the grown district's own masks — its arms as circulation,
what the arms left as buildable — instead of a rect and its cut roadways.

Three things fall out of that order rather than needing to be built:

- **The wall follows the fortress.** Its envelope is the outline of what grew,
  so it is irregular, and its kill-zone buffer is measured from real structure.
- **The gates are where the arteries already are.** A grown arm that runs out
  of the district crosses the wall line exactly once; that crossing is a gate,
  rather than a gate being placed and a road then being found for it.
- **The arteries out are the arms themselves**, continued into the city growth
  beyond, which is what makes them drivable end to end rather than a corridor
  reserved after the fact.

## Slices

| # | Slice | State |
|---|---|---|
| 1 | The ward is told where it goes; band derivation becomes one supplier | done |
| 2 | The program is asked for, and may owe none or many of a facility | done |
| 3 | The district is **grown**, and the packer is handed its masks rather than a rect | next |
| 4 | The wall is drawn around what grew; gates are where the arms cross it | |
| 5 | The city grows outward from those gates, on an authored number of sides | |
| 6 | A map scale sized for a fortress with a city around it | |

## Constraints

**Do not move conquest onto this until it is measured.** `settlement-off-map-link.md`
makes the ordering argument and it still holds: conquest balance was measured
against the maps it has. The comparison path is
`BspCityGenerator.useGrownRoads` on a conquest axis, which is explicit and
unreachable from a campaign profile.

**`BiomeKind` is not decoration.** `CounterattackSystem`,
`FrontLineReinforcementTrigger` and `RecaptureTargetService` read it as
front-line progression ordering. A fortress-first map has to answer "how deep
into the defender's territory is this cell?" some other way — distance from the
fortress is the obvious source, and is the same idea as the junction-depth
successor `grown-road-graph.md` names.

**The growth budget must scale with the map.** `Profile.of` gives a junction
count that is the same at 80x80 and 280x168 while arm length scales with the
short dimension, so the settlement grows about 4x while the map grows 7.4x.
Doubling the map again without addressing this makes it worse, not better.
