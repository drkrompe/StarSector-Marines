# Settlement off-map link

Status: BOTH GUARANTEES LAND — road and landing are enforced and measured.
What remains is a campaign source that ever asks for anything but ROAD.

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

## What remains

**Nothing selects a link from the campaign yet.** Every generated settlement is
`ROAD` because that is the default. An outpost or a ruin has to be asked for.
Market conditions are the natural source when that matters — the same boundary
`SurfaceZoning` reads planet type at.
