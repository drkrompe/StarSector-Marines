# Story — Landed ships at true scale

Status: IN PROGRESS

Written: 2026-10-16

Updated: 2026-10-16 — round 2 on owner review: concrete pads instead of grating, a motion-carried lift run instead of an in-place shrink, and the berths moved to a southern pad field below a ridge, reached only through the berth gate.

Read `air-nouns.md` (**Overhead ships**, the low-hover paragraph), `tender-ship.md`
(**Vulnerability**), `warship-behaviours.md` and `a-small-war-arc.md` (Chapter 5)
before changing this story.

## Problem

Owner, 2026-10-16: a landed ship such as a Venture or a Hound has to be much larger
than it is today, so that it reads at its true size beside infantry; that wants
higher-resolution base art that keeps its detail, and a compound in which those
ships sitting in dock make sense.

Chapter 5 of A Small War puts Greyhook's Venture and two Hound (P) escorts at the
Black Sluice dispatch pier. Today they hang over the pier at overhead flight scale,
with the vanilla fleet sprites, beside a 42 by 33-cell concrete pier. A cruiser that
crews eighty to three hundred people is a little larger than a marine squad's
frontage.

## Scope and ownership

This story owns **landed and docked ships**: how a ship that is down is sized and
drawn, its art, the transition when it lifts, and the ground it sits on. The
ships-of-the-line pass owns airborne warships: `WarshipScale.HULL_PRESENCE`, hull
reach, handling, standoff and duel tuning, and the swap of the salvage rig for a
Venture in `BlackSluiceDuel`. The hull-damage pass owns `WarshipHullDamageRenderer`.
Nothing here changes helm, handling or the damage renderer.

## True scale

The convention is the one the grounded Shepherd established (`shepherd-recovery.md`):
Morrow's 92-pixel vanilla hull is drawn and blocks ground at **24 cells**, chosen
from her crew of 15 to 30, her 100 cargo and her drone bay against one-cell
infantry. That is **0.261 cells per hull pixel**, or **5.8 times** the shared air
density (`AirScale.METERS_PER_PX`, 0.045 cells per pixel). Applied unchanged to the
installed hulls:

| Hull | Vanilla sprite (beam x length px) | True scale (beam x length cells) | Crew / cargo |
| --- | --- | --- | --- |
| Shepherd (Morrow, precedent) | 78 x 92 | 20 x 24 | 15-30 / 100 |
| Hound (P) (`hound_d_pirates`) | 62 x 72 | 16.2 x 18.8 | 10-40 / 75 |
| Venture | 194 x 190 | 50.6 x 49.6 | 80-300 / 600 |

The ladder holds against the game's own crew and cargo figures: a Hound is a little
smaller than a Shepherd, and a Venture is about five Shepherds of deck for ten
times the crew. Fifty marines lie nose to tail along the Venture.

For comparison, airborne hulls are drawn at `HULL_PRESENCE` times the shared
density: 2 on `main` today (Venture 17 cells, Hound 6.5), and 4 in the
ships-of-the-line pass (Venture 34, Hound 13).

## Liftoff: a run that carries the body to flight scale

**Decision.** A landed ship is drawn at **berth presence** (the 5.8 above). When it
lifts it flies a **lift run** (`LandedScale.liftRun`): a leg ahead off the berth
(0.4 landed lengths), a climbing turn of half a landed length's radius (eight cells
at least) toward where it is going, and a leg out along that bearing (0.5 lengths).
Its drawn body eases by smoothstep to the airborne presence **with the share of the
run flown**, and the same progress draws its shadow out and away and fades the
downwash, so the size change is carried by travel and reads as altitude and
distance. The target of the turn is a point the mission names (`lift(x, y)`), the
first waypoint of a climb-out (`climbOut` lifts toward it), or the middle of the
battle. The run is flown before any route or station the mission gave; a hull
given nothing newer holds where its run comes out. A hull held, piloted or blocked
mid-run is through its lift by time (`LIFT_FALLBACK_SECONDS`), so it can never stay
at berth size. The factor is a ratio to whatever `HULL_PRESENCE` is when it flies,
so the ships-of-the-line pass can re-dial flight presence (it is going to 6, which
puts flight within a few percent of berth size) and nothing jumps.

Owner, 2026-10-16, on the first version (a six-second ease where the ship sat):
the shrinking looked weird and wanted motion, the ships moving ahead and cycling
around. The lift run is that motion; flight presence is the ships-of-the-line
pass's dial.

**Rejected.**

- *Ease over a fixed time where the hull stands.* Ships are slow in atmosphere, so
  the shrink happened in place and read as a hull shrinking on its pad.
- *Keep the berth size in flight.* The Hounds lift into a fight with two Wolfs that
  arrive at flight presence; two scales in one dogfight break the ladder between
  frigates.
- *Fly everything at berth presence.* Hull reach, standoff and the duel's tuned
  distances are read at `HULL_PRESENCE` (`WarshipScale.hullReach`); that is the
  ships-of-the-line pass's dial, not a landed-ship one.
- *Swap at lift.* Any instant change is the pop the owner ruled out.

Morrow's low hover remains its own case: she never fights at flight scale, so she
keeps her 24 by 20 body until she has left the map. Folding low hover into the
berth model is a possible later cleanup, not part of this story.

