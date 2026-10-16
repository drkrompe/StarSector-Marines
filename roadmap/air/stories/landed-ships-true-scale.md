# Story — Landed ships at true scale

Status: IN PROGRESS

Written: 2026-10-16

Updated: 2026-10-16 — berth body, eased lift, remastered Venture and Hound (P) art and the Black Sluice docking compound built; the duel still flies the salvage rig until the ships-of-the-line Venture swap lands.

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

## Liftoff: one body, eased between two regimes

**Decision.** A landed ship is drawn at **berth presence** (the 5.8 above). From the
moment it lifts, its drawn body eases continuously to the airborne presence over
the lift (`LIFT_SECONDS`, a few seconds, smoothstep), while the same lift progress
carries its shadow out from under it and its downwash raises dust off the berth. The
size factor is read as a ratio to whatever `HULL_PRESENCE` is when it flies, so the
ships-of-the-line pass can re-dial flight presence and the transition stays
continuous; were flight presence ever to reach berth presence, the ease would be
the identity.

At flight presence 4 the shrink is to 69% over the lift, about a tenth of a percent
per frame at 60 frames a second: below a pop, read together with the shadow
separating and the dust as a ship lifting off its cradle.

**Rejected.**

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

## Black Sluice docking compound

The 42 by 33-cell pier cannot hold a fifty-cell cruiser and two escorts. The map
widens east and the pier becomes the landward apron of a walled **docking compound**:

- a **Venture cradle**: a sunken, see-through, unwalkable berth well a few cells
  larger than the hull, its nose to the east and its climb-out;
- **two Hound berths** flanking it north and south;
- **gantries**: boarding arms out over the well to the hulls' flanks, and gantry
  tower bases on the rim;
- **fuel and cargo lines**: pipe runs from the fuel side to each berth, cargo stacks,
  loaders and pallets on the aprons;
- **service galleries**: sealed walled corridors along the compound, and **pressure
  vestibules** (doorways) in its walls, because Agreus is airless;
- **infantry approaches**: the apron from the dispatch house, aisles between the
  wells, and the galleries as covered routes.

**Invariants kept.** The three objective sites (customs, fuel control, dispatch)
keep their footprints and the dispatch house stays the dispatch objective; the
marked tanks are untouched; the split, Berth Four's hold and the reserves keep
their triggers; Drent's withdrawal keeps its service road. One defender post moves:
the four-strong pier prize crew stands watch at the Venture's stern gantry, beside
the ship it crews, rather than on the open pier; it still holds dispatch after
Voss's order. The road's gate at the
old east edge becomes a **tunnel portal** under the compound wall, so its region,
its distance from the fight and the physical take-off at the gate are unchanged.
Reserves keep their named ingresses (the service-road gate is the portal mouth; the
pier stair is the compound's landward vestibule). The duel's staging moves with the
ships: the rig and Hound posts become berths, and the Wolfs' entry keeps its tuned
distance from the rig.

## Acceptance

- The Venture and both Hounds stand in their berths at true scale from the first
  frame, with infantry beside them, in `createSnapshots -Psnapshot=black-sluice` at
  1920x1080 / 100% (whole-map overview and a dock close view).
- On Voss's order they lift without a pop (a frame strip across the lift) and the
  duel proceeds as before.
- Every existing `black-sluice` verdict stays green; any change of objective
  semantics is reported explicitly.
- A unit test pins the body factor: berth presence, the ease's endpoints and its
  monotonicity, and the identity case.
- `createSnapshots -Psnapshot=landed-ships` shows a Venture and both Hounds in the
  production dock with Greyhook's prize crew at the stern gantry, and the lift as a
  frame every 1.5 s, independent of which hull the duel flies.

## State

Built: the berth body and eased lift, the remastered Venture and Hound (P), the
docking compound, the duel staging in berths, and both suites' evidence (all
`black-sluice` verdicts green). Waiting on the ships-of-the-line pass: the
production duel still fits the salvage rig in the cradle until its Venture swap
lands, and that merge must keep the berth staging (`BlackSluiceDuel.stage` berths
every hull and `withdrawalOrdered` lifts the Hounds) and re-read its tuned Wolf
entry and climb-out against the cradle, which sits about sixty cells east of the old
pier post.

## Plan

1. Story (this doc), cross-linked from the A Small War board.
2. Up-rez the Venture and Hound (P) through Codex ImageGen; pack and ship.
3. Warship berth state and body factor; renderer, shadow and dust follow it;
   ground art override for the remastered hulls.
4. Rebuild the Black Sluice east end as the docking compound; stage the ships in
   their berths.
5. Evidence and frames; fold into `air-nouns.md` and the arc docs.

## Open

- Hit geometry follows the body factor (only needed once a berthed hull can be
  engaged).
- Folding Morrow's low hover into the berth body model.
- Live acceptance under the game camera.
