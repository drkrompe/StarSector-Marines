# Grown road graph

Status: LIVE — non-conquest battles grow their roads from the campaign.
Conquest keeps the stock crossroad. The `BiomeKind` question is what remains.

Written: 2026-08-30

Updated: 2026-09-01 — the class ladder gained its third rung; the plan's paved
share fell from 47% to 38% of the map and the blocks stopped being islands.

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

## The class ladder reaches a back street

Every arm below the first branch used to be drawn as a width-5 cross-street,
because `TrunkKind` had exactly two values and the stock plan — which lays
exactly two trunks — needed exactly two. Applied to a graph of twenty-five
junctions that meant the plan alone paved 47% of an 80x80 map before BSP frames
or any filler ran, against the stock crossroad's 19%, and the city read as
buildings marooned in a continuous grey plane.

A third rung at width 3 — `Bsp.ROAD_WIDTH_MIN`, the same floor the BSP frames
already use — takes that to 38%. Measured on the plan's own mask over twenty
seeds at 80x80: two rungs 33–54%, three rungs 27–46%. The end-to-end reading on
the full generator over four seeds moves with it — street share 57.8% to 53.2%,
and the share of street cells further than 2 cells from anything that is not
street, which is to say wider than any road the partition may carve, 28.0% to
18.5%. Seed 42 gains buildings rather than merely losing road: interior cells
142 to 462, POIs 19 to 26, doodads 191 to 214.

The distributions overlap per seed, so `GrownTrunkClassLadderTest` states the
mean rather than a per-seed cap, and says why in its own Javadoc.

## What is not right yet

**Hinterland terrain is noise, not landscape.** `HinterlandFillStage` dresses it
now, but the ground pick is one independent `rng` draw per cell against an
80/15/5 grass/dirt/sand weighting, so dirt appears as isolated speckles rather
than as coherent patches. It reads as rough ground at map zoom and as static
close up. Two things are missing and both are real work: a spatially coherent
field to drive the pick, and edge frames — the nature sheet has none, so any
grass/dirt boundary is a hard cell edge whatever chooses it.

**There are no trees.** The whole plant vocabulary is `nature.shrub-1..3` and
`nature.tuft-1..3` on the sliced nature sheet, all `validOn` grass, and there
are no vegetation doodads at all. Countryside without trees has a ceiling on how
much it can read as countryside, and that ceiling is art rather than code.

## Density is one knob

`Profile.of(density)` derives every other parameter; `city()`, `town()` and
`hamlet()` are named points on it. Measured over 24 seeds at 80x80, open-country
share falls monotonically with density:

| density | 0.00 | 0.20 | 0.40 | 0.55 | 0.75 | 1.00 |
|---|---|---|---|---|---|---|
| open share | 0.637 | 0.431 | 0.236 | 0.148 | 0.058 | 0.000 |

Three per-seed inversions in 120 adjacent-step comparisons, all mid-ladder where
between-seed variance is widest.

**Arm length must not scale with density.** That was the whole fault: a sparse
profile with longer arms lays a thin ribbon of frontage across the entire map
instead of making a smaller settlement, so it measured denser than the profile
above it. Fewer junctions at a fixed reach is what a smaller town is.

**Built-cell share is the wrong measure and hid this.** At full density there is
no hinterland, so the non-built remainder is road, and adding junctions lowers
built share — the ladder appeared to reverse at the top for a reason that had
nothing to do with settlement. Open-country share is what density controls.

## The hinterland is dressed, and is deliberately not a block

`HinterlandFillStage` reads `BspKeys.HINTERLAND` and hands each region to
`NatureZoneFiller` as grassland — weighted ground plus plant and rock scatter.
It is included only in the grown recipes; the stock path omits it rather than
running it as a no-op, which is how this pipeline forks.

**A hinterland region is not a `BlockLeaf` and must not become one.** Emitting
these rects into the partition would have been less code and is wrong in kind: a
leaf is a city parcel, so labelling would zone it, the size constraints would
demote it, and a compound claim could try to build on it. Open country is the
absence of a parcel.

It cannot strand anybody, and that follows from the data rather than from a
check: the grassland ground pool is grass, dirt and sand with no water, and its
rock pool is small and medium rocks, all passable. The fill only ever adds
walkable ground.

## Retracted: zoning over the hinterland

An earlier reading of the comparison images said `RES` and `MIX` labels were
landing on empty fields and that zoning would need to consult the hinterland.
That was wrong. Sub-rects and hinterland are disjoint by construction and BSP
partitions only sub-rects, so no leaf can exist on hinterland ground and no
filler runs there. Measured across 144 generated maps, sub-rect and hinterland
cells overlapped zero times. The labels are the debug overlay drawing
`DistrictMap`'s 20x20 grid across the whole map, which is a property of the
preview and not of generation.

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
