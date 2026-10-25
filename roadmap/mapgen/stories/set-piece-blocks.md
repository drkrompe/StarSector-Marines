# Set-piece blocks

Status: IN PROGRESS

Written: 2026-10-25

Updated: 2026-10-25 — step 3: the freight yard has ground planned for it, and a two-bay yard where that ground holds one.

Read `compound-programs.md` (authored relationships within a compound) and
`mapgen-nouns.md` (working-yard templates, compound claims, the road
reservation) first. A set piece is an authored layout placed on ground that is
already owned: a compound's claim, or a parcel planned for it the way the
industrial template parcels are.

## Scope

The owner (2026-10-25): larger, more interesting blocks of buildings that make
populated maps more cohesive. They give objectives places to plan around, such
as where a vehicle parks, and they shape the fight: buildings with cover along
the lanes, or that partly control the lanes enemies stream down, which can make
a fight harder or easier. **A set piece is worth building for its own sake**,
whether or not an objective uses it. Moving seeds and story pools is not a
cost to be weighed against it.

This replaces the bar `city-structure-injection.md` parked on ("no generic
structures for variety"). Variety in the built city is now a goal.

**A set piece is a block whose relationships are authored**: which building
faces which yard, where the gate opens onto the street, where a vehicle stands,
which way the cover faces. It is not a bigger building. Each one is a rotatable
layout in its own local frame. Its host compound chooses a pose the claimed
ground can hold. A compound that cannot hold one keeps its ordinary fill, so a
set piece never clips or leaves a hole.

## The first set piece: the freight yard

The loading objective found the gap (`staffed-buildings.md`). A truck asked to
stand at an ordinary warehouse door found nowhere it could also leave from on
two colonies in eight, and the stand it found elsewhere was across a kerb. A
freight yard is the classic loading dock:

- a **warehouse**, its loading door facing into the yard;
- a **walled yard** in front of it, with one **vehicle gate** opposite the door
  opening onto a road, wide enough to drive a hauler straight out;
- a striped **loading bay** between the door and the gate, where a truck stands
  nose to the gate, tail to the door;
- **stacked freight along the yard walls** as cover facing the gate, leaving
  the bay and its drive clear;
- a narrow **side gap** for people, which makes the yard defensible from two
  directions rather than one.

The yard publishes its bay as a map mark (`LoadingBay`: the bay's stand,
heading, door and gate). A loading reads the bay rather than searching the
finished map. Where a map has no freight yard, it still searches as it does
today.

**The yard has ground planned for it, not ground it finds.** Placed only in
industrial compounds, it stood on 3 of 12 colonies: only 7 had a compound, and
every ordinary compound keeps the road reservation running between its
members, so its largest clear rectangle is one member and the frame beside it.
A compound claim cannot give it more, because claims keep their streets. So the
yard is a **parcel**, like the industrial template parcels: before compounds
claim and before the streets become a graph, one parcel per map is labelled a
freight yard, joined to its neighbour across their minor street where that is
what holds the larger yard. The street goes with it, the outer ring stays
whole, and compounds, the spaceport district and the landmark seeds leave the
parcel alone.

- **Which ground.** Working parcels first (industrial buildings and yards),
  then ground a settlement can spare (shops, dense blocks, ruins, plazas);
  never homes, civic blocks, compounds or natural ground. The parcel must have
  a street side long enough for the gate wall. Of what fits, the parcel
  **nearest the map's edge** wins: a freight yard stands where the road leaves
  town, and a hauler loaded in the middle of a colony has the whole town to
  cross, which is exactly the drive vehicle routing gives up on.
- **Which size.** Two sizes, tried largest first, so a short parcel is a
  smaller yard rather than a hole: the **two-bay yard** (20 by 26: two gates
  and drives either side of an island of freight, before a bigger hall) and the
  **standard yard** (14 by 18). A parcel whose streets give no gate a hauler
  can turn out of is an ordinary industrial yard.
- **The drive out.** A hauler leaving a walled yard drives clear of the gate
  before it turns; the cheapest path bends at the gate's mouth, hugging the
  wall, and no hauler can follow it. Each bay therefore publishes its **turn**,
  the point four cells out where the yard proved a hauler can turn either way,
  and a loading drives straight out to it before routing off the map. A gate
  facing the map's edge needs no road at all. A loading is sent only to a bay
  it has proved a truck can leave; the yard still stands where none is.

Measured over 24 Colony Breach colonies: a yard on 20, half of them with more
than one bay, and the loading's truck standing in a yard's bay on 14. Where no
bay can be driven out of, the loading parks by search at another warehouse;
one colony found no stand there either and offers no loading. Over 12 stock
city maps, a yard on 11. The industrial compound still lays a standard yard on
its own ground where that ground holds one, so a map may have two.

## Lanes and pressure

A set piece shapes the lanes near it with its walls, gates and gaps. Which lanes
an attack can come down is the lever that makes a fight harder or easier: one
gate or two, a straight run at the bay or a dog-leg past cover.

An objective may bring its own **pressure** rather than make the mission's enemy
commander aware of it:

- a bore of its own, opened through the ordinary bore seam (Cordon's
  `BoreBeat` is the precedent);
- a temporary director that claims that bore's broods with
  `CommandAuthority.SCRIPTED` and sends them down the set piece's approach
  lanes, paced to the objective's beats;
- for a loading: probes while it starts, a wave at half the count, and a rush
  along the road out once the truck sets off.

When the objective ends, the main commander inherits the survivors. The swarm
spawn pass owns how bodies leave a bore; pressure only decides where its bore
opens and where its broods are sent.

## Plan

Done: steps 1 to 3 (`freight-yard-look`; coverage in `generatedSpaceEvidence`,
`FreightYardCoverageEvidenceTest`).

1. The freight yard as an industrial compound layout, with its `LoadingBay`
   mark and a look suite.
2. The loading stands at a freight yard's bay where the map has one.
3. More colonies carry a freight yard: a parcel planned for it, and the
   two-bay yard where that parcel holds one.
4. Objective pressure for the loading: its own bore and director.
5. Further set pieces from the same seam: a factory row on a service lane, and
   a market square with stalls for cover.

## Acceptance

- The freight yard in any pose: the gate opens onto a road, the bay is clear,
  and a hauler standing in it can set out and leave the map by the gate.
- A unit test on the layout's rule: in every pose the bay lies on a straight,
  clear drive to the gate.
- Owner look stills at 1920x1080 from the look preset.
