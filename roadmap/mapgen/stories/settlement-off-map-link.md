# Settlement off-map link

Status: LIVE — every non-conquest battle grows its settlement from the market.
Conquest keeps the stock crossroad.

Written: 2026-08-31

Updated: 2026-08-31 — scoped from a blanket invariant to a declared
`SettlementLink`, so a ruin is a value rather than an exception.

## Not every map, only a living one

The first draft of this said *every settlement is reachable from off the map*.
That is wrong as a blanket law. An inhabited colony nothing can reach is a
generation defect; a ruin nothing can reach is the point of the ruin, and a law
with an exception carved out for abandoned sites is a law somebody will forget
to except.

So the requirement is a stated property, `SettlementLink`, with three values:

| | meaning | guarantee |
|---|---|---|
| `ROAD` | joined to the planetary road network | at least one arterial runs off the map edge |
| `LANDING` | off-grid; supplied by ship | a landing facility on the map, and no road out |
| `NONE` | abandoned, ruined, never connected | nothing |

Ruins are not yet generated. `NONE` exists now anyway, because the moment they
are, the shape they need is already a value rather than a change to a law.

## What the defect actually was

Growth reaches an edge only when it happens to run that way, and a landing
facility is rare. Measured over 100 seeds per density with nothing declared
(`NONE`), counting maps with no off-map road entry, no landing pad, and neither:

| density | no road | no pad | **neither** |
|---|---|---|---|
| 0.20 | 23/100 | 79/100 | **15/100** |
| 0.55 | 4/100 | 73/100 | 3/100 |
| 1.00 | 0/100 | 70/100 | 0/100 |

Fifteen percent of sparse settlements could not be reached by any means at all.
With `ROAD` the road count is 0 at every density; with `LANDING` the pad count
is 0 at every density and the road count is deliberately unchanged, because an
off-grid outpost is supposed to have no road. Neither case leaves anything
stranded.

At full density the average entry count is identical with and without the
guarantee (9.94), so it acts only where growth did not.

That defect hid completely. `RoadGraphBuilder` promotes a perimeter cell to an
off-map entry node only where a band wide enough to carry a centreline actually
reaches the edge, and `ConvoyMeans.canFulfill` returns false with no log when
there are no perimeter nodes — so ground reinforcement was simply never offered
and nothing said why.

## The road bends

A road laid straight from the middle of a town to the edge of the world reads as
a runway. One right-angle turn on the way reads as terrain the surveyors went
around, and costs nothing, since a segment is already a rectangle and an L is
two of them. The offset is drawn rather than forced, so a zero offset degenerates
to the straight case on its own — some roads really do run straight.

The forced band is `PRIMARY`, because a link to the outside is an arterial and
because a narrower band would leave the road graph with no perimeter node to
promote — the failure this whole story exists to prevent, reintroduced by the
fix for it.

## The pad is promoted, not rolled

`SettlementLandingLinkStage` is in a recipe only when the settlement is
`LANDING`, and sits beside `AirbasePadSeedStage` — after the compound seeds have
taken their parcels, before the claim stage. It scans for a leaf that actually
*publishes* a pad and otherwise promotes the largest ordinary block.

"Actually publishes" is the whole subtlety. A `LANDING_ZONE` below five cells a
side is still striped and marked and contributes nothing to
`MapResult.landingPads`, so a leaf merely carrying the label would have looked
like a link without being one.

## The campaign chooses it

`SettlementZoning` reads market size and the decivilized condition at the same
boundary `SurfaceZoning` reads planet type, and answers two questions: what the
settlement's lifeline is, and how densely it is built.

A market of size 3 or below is an outpost supplied by ship — a mining claim, a
waystation, a survey post, the places nobody paved a road to. Anything larger
grew where people could drive to it. Decivilized is `NONE` at any size.

**No market is an absence of information, not a claim of isolation.** A battle
with nothing behind it reads as `ROAD` and takes the stock crossroad rather than
a grown settlement, because density is derived from market size and there is
nothing to derive it from. That is a real rule and not a carve-out for tests.

## Switched on

