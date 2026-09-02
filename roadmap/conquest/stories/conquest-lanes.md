# Conquest lanes: resistance in depth along the tracks

Status: PLANNED — design settled with the owner on 2026-09-01; implementation
not started.

Written: 2026-09-01

## The problem, seen

The annotated review frame of both 560x336 Conquest fixtures shows the same
shape: one walled garrison at the far end of the axis with the keep, its
barracks, its armouries and an airbase inside the wall; one settlement base
out in the town; and between the beachhead and the wall, open city. The
marine commander lays three lateral tracks across the axis and advances up
all three, and all three arrive at one wall with nothing to take on the way.

Conquest is meant to be a grind through layers of objectives — the marines
working up their tracks and converging on the fortress — and the map does not
supply the layers. The reinforcement layer already reasons in front bands
(`FrontDepth`, four bands from the objective's claim outward) and the
commanders already reason in tracks (`ConquestTrackLayout`, three lateral
thirds of the axis); nothing on the map is placed against either. Every
compound the capture rule and the victory condition count sits inside band 0.

## The model

**A lane is the map's side of a track.** One per track, a ribbon along the
traversal axis from the attacker region to the objective's claim. It is the
third thing a Conquest states after where its objective goes and how far out
its force lands: `Lanes`, with a count defaulting to
`ConquestTrackLayout.DEFAULT_TRACK_COUNT` so the map and the commanders
agree by construction. Lanes are Conquest vocabulary; Assault and Raid state
none and generate exactly as they do today.

**Resistance is a ladder of places along each lane.** Each lane carries one
programmed place per front band between the beachhead and the objective —
band 3 at the outer ring, band 1 abutting the objective's claim — and every
one of them is a garrison precinct in the full sense the noun doc gives the
objective: it claims programmed-first with the objective, its boundary and
guns come from its own `Fortification`, its rooms become tactical nodes, its
stores become points of interest, and it registers as a compound so the same
capture rule takes it and the same victory law counts it. The grind is the
compound ladder: Conquest still requires every compound to flip, and there
are now compounds in every band.

**Two programs beside `garrison()`.** An *outpost* is a guard post or two, a
barrack block and a store, no keep, no airfield: something a squad holds and a
company clears. A *strongpoint* is a gatehouse, guard posts, two barrack
blocks, an armoury and a control room: something a track has to stop for.
Neither packs a keep, which is the one-keep law unchanged; neither owes an
airfield. `FortressProgram.fittedTo` trims them to the ground they are given
the way it trims the garrison.

**The ladder is derived from the same two facts the objective's fortification
is, and stated the same way when a mission wants to say it.** The objective
resolves its rung from the world's rating under the tier's cap
(`Fortification.Demand`). A lane's ladder steps down from that rung outward:
band 1 a strongpoint one rung below the objective, band 2 an outpost two rungs
below, band 3 an outpost at `PICKET`, never below `PICKET`. A mission may
state a ladder per lane — a lane left at pickets is a feint, a lane of
strongpoints is the grind — and an unstated lane derives. That is the
"defence score": the existing named rungs, one per band, per lane.

**Placement follows the tracks and the bands, before growth.** Lane places are
seeded after the objective and before the settlement, so the town grows
around them — a strongpoint stands in the streets, an outpost in the fields.
Lane *k*'s centreline is the lateral centre of track *k* on the map; the band
positions are fractions of the way from the attacker placement's centre to
the objective placement's centre (about 0.3, 0.55 and 0.8), with lateral
jitter inside the track's own span and the plan's ordinary margin and
separation. A place that finds no room is dropped and recorded, on the same
law as the unbuilt program and the unplaced defences: what could not be
placed is evidence, never a crash.

**Roads make the ribbon.** Interconnect already joins every precinct to the
network and an artery aims at the objective's side, so a lane reads as a
route through its places rather than as three islands. No new road machinery
is owed; if the measured map shows a lane place standing off the network, that
is a finding for `PrecinctInterconnect`, not a new stage.

## What it does not do

- It does not change the marine or defender commander. Tracks, capture
  allocation and the front push are unchanged; they simply find compounds in
  every band.
- It does not scale the lift or move the standoff. Those are
  `conquest-560-contact.md`'s decisions and stay there.
- It does not touch the stock crossroad recipe. A headless fixture with no
  market still takes it.

## Acceptance

- `ConquestOnPrecinctsTest` asserts, on both canonical fixtures, that every
  front band 1 to 3 holds at least one compound in at least two lanes, that
  no lane place carries a keep, and that every compound is reachable from the
  marine spawn (the existing law).
- Unit tests on the ladder derivation (rungs step down and floor at
  `PICKET`; a stated lane wins over the derived one) and on lane seeding (a
  seed lands inside its track's span and inside its band's fraction; a map
  too small drops places and records them).
- The annotated review frame of each fixture shows outposts and
  strongpoints along all three tracks, boxed and labelled like every other
  compound.
- The Conquest matrix, measured at full length against the `CLOSE` row in
  `precincts.md`: first contested tick, captures per band over time, held at
  the end, losses. The expected reading is first contact several thousand
  ticks earlier on both fixtures and captures spread across the battle rather
  than clustered at the end; whether `full-strength-west` captures anything
  at all is the number this story is judged on.

## Plan

1. Programs and ladder: `FortressProgram.outpost()` and `strongpoint()`;
   `LaneResistance` (a rung per band) with `derive(objectiveRung)` and a
   stated form; unit tests.
2. Plan: `PrecinctPlan.Lanes` carried on the plan; lane seeding in `derive`
   between objective and settlement; `BspKeys.UNPLACED_LANE_PLACES`;
   `conquestPlanFor` states the default; the mission vocabulary wiring on the
   sprawl and standoff precedent (nullable on `Mission`, optional fixture root
   `"lanes"`, debug stepper).
3. Evidence and fold: annotated frames, the matrix table, and the standing
   model into `precincts.md` (a new section beside the standoff's) and
   `conquest-nouns.md` ("The map a mission requires", "Territory and
   compounds"); `conquest-command.md` gains one sentence saying the tracks
   now have places on them.
