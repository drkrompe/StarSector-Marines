# Set-piece blocks

Status: IN PROGRESS

Written: 2026-10-25

Updated: 2026-10-25 — step 6: the factory row, market square and main street each host a side objective under its own pressure.

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
bay can be driven out of, the loading parks by search at another warehouse,
and every colony offered one. Over 12 stock city maps, a yard on 11. The industrial compound still lays a standard yard on
its own ground where that ground holds one, so a map may have two.

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
commander aware of it (`ObjectivePressure`). The objective says each tick where
it stands in its **beats**: how far through it is, whether it is in its final
stretch, and in that stretch where everything should go. A **bore pressure**
(`BorePressure`) is three things the set piece gives it:

- **A site rule.** Its own bore, opened through the ordinary bore seam (Cordon's
  `BoreBeat` is the precedent) ten seconds after the objective starts, on open
  street a given walk from a cell of the set piece, the reachable cell of those
  farthest from the set piece's middle. The walk never crosses a building or a
  compound's ground, so a square or a row is walked from out past a way in.
- **Lanes.** Each a walk of waypoints: a point out in the street, the way in,
  the goal inside. Its broods take the lanes in turn.
- **Pacing.** Held, so the bore never surges on its own clock and adds nothing
  to the mission's population target; the pressure calls its surges
  (`SwarmReinforcementSystem.surgeScripted`), sized in the director's weight,
  before the tremor as after it: a probe every so often until the objective is
  half done, a wave at half, a rush at its final stretch.

Ravs are not squads, so the pressure does not claim them with a command
authority. It drives them (`SwarmDrives`): a driven body hunts as any body does,
but its idle wander becomes a walk down its lane. When the objective ends, the
drives are released and the survivors wander as the rest of the swarm. The
swarm spawn pass owns how bodies leave a bore; pressure only decides where its
bore opens, when it surges and where its broods are sent. An objective with
pressure is a `PressuredObjective`: a task (what the objective is about) and
its pressure, ended together.

| Objective | Set piece, mark | Site | Lanes | Beats |
|---|---|---|---|---|
| Secure the loading | freight yard, `LoadingBay` | 28 to 45 steps from the gate | the gate and the side gap, to the door | probe every 40 s (weight 3), wave at half the crates (7), rush when the truck sets off (12), sent to a point on its road out |
| Strip the workshop | factory row, `ServiceLane` | 24 to 40 steps from out past gate A | both gates, to the stack | 18 loads; probe every 30 s (5), wave at half (10), rush at three quarters (16), sent to the stack |
| Hold the muster | market square, `OpenSquare` | 14 to 26 steps from out past a mouth | every mouth, to the centre | 100 s; probe every 25 s (6), wave at half (14), rush for the last 30 s (22), sent to the centre |
| Empty the hall | main street, `MainStreet` | 14 to 26 steps from the spine's middle | every side street or gap (`StreetMouths`), to the spine | a party of two every 12 s from 15 s; the loading's pacing, rush when the last party is out, sent to the spine's middle |

The square and the street stand in the open, whose straight streets give a
guard a long field of fire: a bore opened as far out as the loading's had its
broods shot down before any reached the people, so theirs open nearer, round a
corner. The main street publishes no gaps, so its ways in are read off the
map: a step along the spine where open outdoor ground runs straight out from
the kerb for eight cells is open on that side, and each run of open steps is
one way in, at its middle.

**Paced against a static six-marine guard** (`loading-pressure-look`,
`workshop-strip-look`, `square-muster-look`, `hall-procession-look`, three
colonies each). At the loading's own pacing and distance none of the three new
objectives let a brood within twelve cells of its people.

Since the breach blooms in from the outskirts (`colony-breach-nouns.md`), the
breach's own fissures forage into town from landfall, and thirty to fifty of
the field's bodies reach a set piece in town within two minutes or so: a muster
held for two and a half minutes was lost on all three colonies to the field, not
to its bore. So the new objectives are short, done before the field comes in
force. Played on the bloom: the strip stacked on two of three (one with no
guard left standing) and was lost on one; the muster held on all three, one
with five of eight alive and a single guard; the hall was emptied on two of
three and lost on one. The loading, tuned before the bloom, now loses two of
three (one truck to the field at forty seconds); its pacing is the breach's
question to settle with the bloom. A fissure is fragile, and whether pressure
bores should be tougher is open, as is tuning against real play rather than a
static guard.

Where they host, over twelve colonies: the hall on nine, the muster on ten,
the strip on six (seven rows; on one no street in reach had room for its bore).
A row's workshops are too small to be staffed buildings, so the strip brings
its own hands; a muster or a hall's people are brought for the objective too.

The yard's warehouse has one doorway, onto the yard
(`BuildingPlacement.frontOnly`): its other walls back onto the compound wall.

## Plan

Done: steps 1 to 4 (`freight-yard-look`, `loading-pressure-look`; freight-yard
coverage in `generatedSpaceEvidence`, `FreightYardCoverageEvidenceTest`); the
factory row and the market square of step 5 (`factory-row-look`,
`market-square-look`); step 6 (`workshop-strip-look`, `square-muster-look`,
`hall-procession-look`).

1. The freight yard as an industrial compound layout, with its `LoadingBay`
   mark and a look suite.
2. The loading stands at a freight yard's bay where the map has one.
3. More colonies carry a freight yard: a parcel planned for it, and the
   two-bay yard where that parcel holds one.
4. Objective pressure for the loading: its own bore and director.
5. Further set pieces from the same seam: a factory row on a service lane, and
   a market square with stalls for cover (done: `MarketSquare` on a planned
   market lot, publishing its `OpenSquare`).
6. Side objectives on the new set pieces, each under its own pressure: strip a
   workshop on the factory row, hold a muster in the market square, empty the
   hall down the main street.

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
