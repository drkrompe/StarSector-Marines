# Lane chains: a Conquest front measured in places held, not ground covered

Status: IN PROGRESS — the model is built, tested and measured, and ships behind
`battle.conquest.laneChain`, **off by default**. What remains is making it pay
on `reinforced-south`.

Written: 2026-09-01

Updated: 2026-09-02 — three shapes of "open the gate" measured and none paid, so
the gate is not the cost; and the map-side hypothesis has now been tried too and
costs more than it fixes (`lane-fan.md`). Still off. See "What remains".

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

## What the path adds

A lane is a stated or derived path, and the map records the walkable route
between consecutive links — see "And what stands between the two" in
`precincts.md`. The commander therefore never
infers a route: it reads one. A lane that zig-zags or goes round a ridge is
the same chain with a longer road between two of its links, and staging along
that road is what makes the bend matter instead of leaving it to navigation.

## Alongside the model

1. **The evidence diagnostics** (track share, assault progress, deferred
   captures, secure-travel episodes) are track-keyed. They become chain-keyed
   in the same change, or the report stops meaning what it says.
2. **The clock.** reinforced-south lost its win to arithmetic — 23 compounds
   instead of 11 in the same 18,000 ticks. `conquest-560-contact.md` owns that
   question; measure this story against it whatever the answer.

## Acceptance

- On both canonical fixtures, every capture on a lane arrives in chain order
  or the trace says why not (a neighbour-support capture is allowed and
  labelled).
- No advance-track stage lies further from the route to the next link than
  the safe stride.
- A lane whose strongpoint is retaken reads as a front moving back one link,
  in the trace and in the report, rather than as a forward fraction that did
  not change.

## What remains

The model shipped; the balance did not. The bar is unchanged and unmet: the
chain must not give back a held compound on either canonical fixture, and on
`reinforced-south` it holds 16 against the fraction's 17. The matrix is in
`conquest-command.md`.

**The capture gate is not the cause, and that is the session's finding.** The
obvious reading of the first measurement was that the gate refuses compounds
behind a standing outpost and that opening it would give them back. Three
shapes of the opening were built and measured at 18,000 ticks against a control
that reproduced the earlier run exactly, and none of them paid:

| opening | south captures / held | west captures / held |
|---|---|---|
| none — the chain as it stands | 20 / 16 | 13 / 7 |
| a lane opens a rung whenever the allocation can pair nobody | 17 / 16 | 8 / 5 |
| the same, never opening the objective, and only for a squad with no front work | 20 / 16 | 9 / 6 |
| the same, and only onto rungs the depth latch has also reached | 20 / 16 | 12 / 7 |

Held never moves off 16 on the south, whatever is offered. Offering the whole
ladder *lowers* captures to 17, because opening by chain position alone hands
the allocation the fortress — a thousand secure squad-pulses went into the keep
out of order on each fixture, and the west's front push fell from a third of
its orders to a twenty-fifth. Bounding that back to rungs the friendly line has
come level with recovers the west exactly to its unopened numbers and makes the
opening inert on the south, because there every uncommitted squad has front
work and the "nobody left over" trigger never fires. The code was reverted; the
result is here so the next attempt does not re-derive it.

What the numbers say once the gate is ruled out: **the chain keeps a higher
share of what it takes and simply takes less** — 16 of 20 against the
fraction's 17 of 25 — and the compounds it never takes are the lane places
themselves.

Three things to look at, in the order they are likely to matter:

1. **`reinforced-south` lays two of its three lanes across the advance rather
   than along it.** Lane 1 runs far west and lane 3 far east, each with its
   rung 0 near the south edge, while the marines come ashore in the middle and
   go north; so those lanes' fronts are places the force never goes near, and
   their fronts sit at rung 0 for the whole battle. The chain's ordering
   assumes a lane is walked from its beachhead end. Either the lane seeding
   should place a lane the force will actually use, or the reading needs to
   admit a lane nobody is walking — and that is a question for the map before
   it is one for the commander. This is the most likely place the held compound
   is hiding.

   **The first half of that has been tried and does not pay.** `lane-fan.md`
   gave every derived lane the same two ends — the landing place and the keep —
   with a spread envelope in between, so all three ladders start where the force
   stands. The geometry came out exactly as intended and the balance came out
   worse: held falls to 14 on the south and 3 on the west, and an isolation run
   against the same tree with the old lateral thirds restored still reads 12 and
   4, so the fanned *map* carries the loss rather than any lane reading over it.
   It was reverted; the matrix and what it turned up are in that story. What
   remains untried is the second half — a reading that admits a lane nobody is
   walking, which changes the commander and leaves the map alone.
2. **The route staging cannot be measured on the south at all.** It is 6
   squad-pulses of 3,779 with the chain on and 4 of 3,607 with it off, because
   `laneStageChoice` is consulted only for a squad `targetChoice` gives no
   defender zone to, and on that fixture belief is rich enough that one is
   always available: 1,348 pulses are born compound garrisons, 1,466 are
   secure-compound orders, 959 are clear-zone orders, and six reach the staging
   derivation. The staging half of the design lives on `full-strength-west`,
   where it is 230 advance-track pulses. Do not tune staging against the south.

   **That is a property of where the lanes are, not of the fixture.** Under
   `lane-fan.md`'s lanes the same fixture reaches the staging derivation on 370
   pulses of 3,959 — nine per cent. Whatever eventually puts the south's lanes
   where the force walks makes staging measurable there too.
3. **"Nothing stays held" was overstated.** Only two distinct compounds on the
   south and two on the west ever changed hands back (5 and 7 losses against 20
   and 13 gains); the fronts return to rung 0 because those particular rung-0
   places flip, not because the map does. It is a genuine tug-of-war over a
   couple of outposts, which is what the story is named for, rather than a
   failure to hold ground.

The acceptance bullets below are met by the reading itself.
