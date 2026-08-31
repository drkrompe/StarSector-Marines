# Direction: a vehicle is a unit the player can order

Status: ACTIVE — step 1 landed, steps 2 and 3 open. Read `convoy-nouns.md` first.

Written: 2026-08-31

Updated: 2026-08-31 — the control layer now takes a route and a `VehicleLeg`;
the structural blocker below is resolved.

The destination: the player selects a vehicle on the battlefield, right-clicks
a cell, and the vehicle does its best to get there. Not a delivery being
watched — a unit being commanded, alongside the mech and infantry-squad move
orders that already exist.

## What this changes about convoy

`convoy-nouns.md` currently draws its boundary as "the ground counterpart to
shuttle delivery, not a generic ground-unit system." That boundary is what this
direction retires. Delivery stops being what a vehicle *is* and becomes one of
the errands it can be given. The chassis, its body, its motion, and its
recovery are the enduring nouns; the inbound/land/deboard/depart script becomes
one caller among several.

Nothing about the reinforcement layer changes. A dispatched convoy is still a
request the reinforcement layer decided to fulfil, and convoy still never mints
itself a delivery opportunity.

## What already exists

Three quarters of this is built, which is why it is worth writing down now
rather than treating as a large feature.

- **Selection.** `WorldPicker` already picks the nearest visible vehicle and
  `Selection.selectVehicle` already holds it; `BattleRenderer` already draws the
  selection. A player can click an APC today — the click simply has no
  consequence.
- **The order pattern**, established twice: a `…MoveOrderService` mailbox
  holding pending requests and active orders, a `…MoveOrderSystem` that
  validates at the serialized command boundary and intercepts execution, and a
  highlight publisher. `MechMoveOrderService` is the closer of the two models,
  because it commands one exact body rather than a formation.
- **The motion stack is already goal-agnostic.** `ReferenceCorridor`,
  `LocalTrajectoryPlanner`, `PurePursuit`, the Reeds-Shepp maneuver, and the
  recovery ladder none of them know what an LZ is. They follow a corridor.

## The one structural blocker: a route is a script, not a thing

*(Resolved by step 1 below; kept because it is why the rest is shaped as it is.)*

`VehicleMission` describes a delivery errand — an inbound polyline, a drop
point, an outbound polyline — and `GroundSystem` is the five-state script that
walks it. Each driving state hard-codes *which of exactly two corridors* the
controller should follow, and that choice reaches the controller as an
`isInbound` boolean threaded through the whole of `VehicleControlSystem`: it
selects the route arrays, it picks the arrival tolerance, it gates whether the
docking maneuver may engage, it decides whether arrival snaps to the drop point.

A player move order is a third corridor, and there is nowhere to put it.

**The corridor a vehicle is following should be something the vehicle has, not
a branch of the errand it was dispatched on.** Once `VehicleControlSystem`
takes a corridor plus an arrival policy instead of a direction flag, a move
order is simply another corridor, and delivery is the caller that happens to
supply two of them in sequence. This is mostly deletion, and the existing
controller tests hold it in place while it happens.

## The inversion that decides whether this is any good

Feasibility currently lives at **dispatch time**. `ConvoyMeans` proves the whole
route before committing to it — both polylines, the ordinary bends, and the turn
from the arrival heading onto the exit heading. That is possible only because a
planner chooses both endpoints, and it is the right design for a dispatched
delivery.

A right-click cannot be vetted that way. The player names a destination and the
vehicle must try. So feasibility inverts: it moves out of the dispatch gate and
into runtime, and **the recovery ladder stops being a safety net and becomes the
primary mechanism.**

That matters because of what the ladder's terminal state currently is. Measured
across eight conquest seeds, twelve of sixty-three vehicles ended a battle held
permanently — stationary, footprint-legal, recovery attempts spent, holding
position for the rest of the battle without saying so. For a dispatched convoy
that is a defect found with a probe. For a vehicle the player is looking at, it
is the entire experience of the feature.

Two consequences, and they are the actual design work:

- **An order must be able to fail out loud.** `MechMoveOrderSystem` already has
  the shape: resolve the click to the nearest reachable cell, refuse the request
  outright when there is none, and keep the requested cell separate from the
  destination cell so the interface can say "going here, not exactly where you
  clicked." A vehicle needs that, plus a genuine give-up that releases the order
  rather than parking on it. `slice-3-recovery-ladder.md` has carried "decide
  the terminal no-route policy" as its last open question; player command is
  what forces the answer, and the answer is visible refusal.
- **Turn feasibility becomes an interface affordance.** `canReverseDirectionAt`
  answers whether a chassis can get from one heading to another in the room
  available. That is the question that makes a destination reachable *for a
  vehicle* as opposed to for infantry, and it is why a vehicle cannot reuse the
  infantry reachability answer. A tank that cannot turn round in an alley is
  good play when the player can see why; it is a bug when the truck silently
  stops.

## Prerequisite that is not movement

Every vehicle on the field today belongs to the defender: `ConvoyMeans` is a
defender reinforcement means. "Select the APC and move it" therefore needs a
marine-side vehicle to exist at all, which is `vehicle-variants.md`'s concern
rather than this one. The movement work can land first and be exercised against
defender vehicles in a harness; the feature is not playable until a player owns
a chassis.

## Sequence

1. ~~**Dissolve `isInbound`** into a corridor plus an arrival policy on
   `VehicleControlSystem`.~~ **Landed.** `VehicleLeg` carries the arrival
   semantics and the route is a parameter; behavioral equivalence was checked
   by replaying conquest seeds against the immediately preceding commit.
2. **Vehicle move orders** against the `MechMoveOrderService` template, with
   hard refusal and a reachability answer that accounts for turning room.
3. **A marine-side vehicle** to command.

Ordering matters: (2) is small once (1) is done and awkward before it, and (3)
is independent of both.

## Boundaries this direction does not cross

- Command authority still follows the existing priority model; a player order
  temporarily owns a vehicle's locomotion and nothing else — not its turret,
  not its payload, not its delivery obligation.
- A commanded vehicle is still a body under its own kinematics. It never solves
  a failed order by crossing a wall or snapping through a corner, and an order
  it cannot satisfy is refused rather than approximated.
- Formation movement for several vehicles is out of scope here and belongs with
  `multi-truck-convoys.md`.
