# Elevation

Status: ACTIVE — StarCraft-style raised ground: a few discrete tiers, impassable cliffs between them, and ramps that are the only way up or down.

Written: 2026-10-22

Read `stories.md` for open work.

## Purpose

Elevation gives a battle map ground worth holding: a plateau above a road, a
redoubt on a rise, an overlook over a crossing, a ridge whose only ways up are
two ramps. It is a **tactical** model, not a terrain simulation. Height comes
in a few whole steps, the edge between two steps is a wall you cannot walk
through, and a ramp is the one place a step can be crossed. Everything elevation
changes — who can move where, who can see whom, how fire lands — follows from
those three nouns, so a player can read a map at a glance and a mission designer
can state "the only way onto the hill is the north ramp" and have it be true for
every unit that walks, drives or crawls.

It extends the existing natural terrain (`mapgen-nouns.md`, Authored natural
terrain). That model already raises inaccessible masses with drawn cliff faces
and said a standable summit would need "an explicit ramp and traversal/elevation
contract". This is that contract. A raised mass without a walkable top stays
what it was: an obstacle, not a level.

## Vocabulary

- A **tier** is a whole level of ground: `0` is the map's ordinary low ground,
  `1` a plateau above it, `2` a plateau on a plateau. Maps use at most three
  tiers above the base (`MAX_TIER = 3`). There is no continuous height and no
  partial step; a cell is on exactly one tier. A tier is a fixed number of
  metres above the one below (**tier rise**, one storey of about four metres),
  which is the only thing the sun and the relief composite read.
- A **plateau** is a connected region of walkable cells on one tier above the
  base. Its **top** is the walkable interior; its **rim** is the band of top
  cells within the face width of a cliff face. The rim is where the plateau's
  holders stand to see and shoot down, and where they can be seen from below.
- A **cliff face** is the band of cells between a tier and the tier below it.
  Face cells are not walkable for anything on the ground, are permanent
  natural solid (`CellTag.NATURAL_SOLID`: wall damage, demolition, dozers and
  crashes never open them), and carry the height of the tier they hold up. A
  face is at least one cell wide, because every ground-movement authority —
  infantry A*, the swarm's flow fields, vehicle clearance erosion, the mech
  clearance planner, manual WASD motion — already refuses an unwalkable cell,
  and a cell-wide band is therefore a cliff to all of them without a second
  rule. The **face width** is a map's authored choice of one or two cells.
- A **cliff edge** is any shared edge between two walkable cells on different
  tiers that is not inside a ramp. There should be none, because a face
  separates them; where a band's corner leaves two diagonal neighbours of
  different tiers touching, the shared edges between them are closed as well.
  A cliff is never crossed by a step.
- A **ramp** is a walkable strip of cells that climbs exactly one tier, laid
  along an **axis** from its **foot** on the lower tier to its **head** on the
  upper one, with a **width** in cells. A ramp's cells belong to neither
  tier: they are **ramp cells** with a lower and an upper tier. Their sides
  are cliff faces; only the foot and head edges are open. A ramp's width is
  what a unit needs to fit: infantry need one cell, a vehicle or mech needs its
  clearance diameter, so a three-cell ramp passes a squad and a scout car but
  not a tank. A ramp is directional only in its geometry; units cross it both
  ways.
- **Height** is what a cell stands at for sight and fire: a walkable cell's
  tier, a face cell's upper tier, a ramp cell's lower tier. Everything
  inaccessible that is not elevation (an old raised mass, a building) keeps the
  height it had — a wall is a wall at every tier.
- The **elevation map** is the per-cell record of tier, ramp membership and
  face membership, fixed when the map is generated. It is an immutable value
  shared by every copy of the navigation grid. Cliffs cannot be destroyed, so
  the map never changes in a battle and nothing derived from it ever needs
  invalidating.

## Movement

Elevation adds no movement rule; it is expressed entirely in the grid's
existing vocabulary.

- Face cells are unwalkable and natural solid; ramp and plateau cells are
  ordinary walkable ground.
- Every shared edge between walkable cells of different tiers outside a ramp is
  closed (`NavigationGrid.blockSharedEdge`).

So every movement authority honours a cliff with no change of its own:
infantry A*, hierarchical and squad routes, the zone graph, `SwarmFlowFields`,
vehicle clearance (`VehicleClearance.erode` reads walkability, and the face
band is unwalkable), the mech clearance planner and `ManualTerrainMotion`.
Routes go round to a ramp because that is the only connected way.