Every battle without a traversal axis now grows its settlement from what the
campaign says about the market. Conquest keeps `TrunkSkeletonStage`'s fixed
crossroad, and there is deliberately no grown conquest recipe to reach by
accident: it is the mission the campaign is built around, its balance was
measured against the maps it has, and the ground under it does not move while
that judgement stands.

A profile with no market behind it takes the stock recipe. That is a rule
rather than a carve-out for tests — density is derived from market size, so a
battle with nothing behind it has nothing to derive it from.

The three things that blocked this are all closed:

| | was | now |
|---|---|---|
| wild terrain read as static | one material per cell across three families | one material per surface, eight variants each |
| apron never contiguous | unmeetable by construction — the filler must leave the centerline drivable | measured as the killing ground it is for; 59/60 stock, 53/60 grown |
| port campus starved | pocket hardcoded to one map corner; grown pads 33/60 | pocket placed where a campus fits; grown pads 58/60, stock held at 60/60 |

## The road bends

A road laid straight from the middle of a town to the edge of the world reads as
a runway. One right-angle turn on the way reads as terrain the surveyors went
around, and costs nothing, since a segment is already a rectangle and an L is
two of them. The offset is drawn rather than forced, so a zero offset degenerates
to the straight case on its own — some roads really do run straight.

The forced band is `PRIMARY`, because a link to the outside is an arterial and
because a narrower band would leave the road graph with no perimeter node to
promote — the failure this whole story exists to prevent, reintroduced by the
fix for it.

## The pad is promoted, not rolled

`SettlementLandingLinkStage` is in a recipe only when the settlement is
`LANDING`, and sits beside `AirbasePadSeedStage` — after the compound seeds have
taken their parcels, before the claim stage. It scans for a leaf that actually
*publishes* a pad and otherwise promotes the largest ordinary block.

"Actually publishes" is the whole subtlety. A `LANDING_ZONE` below five cells a
side is still striped and marked and contributes nothing to
`MapResult.landingPads`, so a leaf merely carrying the label would have looked
like a link without being one.

## The campaign chooses it

`SettlementZoning` reads market size and the decivilized condition at the same
boundary `SurfaceZoning` reads planet type, and answers two questions: what the
settlement's lifeline is, and how densely it is built.

A market of size 3 or below is an outpost supplied by ship — a mining claim, a
waystation, a survey post, the places nobody paved a road to. Anything larger
grew where people could drive to it. Decivilized is `NONE` at any size.

**No market is an absence of information, not a claim of isolation.** A battle
with nothing behind it reads as `ROAD` and takes the stock crossroad rather than
a grown settlement, because density is derived from market size and there is
nothing to derive it from. That is a real rule and not a carve-out for tests.

## Nothing has been switched over yet

Conquest keeps `TrunkSkeletonStage`'s fixed crossroad, and there is deliberately
no grown conquest recipe to reach by accident. The reason is ordering rather
than doubt: Conquest is the mission the campaign is built around and its balance
was measured against the maps it has now, so changing the ground under it while
judging whether the new ground is better would leave neither question
answerable.

The rest of generation is on stock too, for now. The switch is one line in
`BspCityGenerator.recipeFor`. Both things that were in the way of pulling it
have since been fixed, so what remains is the ordering argument above rather
than a defect — it is a decision to take deliberately, not a task that is
blocked:

~~**A spaceport world does not reliably get a port on a grown partition.**~~
Fixed: the campus pocket is placed on the district block that actually holds a
connected group of pad-sized leaves rather than in a hardcoded quadrant, and
grown four-berth rates went from 33/60 to 58/60 with stock held at 60/60. See
`mapgen-nouns.md` on placing a facility where it fits.

An earlier version of this section put the stock rate at one in five and called
the weakness pre-existing rather than grown-specific. That measurement was taken
while recipe selection still keyed off market size, so its stock control was
running grown.

~~Wild terrain reads as static in the real art.~~ Fixed: the surface palettes
draw one material each, and `floors.stone` and `floors.sand` have eight real
variants apiece where they previously held one picture in three cells.
