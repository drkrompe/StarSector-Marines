# Set-piece blocks

Status: IN PROGRESS

Written: 2026-10-25

Read `compound-programs.md` (authored relationships within a compound) and
`mapgen-nouns.md` (working-yard templates, compound claims, the road
reservation) first. This story adds no generation mechanism: a set piece is one
more authored compound layout, placed by the compound claim that already owns
its ground.

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

**The yard is sized for the ground compounds have, not the ground it would
like.** A 20 by 26 yard was laid out first and fitted no compound on 24 maps.
Every ordinary industrial compound has the road reservation running between
its members, so its largest clear rectangle is one member and the frame
beside it. The yard is therefore 14 by 18: a nine-row yard and an eight-row
hall. That stood on 9 of 12 city maps and 3 of 12 colonies, and only 7 of
those colonies had an industrial compound at all. Requiring the street outside
the gate to hold a hauler's turn costs a few more. A larger yard needs a claim
built for it (`cross-leaf-footprint-planning.md`), not a bigger template.

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

Done: steps 1 and 2 (`freight-yard-look`).

1. The freight yard as an industrial compound layout, with its `LoadingBay`
   mark and a look suite.
2. The loading stands at a freight yard's bay where the map has one.
3. More colonies carry a freight yard: an industrial claim on colonies that
   have none, and a larger yard on a claim built for it.
4. Objective pressure for the loading: its own bore and director.
5. Further set pieces from the same seam: a factory row on a service lane, and
   a market square with stalls for cover.

## Acceptance

- The freight yard in any pose: the gate opens onto a road, the bay is clear,
  and a hauler standing in it can set out and leave the map by the gate.
- A unit test on the layout's rule: in every pose the bay lies on a straight,
  clear drive to the gate.
- Owner look stills at 1920x1080 from the look preset.
