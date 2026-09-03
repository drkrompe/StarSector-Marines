# Lanes fan out from the beachhead and close on the keep

Status: MEASURED AND REVERTED — the fan was built, unit-tested and measured
both ways at full length, and it loses held ground on both canonical fixtures.
The code is out of the tree; the reading is below, so the next attempt starts
from it.

Written: 2026-09-02

Updated: 2026-09-02 — built and measured. Acceptance bullets 1 and 2 hold; the
third does not, and it fails in the direction the story said to stop at. Two
generator defects the fan uncovered are real and outlive it.

## What the chain measured, and what the frame shows

The chain reading holds one compound fewer than the forward fraction on
`reinforced-south`, and the reason is not in the commander. On that fixture
lanes 1 and 3 are laid up the far west and far east of the map with their first
rung near the south edge, while the marines come ashore in the middle and go
north. Those two fronts sit at rung 0 for all eighteen thousand ticks because
nobody is ever near them. A derived lane today starts from the middle of its
lateral third of the attacker region — three parallel strips, each with its
own start — and the landing place is one, in the centre. Two of the three
ladders therefore begin two hundred cells sideways from the only ground the
force stands on, and a chain that says "take the next link" is pointing at
outposts the force never chose to walk to.

That diagnosis is still correct. The fan is what it does not license.

## The model that was tried

**Every lane begins at the beachhead and ends at the keep.** A derived path's
first waypoint is the landing place and its last is the objective's claim; the
lanes share both ends and differ in the middle, which is the shape the mission
was named for — one base, three routes, one fortress. The lateral spread is a
function of position along the path: nothing at the beachhead, widest in the
middle band, closing again toward the keep, with the meander applied on top of
that envelope rather than instead of it. The rungs keep their forward
fractions, so band 3 places stand a short walk from the LZ in three
directions, band 2 stands widest apart, and band 1 closes on the fortress.

**A stated path is not moved.** A mission that writes its own waypoints gets
them; the fan is the derivation's shape, not a rule over authored paths.

**The tracks follow the fan.** A fence drawn across the axis cannot say which
fan a squad near the beachhead belongs to, so lane membership was taken from a
Voronoi partition of the map between the recorded routes rather than from the
lateral thirds — ground two routes both run over seeding nothing, and a map with
no routes (every mission but Conquest) keeping the thirds.

Two details that were not obvious and had to be measured into the shape:

- **The spread is an offset from the shared axis, not a destination.** Pushing
  each lane at the absolute middle of its own third makes the fan lopsided when
  the beachhead is not centred: one fixture's outer rungs came out 55 and 157
  cells from the LZ.
- **The meander drifts by the same envelope.** A lane that closes on the
  beachhead and then wanders a hundred cells off it has not closed on anything.
  Walking a bounded *offset from the fan line* is what makes that work; walking
  the coordinate and clamping it to the fan plus or minus the drift saturates
  the clamp every rung, which is a ruled line with a random-number generator
  attached.

## What the matrix said

Full length, 18,000 ticks, both canonical fixtures, all timeouts, captures /
held. The `fence` column is which rule decides a squad's lane —
`battle.conquest.laneFence`, a control switch added so the map change and the
commander change could be told apart against one tree.

| fixture | tree | fence | chain | captures | held |
|---|---|---|---|---:|---:|
| reinforced-south | main | thirds | on | 20 | 16 |
| reinforced-south | main | thirds | off | 25 | **17** |
| reinforced-south | fan | routes | on | 24 | 14 |
| reinforced-south | fan | routes | off | 19 | 15 |
| reinforced-south | fan | thirds | on | 20 | 10 |
| reinforced-south | fan | thirds | off | 18 | 12 |
| full-strength-west | main | thirds | on | 13 | **7** |
| full-strength-west | main | thirds | off | 12 | **7** |
| full-strength-west | fan | routes | on | 8 | 3 |
| full-strength-west | fan | routes | off | 10 | 3 |
| full-strength-west | fan | thirds | on | 11 | 4 |
| full-strength-west | fan | thirds | off | 14 | 4 |