- **Infantry** take any ramp.
- **Vehicles and mechs** take a ramp their clearance fits. A ramp narrower than
  a chassis is a cliff to it, and a map that wants armour on a plateau must
  give it a ramp wide enough. Ramps cost the same as the ground they replace;
  there is no slope penalty, because a penalty the player cannot read is worse
  than none.
- **The swarm** (Runners and every strain) **does not climb cliffs.** It reaches
  high ground by ramps, which is what makes a ramp a chokepoint worth defending
  against it. *(Owner decision pending — see `stories.md`; a climbing strain is
  a later, separate noun, never a property of the base swarm.)*
- **Aircraft** fly over elevation and never read it in flight. A landing,
  a drop or a hover over a plateau uses the plateau's walkable cells like any
  other ground; a face cell is never a landing or drop cell.

## Sight

The fog of war, AI perception and direct-fire line of sight share one rule, so
a unit never shoots something it cannot see or sees something it cannot shoot
for elevation's sake.

- A unit **sees down and across freely**: from tier `t` every cell of tier `t`
  or lower is visible unless something taller than `t` stands in the way. A
  cliff face whose height is `t` or less does not block — a holder on the rim
  sees over its own cliff and down onto the low ground.
- A unit **does not see up**, with one exception: the **skyline rule**. Looking
  at higher ground, a unit sees the cliff face and the rim above it — a body
  standing on the lip is outlined against the sky — and nothing further back.
  A plateau's holders who stand back from the rim are hidden from below; those
  who step up to the rim to fire are seen, and can be fired on. *(Owner decision
  pending: strict StarCraft — no upward sight at all — and flat — elevation
  does not affect sight — are the alternatives.)*
- Ramp cells count as their lower tier for the observer on them, so a unit
  climbing a ramp sees up only when it reaches the head. A ramp is seen from
  both tiers.
- Higher ground between two units on lower ground blocks them from each other
  like a building does.
- Air observers (shuttles, flybys, recon) see every tier: their footprint is
  not limited by ground height, which is how a side without the high ground
  spots onto it.

Along a line, the rule reads: a cell no higher than the eye passes as it
always has (a face under the eye passes outright); on higher ground a face
passes under the skyline rule, the first top cell past it is the rim and is
seen, and every top cell after that stops the line. The fog's shadowcast, the
cell line of sight AI perception and spotting read, and the direct-fire ray all
apply it, so the three agree. The rule is a switch (`-Dbattle.elevation.sight=
skyline|strict|flat`), so a control run is one property away. Sight up a ramp
is not special: a ramp cell is the lower tier.

Height is a physical claim that never moves with what anyone knows
(`surface-relief-nouns.md`), and the skyline rule asks only what stands where.
Two older paths still read a face as a wall in every direction: the
anti-aircraft and turret air-sight walks, and the Bresenham scatter snap that
stops a spread round at the first wall. Both err toward blocking.

## Combat

- **Direct fire** follows sight: a round is fired only along a line the shooter
  could see down, and a cliff face higher than both shooter and target stops it
  as a wall would. A round fired up at a rim holder crosses the face below him.
- **Rim cover.** A rim cell gives cover against fire from lower tiers: the lip
  is between the shooter and the body. It is per-facing cover like a wall's,
  on the facings that look out over the face, and it applies only to shooters
  below. A cell at the foot of a cliff gets no cover from the face against
  fire from the top: the face is under the shooter, not in front of the body.
  (The old raised-mass cover at a cliff foot remains for masses that have no
  top, where nobody stands above.)
- **Firing up.** Beyond the skyline rule and rim cover there is no further
  penalty for firing uphill and no bonus for firing down. *(Owner decision
  pending: an uphill accuracy penalty, StarCraft I's miss chance, is the
  alternative.)*
- **Indirect fire** — mortars, artillery, bombs and grenades lobbed over —
  ignores tiers for its flight and lands where it is aimed. It still needs a
  spotter, and the spotter needs sight, so elevation reaches artillery through
  vision: a side that cannot see onto a plateau cannot call fire onto it.
- **Fire** (burning) stops at a face: a face blocks heat as a wall does.
- **Area effects** spread on the ground they land on. A blast at the foot of a
  cliff does not reach over the face onto the top, nor a blast on the rim down
  the face — the face is solid between them.

