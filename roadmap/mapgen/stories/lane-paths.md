# Lane paths: a lane is a route, and its places stand on it

Status: PLANNED — direction settled with the owner on 2026-09-01; supersedes
the lateral-strip seeding `conquest-lanes.md` shipped with, which becomes one
derivation of a path.

Written: 2026-09-01

## What a ribbon cannot say

A lane today is a strip: a third of the map across the axis, with its ladder of
places seeded at fixed fractions up the middle of the strip. That laid the
layers a Conquest was missing, and it is measured. What it cannot say is
*where the route goes*. A lane that has to zig-zag, or go round a large
obstacle — a lake, a ridge, a claimed town — has no way to be stated, and the
places it seeds up a straight line may leave the marines to find the way
between them by the accident of navigation. The commanders read the map the
same way, as a forward fraction, and a forward fraction cannot describe a bend.

## The model

**A lane is a path.** An ordered list of waypoints from the attacker region to
the objective's claim, stated as fractions of the map so the same brief lays
out at any scale (the shape `MapPlacement` already has), or derived when the
mission says nothing. The ladder's places stand on the waypoints, one rung per
waypoint in path order, so the chain of compounds `conquest-lanes.md` created
*is* the path: band 3 first, band 1 last, the objective beyond.

**Derived paths may meander, and never cross what they cannot cross.** Today's
straight line up the strip is the simplest derivation and stays available. The
default derivation walks the strip band by band with a bounded lateral drift,
so two lanes on the same map are not parallel lines, and it refuses a waypoint
that lands inside another place's claim, inside the objective, or on ground
the map cannot walk — which today means buildings and walls, and will mean
water and rock once `world-surface-palette.md` puts them on the ground. A
waypoint that finds no room moves along the path rather than being dropped;
only a lane with nowhere at all to stand records itself unplaced.

**The route is a road, and it is recorded.** Consecutive places on a lane are
joined along the map: each lane place's artery aims at the next place on its
path rather than at the objective's centroid, and interconnect's flood-walk
weld already bends around whatever stands between. After topology, the route
between consecutive links is read back as the cheapest walkable path along the
road network and recorded on the map — `MapResult.lanes`: per lane, its
compounds in order and the polyline of cells between them. That record is the
seam the commander's chain reads (`lane-chain-tug-of-war.md`); the generator
owes the route, the commander owes what is done along it. A lane whose route
cannot be walked is a generation defect on the same law that already makes
every compound reachable from the marine spawn.

**Stated paths are Conquest vocabulary.** `Lanes` gains per-lane waypoints,
nullable for derived, on the same mission, fixture and debug-stepper path the
count took. A mission author writes the zig-zag; a fixture can pin one for
evidence.

## What it does not do

- It does not touch the marine or defender commander; they still read tracks
  until the chain story lands, and the recorded route is inert until then.
- It does not add terrain. Obstacles are whatever the map has; the derivation
  is written so that water and rock are refused the day they exist.

## Acceptance

- A stated zig-zag lane on a test plan seeds its places at its waypoints in
  order, and the recorded route visits them in that order.
- A stated waypoint inside another place's claim is moved along the path, and
  the move is reported.
- Derived lanes on both canonical fixtures still seat their ladders, still
  satisfy `ConquestOnPrecinctsTest`'s band and keep laws, and every recorded
  route is walkable end to end.
- The annotated review frame draws each lane's route as a polyline through
  its numbered links, so a bend is visible before a battle is played.
- `simDeterminism` green.

## Plan

1. `LanePath` (waypoints as map fractions; derived straight and meandering
   forms; refusal and slide rules) with unit tests; `Lanes` carries an
   optional path per lane.
2. Seeding on the path in `PrecinctPlan.derive`; lane arteries aim at the next
   link; the route read-back stage after topology writing `MapResult.lanes`
   and `BspKeys.LANES`; the annotation source draws it.
3. Mission vocabulary for stated paths; evidence; fold into `precincts.md`
   ("And what stands between the two") and `mapgen-nouns.md`.