**The fan map carries the loss, not the fence.** The isolation rows are the
ones that decide it: the fanned map read by the *old* lateral thirds still
holds 12 and 4 against a baseline of 17 and 7. Nothing the commander does with
the lanes recovers that. Marine losses rise with the fan — 150 to 217 on the
south at thirds-fraction — while defender losses stay flat, which is what a
ladder pulled in toward the axis looks like: the same force meets the same
garrisons in a narrower space and trades worse.

So the third acceptance bullet fails, and it fails the way the story said to
stop at rather than the way it said to ship. The fan is reverted.

The other two bullets held, and are worth keeping:

- Every rung-3 place stands 63 to 130 cells from the middle of the beachhead
  and every rung-1 place 60 to 74 cells from the keep's own link, on both
  fixtures. `simDeterminism` was green on the fan tree.
- The annotated 3,000-tick frames show exactly what was asked for: three routes
  leaving one beachhead and converging on one keep, on both fixtures. The
  geometry was never the problem.

## What it found on the way

**Two silent defects in lane seeding, on `main` today, unrelated to the fan.**
Both should be fixed on their own:

- `LANE_SEED_SEPARATION` (32) never applies. A seated rung is appended to the
  plan's own `taken` list as well as to the ladder's, so the ordinary 60-cell
  `MIN_SEED_SEPARATION` always finds it there first and dominates. The symptom
  is a middle lane one rung short on both canonical fixtures — the middle lane
  is the shortest, so it loses first. Holding the pre-lane seeds separate from
  the lane seeds and testing each at its own separation seats all nine on both.
- The lane seed's jitter window is clamped to the lane's own lateral third. A
  rung whose fitted position leaves that third gets a window of a few cells and
  is dropped for a reason that has nothing to do with the ground. Bounding it
  with the map margin instead is the fix.

**A waypoint's last leg does not point at the objective once the path bends.**
`LanePath.fitted` slides a refused waypoint forward first. With a fan, the
deepest rung's heading is the closing curve, so it slid 71 cells "forward" and
came to rest 40 cells *behind* the fortress on one fixture and 108 cells past
the objective on the other. The deepest waypoint should give way backward
first. This is latent on `main` only because a straight lane's last leg happens
to aim at the keep.

**A route-Voronoi fence must refuse shared ground.** Handing every cell of a
shared trunk road to whichever route was read first gave `full-strength-west`'s
middle lane not one compound of its own. Marking multi-route cells as belonging
to nobody and sweeping them like any other cell is what fixes it.

**Route staging is measurable on `reinforced-south` once the lanes fan.**
`lane-chain-tug-of-war.md` records the staging derivation as 6 squad-pulses of
3,779 there and says not to tune it against that fixture. With the fan and the
chain on it is **370 of 3,959 — nine per cent**. The dormancy is a consequence
of where the lanes are, not a property of the fixture, so that claim is
overturned even though the fan itself is not kept.

The lane chain fronts also moved on the south for the first time (+1/-1 on lane
2, +3/-2 on lane 3), while still ending at rung 0 on all three.

## Where that leaves the chain

`battle.conquest.laneChain` stays off. Hypothesis 1 of
`lane-chain-tug-of-war.md`'s "What remains" — that the map should place a lane
the force will actually use — has now been tried in its most direct form and
measured, and it costs more than the misplacement it fixes. What is *not* ruled
out is the second half of that same hypothesis: that the reading should admit a
lane nobody is walking, which is a change to the commander and leaves the map
alone. That is the cheaper thing to try next.

## Acceptance

- ~~On both canonical fixtures every lane's rung-3 place lies within a stated
  radius of the landing place, and every rung-1 place within a stated radius
  of the objective claim; the meander and separation laws still hold, and
  `simDeterminism` is green.~~ Met.
- ~~The annotated review frame shows three routes leaving one beachhead and
  arriving at one keep.~~ Met.
- The full matrix both ways: with `battle.conquest.laneChain` on, the chain
  holds at least as many compounds as the control on both fixtures. **Not met,
  in both directions** — the fan loses held compounds against the baseline
  whatever the chain is set to. Recorded above; stopped.
