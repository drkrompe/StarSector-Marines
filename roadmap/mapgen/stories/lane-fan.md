# Lanes fan out from the beachhead and close on the keep

Status: PLANNED — the map-side answer to the held compound `lane-chain-tug-of-war.md`
could not win back.

Written: 2026-09-02

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

## The model

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

**The tracks follow the fan.** The lateral thirds remain the fence for a squad
that has no lane assignment, but a fence drawn across the axis cannot say
which fan a squad near the beachhead belongs to, so lane membership near the
shared ends is by nearest route, not by third. The chain assignment already
names a lane per squad; the fence only decides for squads it has not reached.

## What it does not do

- It does not touch the commander's chain logic or the capture gate; those are
  measured and stand as shipped, off by default until the map gives them
  fronts the force actually reaches.
- It does not change the number of lanes, their resistance ladder, or the
  landing place.

## Acceptance

- On both canonical fixtures every lane's rung-3 place lies within a stated
  radius of the landing place, and every rung-1 place within a stated radius
  of the objective claim; the meander and separation laws still hold, and
  `simDeterminism` is green.
- The annotated review frame shows three routes leaving one beachhead and
  arriving at one keep.
- The full matrix both ways: with `battle.conquest.laneChain` on, the chain
  holds at least as many compounds as the control on both fixtures. If it does,
  flip the chain on by default and fold `lane-chain-tug-of-war.md` with it. If
  it does not, record the both-ways table here and stop.

## Plan

1. `LanePath`'s derived form takes the beachhead and objective as shared
   endpoints and a spread envelope; unit tests on the envelope and the
   endpoints directly.
2. `PrecinctPlan.seedLanes` derives from the landing place rather than the
   attacker region; `ConquestTrackLayout` membership by nearest route for
   unassigned squads.
3. Matrix both ways; fold into `precincts.md` "And what stands between the two".
