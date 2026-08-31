# Grown road graph

Status: SPIKE LANDED — opt-in and unwired; the density knob and the zoning
boundary below are what remain before any recipe adopts it.

Written: 2026-08-30

## What this is

An alternative to `TrunkSkeletonStage`'s single fixed crossroad: the city's road
skeleton grown as a recursive junction graph, reached through
`BspCityGenerator.useGrownRoads(GrownTrunkPlan.Profile)`. Nothing selects it on
its own, and the stock stage is untouched.

The unit of growth is a **junction** of degree three or four, not a tile. Each
arm draws its own length and class, so the output repeats at no constant pitch —
what stays fixed is only that junctions have three or four ways out, which is
true of real ones. That is the answer to the obvious objection to any
seed-and-stamp scheme: stamped tiles are visible because the *cell* is fixed,
and here the only fixed thing is a junction's degree.

Output is a drop-in `TrunkPlan.Plan`, so `BspPartitionStage` and every other
`TRUNK_PLAN` consumer works unchanged. BSP still owns everything below the
arterial; the graph decides where the blocks are, not what fills them.

## Why it also answers nature

**Density is a growth budget, not a map family.** A budget that cannot fill the
map leaves ground no road reached, and that ground is published as
`BspKeys.HINTERLAND` and painted `GRASS` rather than handed to BSP. One
generator therefore produces a city, a town, and a road through open country
without a second recipe and without a hardcoded biome band.

What separates built from open is **distance from a grown band**
(`Profile.frontageDepth`), not region size. Sorting maximal rectangles by area
was tried first and does not produce settlement: on a sparsely grown map the
largest free rectangle is half the map, so it came out as a built half and a
field half rather than as buildings along the roads.

The perimeter ring is excluded from that measurement. It is a map-edge
reservation rather than a street anyone builds along, and measuring from it puts
every cell on the map near a road — the hinterland collapsed to a sliver in the
middle distance.

## Measured, at 80x80 over seeds 1 / 42 / 100 / 777

Evidence is `GrownRoadComparisonTest` → `build/map-previews/grown-comparison.png`
(stock, `city`, `town`, `hamlet` per seed). `:test --tests '*world.gen*'` is
green at 492 tests.

The grown city reads as more varied than stock — irregular block sizes,
staggered junctions, streets that do not run edge to edge — and carries more
content (seed 777: 175 doodads against 174; seed 42: 172 against 151). `town`
and `hamlet` produce genuine settlement with open country around it.

## What is not right yet

**The density ladder is not monotonic across seeds.** At seed 777 the doodad
counts fall 174 / 175 / 102 / 69 as intended; at seed 42 they run
151 / 172 / 39 / 90 and `hamlet` comes out *denser* than `town`. Two knobs
interact — budget and arm length set how far growth spreads, `frontageDepth`
sets how wide the built ribbon is — so a clustered `town` leaves large fields
while a spread-out `hamlet` puts thin frontage everywhere. These want collapsing
into one density control with the others derived.

**Zoning does not know the hinterland exists.** `DistrictMap` is a blind 20x20
grid, so `RES` and `MIX` labels land on empty fields. Zoning has to consult the
hinterland before a recipe adopts this.

## The `BiomeKind` constraint

`BiomeKind` is not only cosmetic: `CounterattackSystem`,
`FrontLineReinforcementTrigger`, and `RecaptureTargetService` read it as the
front-line progression ordering. Replacing the fixed percentile bands means
something must still answer *"how deep into the defender's territory is this
cell?"* — junction depth is the natural source, since it is derived from where
the settlement actually is rather than from a 15/35/75% split along an axis.
That is a follow-on, not part of this spike.

## Not attempted

Non-rectilinear streets. The renderer's wall autotiling, `WallMasks`, and the
shared-edge barrier model all want axis alignment; varying *spacing* is what
kills the lattice read, and varying *angle* would cost far more than it buys.
