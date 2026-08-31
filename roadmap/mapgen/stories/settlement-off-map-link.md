# Settlement off-map link

Status: WIRED BUT NOT SWITCHED ON — the campaign derives the link and the
density, and nothing consumes them yet. Adopting grown maps waits on terrain
coherence and the spaceport apron, both measured below.

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

## Nothing has been switched over yet

Conquest keeps `TrunkSkeletonStage`'s fixed crossroad, and there is deliberately
no grown conquest recipe to reach by accident. The reason is ordering rather
than doubt: Conquest is the mission the campaign is built around and its balance
was measured against the maps it has now, so changing the ground under it while
judging whether the new ground is better would leave neither question
answerable.

The rest of generation is on stock too, for now. The switch is one line in
`BspCityGenerator.recipeFor`, and two things are in the way of pulling it:

**A spaceport world does not reliably get a port.** The district contract wants
one large related apron. Over five seeds it appeared on one of five grown maps
at the density a size-5 market asks for, and four of five at 0.55. The stock
partition manages one of five, so this is a weakness the grown path exposes
rather than one it introduces — but `SpaceportDistrictGenerationTest` passes on
stock at its chosen seed and fails on grown, and that is a regression for the
scenario it pins whatever the general rate is.

**Wild terrain reads as static in the real art.** In debug colours the
per-cell ground pick looked like plausible rough ground. Rendered through the
sprite path it is visual noise across whole regions, and it is the first thing
the eye lands on. See `grown-road-graph.md` for why it is uncorrelated and what
fixing it needs.
