# Spaceport campus window

Status: SHIPPED — the pocket is placed where a campus fits. Grown pads went
from 33/60 to 58/60 with stock held at 60/60.

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

## What changed

The pocket is placed on the district block that holds the largest *connected*
group of pad-sized leaves, scored with the same adjacency the campus is later
built from. Counting leaves alone would happily choose a block whose leaves sit
either side of a trunk and cannot form one facility.

`SpaceportDistrictPlanStage` now reads the pocket out of the district map
instead of re-deriving the constant, so there is one answer to where the port is
rather than two that agreed by coincidence. Its trunk-quadrant test went with
the constant: `LeafAdjacency` already treats a trunk as a barrier, so a campus
could never span one, and the test's only remaining effect was to pin the search
to a corner.

| partition | pads >= 4 before | after | apron before | after |
|---|---|---|---|---|
| stock | 60/60 | 60/60 | 58/60 | 59/60 |
| grown, density 0.52 | 33/60 | 58/60 | 43/60 | 53/60 |

**A scorer that models a decision must predict the outcome, not the request.**
`DistrictMap.forceThemeAt` silently declines to overwrite a WATERFRONT district,
so the first version — which scored every leaf in a block — chose blocks whose
coast cells never became port zoning, and stock fell from 60/60 to 57/60. The
score now skips leaves whose district cannot be reserved. That is the same
two-places-encoding-one-decision fault this story exists to remove, reintroduced
inside the fix for it.

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
