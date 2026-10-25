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

## The factory row

Workshops side by side along a private **service lane**:

- two or three **workshops** sharing party walls, their **loading doors** onto
  the lane and every other wall blank, so their **backs face the street** and
  shape the street lanes outside;
- the **lane** runs the length of the row, four cells clear, with a **vehicle
  gate at each end** onto the street, so a fight down it can come from either
  way;
- across the lane, against the far wall, a **parked utility vehicle** near each
  end, a generator by each gate and **pipe runs** with drum clusters to the
  middle, leaving **pockets** of cover off the lane and the lane itself clear
  gate to gate. The pockets mirror end to end.

The row publishes its lane as a map mark (`ServiceLane`: the two gate cells, the
centre line between them and the lane's width) for an objective to send
pressure down or hold.

**The row has its own compound kind** rather than sharing the industrial
compound with the freight yard, which keeps that compound where it fits. The
label pass promotes one lot per map to `FACTORY_ROW`: the largest industrial lot
left of at least 11 by 11, or the largest commercial lot where no industrial lot
qualifies. Its claim takes one neighbour. The row is 13 deep and 14, 16, 18 or
20 long (three workshops from 18), so an 11-cell lot and its street frame hold
the shortest. Gates on the reserved road win over a longer row. Both gates need
four open cells outside them, the first a street cell. A compound that cannot
hold the row fills as ordinary industrial lots. Across 24 maps of each, the row
stood on 13 medium cities (18 had a host lot) and 16 colonies (all 24 had one).
Most misses are the road reservation crossing every pose.

## The market square

A commercial set piece: an open paved square framed by shopfronts whose doors
face it, crossed by two rows of market stalls, entered by street mouths and
alleys. Its tactical job is the killing ground: a fight crosses the square
between the stall rows, and **the mouths decide which ways a fight can enter**.

- **Shops down each side**, doors facing the square, with two cells of aisle
  kept clear before every frontage. They are ordinary commercial shells, so
  the staffed grocery crews work them like any shop.
- **The back** is either a row of shops (the square framed on three sides) or
  a second street running through.
- **An alley** may break each side row.
- **Stalls** are composed from existing props, since no stall or awning art
  exists: a produce stall is three servery counters with a crate and a box
  behind; a fish stall is iced fish crates with a counter at its end. Potted
  planters stand at the front corners.
- **A bandstand** stands at the centre of a square deep enough for it, as hard
  cover.

The **mouths** are the layout's variant and the owner's lever: one (the front
street only), two (a street through), three (front and two alleys) or four
(a street through and two alleys). The square publishes an **open square**
(`OpenSquare` on `MapResult`): its centre, its open extent and every mouth's
cells and outward step, for an objective to hold, cross or send pressure
through.

**The square needs ground no compound has.** A 24 by 12 layout fitted the
dense quarter on 2 city maps in 24 and 7 colonies in 24, because the road
reservation runs down every street between members; letting the reservation
cross the open square and leaving out a shop it ran through still left broken
frames on bare paving. The square therefore has a **market lot** planned for
it: after compound claims and before the road graph, two complete commercial,
plaza, dense-block or residential parcels and the minor street between them
are joined into one lot, exactly as the industrial planning pass joins
parcels. The street ring round the lot stays complete, so every mouth gives
onto road. A lot the square cannot stand in is an ordinary plaza. Where the
lot holds every layout, the mouths are picked from the lot's position without
a random draw. With at least four shops standing, the square stands on 19 city
maps in 24 and 20 colonies in 24.

## Lanes and pressure

A set piece shapes the lanes near it with its walls, gates and gaps. Which lanes
an attack can come down is the lever that makes a fight harder or easier: one
gate or two, a straight run at the bay or a dog-leg past cover.

An objective may bring its own **pressure** rather than make the mission's enemy
commander aware of it. The loading's is `BorePressure`:

- **Its own bore**, opened through the ordinary bore seam (Cordon's `BoreBeat`
  is the precedent) about ten seconds after the crew start, on open street 28
  to 45 steps' walk from the yard gate, the reachable cell farthest from the
  yard. The yard publishes the marks it needs: its gate, its side gap and the
  hall door (`LoadingBay`).
- **Held, and surged on the objective's beats.** The bore never surges on its
  own clock and adds nothing to the mission's population target; the
  objective calls its surges (`SwarmReinforcementSystem.surgeScripted`), sized
  in the director's weight, before the tremor as after it. A loading sends a
  probe every forty seconds while the crew start, a wave at half the count and
  a rush once the truck sets off.
- **A director for its broods.** Ravs are not squads, so it does not claim
  them with a command authority. It drives them (`SwarmDrives`): a driven body
  hunts as any body does, but its idle wander becomes a walk down its lane,
  alternately the gate lane (a point out in the street, the gate, the door)
  and the side lane (out past the gap, the gap, the door). Once the truck
  sets off, all of them are sent to a point on its road out.

When the objective ends, the drives are released and the survivors wander as
the rest of the swarm. The swarm spawn pass owns how bodies leave a bore;
pressure only decides where its bore opens, when it surges and where its
broods are sent.

Against a static six-marine guard on three yard colonies (`loading-pressure-look`),
all three trucks got away, the guard fighting for it on two. A first, heavier
pacing lost two yards of three. On one map the colony's own militia collapsed the
fissure before the rush; a fissure is fragile, and whether the pressure bore
should be tougher is open, as is tuning it against real play.

The yard's warehouse has one doorway, onto the yard
(`BuildingPlacement.frontOnly`): its other walls back onto the compound wall.

## Plan

Done: steps 1, 2 and 4 (`freight-yard-look`, `loading-pressure-look`); the
factory row and the market square of step 5 (`factory-row-look`,
`market-square-look`).

1. The freight yard as an industrial compound layout, with its `LoadingBay`
   mark and a look suite.
2. The loading stands at a freight yard's bay where the map has one.
3. More colonies carry a freight yard: an industrial claim on colonies that
   have none, and a larger yard on a claim built for it.
4. Objective pressure for the loading: its own bore and director.
5. Further set pieces from the same seam: a factory row on a service lane, and
   a market square with stalls for cover (done: `MarketSquare` on a planned
   market lot, publishing its `OpenSquare`).

## Acceptance

- The freight yard in any pose: the gate opens onto a road, the bay is clear,
  and a hauler standing in it can set out and leave the map by the gate.
- A unit test on the layout's rule: in every pose the bay lies on a straight,
  clear drive to the gate.
- The factory row in any pose and length: both gates open outward, the lane is
  clear gate to gate, the cover stands off it, and the workshops fill the
  length sharing their party walls (`FactoryRowTest`).
- The market square in every layout: every mouth reaches every cell before
  every shop frontage between the stalls, and no stall stands in an aisle.
- Owner look stills at 1920x1080 from the look preset.
