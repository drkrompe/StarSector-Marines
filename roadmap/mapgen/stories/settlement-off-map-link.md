# Settlement off-map link

Status: PLANNED — depends on `grown-road-graph.md`; the invariant below is the
whole of the work.

Written: 2026-08-31

## The law

**Every settlement is reachable from off the map, by road or by landing.** A
settlement is connected to the rest of its world one of two ways:

- a **ground link** — at least one arterial running off the map edge, joining
  the planetary road network; or
- a **landing link** — a landing facility on the map, which is what an off-grid
  outpost has instead. Supplies and relief arrive by ship.

A map with neither is a generation defect rather than a flavour choice, and it
is a defect that hides: `RoadGraphBuilder` promotes a perimeter cell to a convoy
entry node only where a band at least five cells wide reaches the map edge, so a
settlement with no road out has nowhere for ground reinforcement to arrive from
and nothing reports it.

## Why it is open now

`GrownTrunkPlan` reaches the edge only when growth happens to run that way. An
arm that would overhang is painted through to the perimeter — that much is
deliberate, and exists precisely so the band carries an entry node — but nothing
*guarantees* any arm gets that far. At low density the whole settlement can sit
in the middle of the map with no link at all.

## Shape of the work

The link is a property of the settlement, stated rather than hoped for.
Following the density knob's precedent, it belongs on `GrownTrunkPlan.Profile`:
how many arterials must leave the map, where zero means an off-grid outpost that
must instead carry a landing facility.

**A link may bend.** A road out is not a highway ruled from the settlement to
the edge; one right-angle turn on the way reads as terrain the road went around
and costs nothing, since a `TrunkSegment` is already a rectangle and an L is
simply two of them.

**An outpost is not a new kind of place.** `LANDING_ZONE`, `SPACEPORT_PAD`,
`AIRBASE_PAD` and `AIRBASE_COMPOUND` already exist as lots, so an off-grid
settlement is an ordinary one whose link happens to be a pad. The work is to
guarantee the lot is present when the roads are absent, not to invent content
for it.

## Acceptance

Over a seed survey at each density: every generated map has at least one
perimeter road exit or at least one landing facility, and a map declared
off-grid has a landing facility and no perimeter exit. That is a survey, so it
is evidence rather than a `:test` case; the unit test is on whichever function
decides the link.
