# Spaceport campus window

Status: PROPOSED — the last thing blocking grown maps in production.

Written: 2026-08-31

## The defect

`SpaceportDistrictPlanStage` looks for its campus in the intersection of two
rectangles: a hardcoded zoning pocket around `(width/8, 5*height/8)`, and the
one trunk quadrant that anchor falls in. On the stock trunk that window is the
whole bottom-left sub-rect clipped to the pocket, which holds between four and
nine leaves depending on the trunk roll. On a grown skeleton the leaves are
irregular and often fewer, so the largest connected candidate component is
smaller than a port needs.

Measured over 60 seeds with a size-5 spaceport market, four or more berths:

| partition | pads >= 4 | apron >= 180 (bridged) |
|---|---|---|
| stock | 60/60 | 58/60 |
| grown, density 0.52 | 33/60 | 43/60 |

Pad count is exactly `min(4, campus members)`, so the shortfall is entirely
"how many connected leaves were inside the window".

## Why the anchor is arbitrary

`(width/8, 5*height/8)` puts every port in the south-west of every map,
regardless of where the market's water, spawn, or industry is. The trunk-quadrant
test on top of it is nearly redundant — `LeafAdjacency` already treats trunks as
barriers, so a compound could never span one anyway — and its only real effect
is to pin the search to that corner.

## Shape of the work

Search for the best connected component of candidate leaves wherever the zoning
allows one, rather than in a fixed corner. The port should still be one campus
and still sit in port-flavoured zoning; what should go is the assumption that
port zoning is always in the same place.

This changes where ports appear on stock maps, which is why it is its own story
rather than a rider on the apron fix. Stock is at 60/60 today and must stay
there.

## Not the defect

**The apron was never the problem.** A strict flood over apron ground cannot
span a campus by construction: a compound filler must leave the one-cell road
centerline between members drivable, so the tarmac either side is always two
regions. `SpaceportDistrictGenerationTest` now hops a single walkable cell,
which is the difference between a centerline and a real three-cell street, and
measures the killing ground the apron is actually for.

**The demotion order was a hazard rather than a cause.** The stage used to
demote every incidental pad roll before searching, so a failed search left the
map with fewer berths than if the stage had not run. It now compares what the
campus would publish against what the scatter already gives and declines to
replace better with worse. That removes the hazard; it does not move the
aggregate numbers, because the window is what limits them.
