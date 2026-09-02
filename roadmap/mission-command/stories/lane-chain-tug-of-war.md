# Lane chains: a Conquest front measured in places held, not ground covered

Status: PROPOSED — design direction agreed with the owner on 2026-09-01; to be
specified against the lanes measurement below before implementation.

Written: 2026-09-01

## Where the tracks came from, and what changed under them

Conquest's three lateral **tracks** are a geometric fence: thirds across the
traversal axis, a sticky coordination preference, and progress measured as a
forward fraction of the map (`ConquestTrackLayout.assaultProgress`). That was
the right abstraction for a biome band map, where the ground between beach and
fortress was uniform and the only thing to organise was width.

The map is grown from places now, and since `conquest-lanes.md` shipped every
lane carries a ladder of garrison places from the beachhead to the keep — an
outpost or two and a strongpoint per lane, each a compound the capture rule
takes and the victory law counts. Measured at 560x336 against a lanes-off
control on the same tree:

| fixture | lanes | result | first contested | compounds | captures | held |
|---|---|---|---:|---:|---:|---:|
| reinforced-south | none | MARINE at 13680 | 5610 | 11 | 11 | 11 |
| reinforced-south | 3 | timeout | 4410 | 23 | 19 | 18 |
| full-strength-west | none | timeout | 15570 | 12 | 0 | 0 |
| full-strength-west | 3 | timeout | 2820 | 24 | 7 | 6 |

The chain exists and the battle already layers: captures come band by band,
and the fixture that could take nothing takes its lane places. Two things the
track fence still gets wrong on a grown map are what this story is about. The
road between two places is wherever interconnect put it, not a straight line
up a third, so staging "behind the hostile frontier in the corridor of advance"
stages against a line the ground does not follow. And a forward fraction says
nothing about what the marines hold: a track can read 0.8 advanced while its
strongpoint is still the defenders'.

## The model

**A lane's state is ownership along its chain.** A lane is its ordered
compounds from beachhead to keep, band 3 to band 0, joined by the road route
between them. The lane's front is the furthest link the marines hold; the next
uncaptured link is the lane's objective; the last link taken is what the
defenders counterattack. Progress is a chain index, not a coordinate.

**Staging follows the route.** An advance-track order stages on the road path
to the next link, bounded by the same friendly-lead, safe-stride and
no-backtracking laws the front push has now, but measured along the route
rather than along the axis.

**The defender's side is symmetric.** Hold the next link, retake the last one
lost. The reinforcement layer's front-line trigger and counterattack already
key on front bands; a lane's chain index is a finer band, and the recapture
target is the link the marines just took rather than "the band that moved".

**Tracks stay as the fence.** Squads with no chain work, cohesion, and the
neighbour-support law still want a lateral notion of "near my lane", so
`ConquestTrackLayout` survives as a lane's lateral extent. What it stops being
is the measure of progress.

**Convergence is unchanged.** When every lane's chain is held to band 1 the
force converges on the keep exactly as it does today.

## What it does not do

- It does not change the map. The chain is read off the lane places
  `conquest-lanes.md` seeds; a lane with no places (a stated zero, or a small
  map that seated none) is a bare track and behaves as today.
- It does not change the capture rule, the victory law, or the arrival lift.

## Open before specification

1. **How much of the win is already there.** The table above is the track
   commander playing a chained map. Before rewriting it, measure how the
   current front push behaves on the chain: how often an advance-track order
   stages off the route, how often a lane's forward fraction disagrees with
   its chain index, and whether captures on a lane arrive in chain order. If
   they mostly do, this story is about the grown map's bends and about
   legibility, and it is smaller than it looks.
2. **The evidence diagnostics** (track share, assault progress, deferred
   captures, secure-travel episodes) are all track-keyed. They become
   chain-keyed in the same change, or the report stops meaning what it says.
3. **The clock.** reinforced-south lost its win to arithmetic — 23 compounds
   instead of 11 in the same 18,000 ticks. Whether the budget grows with the
   chain is `conquest-560-contact.md`'s question and should be answered there
   first, or every measurement here reads as a timeout.

## Acceptance, to be sharpened

- On both canonical fixtures, every capture on a lane arrives in chain order
  or the trace says why not (a neighbour-support capture is allowed and
  labelled).
- No advance-track stage lies further from the route to the next link than
  the safe stride.
- A lane whose strongpoint is retaken reads as a front moving back one link,
  in the trace and in the report, rather than as a forward fraction that did
  not change.