**What scales.** The hull sprite, its mounts and engine nozzles, and its shadow are
drawn about the hull's centre by the body factor. A berthed hull is cold: no engine
plume, drawn on the vehicle layer with the ground objects it sits among; it moves to
the air layer when it lifts. **What does not.** The hull's fitted geometry (hit
polygon, slots, shield) stays at flight presence. Nothing can engage a berthed hull
(infantry fire never reaches a hull, and Chapter 5 holds hull and ground fire until
Voss's order, which is also what lifts the ships), so the body and the hit shape
agree whenever it matters. A mission that lets ships fire on a berthed hull must
first make hit geometry follow the body factor; that is recorded as open.

## Art: an up-rez that keeps the design

The vanilla Venture (194 x 190) and Hound (P) (62 x 72) are far too small to draw at
fifty and nineteen cells. Following the Borer and grounded-Shepherd pipeline, each
gets a faithful high-resolution remaster made by Codex ImageGen from the vanilla
sprite as the edit target and geometry guide (`codex exec -i <guide>`, the
boarding-jack generator's shape), with the prompt retained beside the raw master
under `art-source/ships/`. The packer is mechanical: it aligns the master's alpha
bounds onto the vanilla sprite's, scaled by a whole multiple of the vanilla canvas,
so slot, nozzle and armour-grid fractions read off the vanilla `.ship` stay where
they were. The remaster is the hull's ground-battle art in **both** regimes, so
detail does not change at liftoff either; the fleet-combat sprite is untouched.

## Black Sluice: a southern pad field behind the berth gate

The 42 by 33-cell pier cannot hold a fifty-cell cruiser and two escorts, and the
owner did not want the ships' fight overhead the buildings the marines are
fighting over (2026-10-16): the pads belong on a southern part of the facility,
with inaccessible terrain preventing direct access. The map grows to 300 by 232
and splits in two:

- the **northern band** is the ground fight, unchanged in layout (customs, the
  causeway between the settling beds, the galleries, fuel control and its tanks,
  dispatch on the pier, the service road and its vehicle lock);
- east of dispatch, the walled **berth yard** (warehouses either side of a
  concourse, fuel mains, sealed galleries, pressure vestibules) ends in the
  **berth gate**, a pressure lock through its east wall;
- a broken, knolled **ridge** runs the width of the map below the band and a **rock
  massif** fills the south-west; a retaining wall carries the yard's east wall down
  onto the ridge, so the only way south is the gate and the **berth road** down the
  east side;
- the **pad field** below holds the **Venture cradle** and **two Hound berths**,
  noses west: plain concrete slabs, open to sight and closed to feet, with a painted
  border, a walkable kerb, hazard-marked tie-downs, floor drains, deck hatches and
  engine-wash scorch at the sterns (the owner rejected the first version's blue
  grating), gantry arms from the kerbs to the hulls, a pad shed, a pad-control
  house and fuel points between the pads.

The generator proves the shape: every gantry arm's root is reachable from the
landing, and with the gate's lock shut nothing in the band reaches the pads.

**Objective semantics.** Unchanged except where stated. Customs, fuel control and
dispatch keep their footprints (every coordinate moves north by 104 with the band);
dispatch stays the dispatch house on the pier, and the pads are its southern berths,
not a site. The marked tanks, the split, Berth Four's hold, the reserves, their
named ingresses and Drent's service-road withdrawal are unchanged. One post moves:
the four-strong pier prize crew, which round one stood at the Venture's stern,
now holds the **berth gate**, eight cells inside the yard's east lock; it still
holds after Voss's order, so a company that clears the band walks the yard to it.

**The duel.** The ships fight over the southern ground (`BlackSluice.AIRSPACE_TOP`,
every row below 88). The rig climbs out west over the massif and the Hounds lift
toward the same first waypoint; the Wolfs enter east of the pads at the tuned
seventy-seven cells. The ships-of-the-line pass owns the duel's tuning and is
adding a mission airspace clamp (`HullAirspace`) that Black Sluice will set to
that rectangle.

## Acceptance

- The Venture and both Hounds stand on their pads at true scale from the first
  frame in `createSnapshots -Psnapshot=black-sluice` at 1920x1080 / 100%
  (whole-map overview, pad, stern, Hound and berth-gate close views).
- On Voss's order they lift on runs, moving off the pads and round before they are
  at flight scale (a frame strip across the lift), and the duel is flown over the
  south.
- Every existing `black-sluice` verdict stays green; any change of objective
  semantics is reported explicitly.
- Unit tests pin the body factor (endpoints, monotonicity toward flight scale, no
  jump, the identity) and the lift run (ahead first, the exit leg on the bearing it
  turned to, a curve rather than a pivot, a minimum radius, progress from nought to
  one).
- `createSnapshots -Psnapshot=landed-ships` shows a Venture and both Hounds on the
  production pads and the lift run as a frame every two seconds, asserting that the
  hull travelled more than half its flight length before reaching flight scale.

## State

Built: the berth body, the remastered Venture and Hound (P), round two's concrete
pads, lift run and southern pad field, the duel's staging and climb-out moved
south, and both suites' evidence. Waiting on the ships-of-the-line pass: the
production duel still fits the salvage rig in the cradle until its Venture swap
lands; it takes the map, berths, lift run and `AIRSPACE_TOP` from here, and owns
the Wolf entries, the climb-out tuning at presence 6 and the airspace clamp.

## Plan

1. Story (this doc), cross-linked from the A Small War board.
2. Up-rez the Venture and Hound (P) through Codex ImageGen; pack and ship.
3. Warship berth state and body factor; renderer, shadow and dust follow it;
   ground art override for the remastered hulls.
4. Black Sluice's ships on concrete pads in a southern pad field behind a ridge,
   reached through the berth gate; the lift run; the duel over the south.
5. Evidence and frames; fold into `air-nouns.md` and the arc docs.

## Open

- Hit geometry follows the body factor (only needed once a berthed hull can be
  engaged).
- Folding Morrow's low hover into the berth body model.
- Live acceptance under the game camera.