## Buildings, walls and fortifications

A plateau's top is ordinary ground. A generator may place compounds,
buildings, sandbags and gun pits on it exactly as it would on low ground,
provided their whole footprint, door approaches and circulation stay on one
tier and connect to a ramp. A building never straddles a face; a wall never
replaces a face. A redoubt on a plateau is the point: its walls and the cliff
together are the defence, and its ramp is its gate.

## Fire and creep

- **Fire** does not cross a cliff face: faces are bare rock or earth, not fuel,
  so a fire on the low ground stops at the foot and a fire on top stops at the
  rim. Fire climbs a ramp if the ramp has fuel. Heat and smoke follow their own
  model (`fire-nouns.md`); smoke drifting up a face is presentation.
- **Hive creep** grows over a face as it grows over any unwalkable natural
  ground (`colony-breach-nouns.md`): it is a film, not a walker, and a bloom
  creeping onto a plateau looks like one. Creep on a face changes nothing about
  movement.

## Generation

Elevation is generation-time work in the terrain brush's family. An
**elevation plan** extends the terrain plan with two stroke modes:

- **Plateau** raises a walkable top on a given tier. Its outline is the brush's
  continuous signed field, so rims are as irregular as the existing natural
  cliffs; cells inside the face width of the outline become face, cells inside
  that become top.
- **Ramp** cuts a ramp along a stroke's axis between the tier at its foot and
  the tier at its head, with the stroke's width.

The brush preflights the whole footprint before writing anything, as the
terrain brush does: plateaus never cover roads, buildings, doorways, fixtures
or a host's reserved cells, and a refused plan leaves the map untouched. It then
proves access: every plateau must have at least one ramp, every ramp must join
one top to the tier below, and the host's required places must stay connected.

Generators place plateaus where a mission wants them:

- **Countryside** (rural, the heart's country) places a few plateaus on open
  ground in place of some of the existing cliff-backed hills, each with one or
  two ramps facing the roads.
- **Mission maps** author their plateaus and ramps (the Cordon's line, a
  redoubt on a rise, an overlook over a crossing).
- City blocks do not get plateaus; a city's height is its buildings.

## Look

Elevation is drawn by the ground, never by a UI mark: nothing is painted on the
ground to say "this is high". It reads from three things the ground already
draws.

- **Cliff faces** are drawn in the face cells with the natural terrain's face
  materials, lit by bearing (the face that looks toward the sun is bright,
  the one that looks away dark) and with talus at the foot.
- **Plateau tops** are the ordinary ground of the map, a step lighter than the
  low ground, so the eye separates tiers even in shade.
- **Shadow.** Each tier stands at its height in the macro-relief field
  (`surface-relief-nouns.md`), so the battle's sun casts a plateau's shadow
  across the low ground on the side away from it, and a higher tier's shadow
  across a lower one. That shadow, at play zoom, is what makes a plateau look
  raised.
- **Ramps** are drawn as a graded strip of the ground's own material between
  the foot and the head, its sides edged by the face material, shading from
  low-ground colour to top colour along its axis.

Units on a plateau are drawn where they stand, with no displacement, scale or
parallax: the map is top-down.

## Laws

- Height comes in whole tiers; nothing stands between two tiers except a ramp.
- A tier change is never crossed except along a ramp, by any ground unit.
- The elevation map is fixed for a battle. Nothing in a battle raises, lowers,
  breaches or cuts a cliff or a ramp.
- Sight and direct fire use one elevation rule. A rule that let a unit see
  something it could not shoot, or shoot something it could not see, would be
  two answers to one question.
- Elevation never paints the ground with a tell; height is read from faces,
  shading and shadow.
- A map without an elevation map behaves exactly as before.

## Boundaries

- `mapgen-nouns.md` owns the terrain brush, its finishes and the existing
  inaccessible masses and excavations; elevation adds walkable tops and ramps
  to that family.
- `surface-relief-nouns.md` owns the relief composite and the sun; elevation
  supplies tier heights to its macro-relief field and nothing else.
- `fog-of-war-nouns.md` owns reveal composition; elevation changes only what a
  single observer's footprint contains.
- `ballistics-nouns.md` owns shot resolution; elevation supplies blocking and
  rim cover.
- `fire-nouns.md` and `colony-breach-nouns.md` own fire and creep spread.
