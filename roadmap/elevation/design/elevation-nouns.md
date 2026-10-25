# Elevation

Status: ACTIVE — StarCraft-style raised ground: a few discrete tiers, impassable cliffs between them, and ramps that are the only way up or down.

Written: 2026-10-22

Updated: 2026-10-25 — relief: natural ridges swell, narrow, wander and taper; a berm is graded, straight and cut
square; a brook's valley sides differ; outcrops stand off the mission's lines.

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
  against it. (Owner, 2026-10-22. A climbing strain would be a later, separate
  noun, never a property of the base swarm.)
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
  who step up to the rim to fire are seen, and can be fired on. A holder back
  from the rim that fires down is revealed to the low ground for about three
  seconds after the shot, so the low ground can answer it (owner, 2026-10-22:
  skyline sight, firing down reveals). While exposed, sight and direct fire
  from lower ground to its cell are judged from its own height, as if looking
  down the line it fired along: whoever it could see down to sees it back and
  can shoot it. The exposure follows it if it moves; a new shot down restarts
  it (`FiringExposure`, `-Dbattle.elevation.firingReveal=false` is the control).
- Ramp cells count as their lower tier for the observer on them, so a unit
  climbing a ramp sees up only when it reaches the head. A ramp is seen from
  both tiers.
- Higher ground between two units on lower ground blocks them from each other
  like a building does.
- A **raised gun** (a gun on a plinth of whole tiers, `ground-turret-tiers.md`)
  sees from its ground's tier plus its plinth, and like an exposed holder it
  is seen back from that height: sight and direct fire between it and lower
  ground are judged from its eye. The height is the gun's, not the ground's:
  the plinth's cells are not raised ground, nobody stands on them, and the
  gun's death lowers its cell again (`NavigationGrid.setRaisedEye`). To the
  sun the plinth is a block over the whole footprint, standing until the
  structure dies (`CellTopology.getPlinthTiers`), and it casts as a wall of
  its height does.
- Air observers (shuttles, flybys, recon) see every tier: their footprint is
  not limited by ground height, which is how a side without the high ground
  spots onto it.
- A **sensor sweep** carried by a body on the ground reads past higher ground
  as it reads through walls, within its read radius only; beyond it the
  sweep sees from the carrier's own tier. A suit at the cliff foot reads the
  holders just behind the rim, not the whole plateau.

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
  penalty for firing uphill and no bonus for firing down (owner, 2026-10-22;
  an uphill accuracy penalty, StarCraft I's miss chance, was declined).
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
or a host's reserved cells, and a refused plan leaves the map untouched. The
one exception is ground the plan names **built** (a terrace's, below): what
stands there is raised with the top, though a face or a ramp is still refused
on it. It then
proves access: every plateau must have at least one ramp, every ramp must join
one top to the tier below, and the host's required places must stay connected.

Generators place plateaus where a mission wants them:

- **Outdoor landscape.** The closing outdoor pass (`OutdoorLandscapeStage`)
  places as many plateaus as the mission asks for (`BspKeys.PLATEAUS`; none
  when it does not ask, so every existing map is unchanged;
  `-Dbattle.mapgen.plateaus=N` asks on every map, for evidence). It runs after
  the hills and before the trees, so a plateau top on living ground can carry
  a wood. A plateau stands only where there is room for it: on open ground
  clear of every reservation the pass keeps (roads, places, landing
  approaches, anchors and the routes between them) and of the hills, sized to
  the room it finds, at least two cells from any other plateau so two never
  merge, with one ramp facing the nearest reserved ground, so it overlooks what
  the host keeps open and is climbed from it. A ramp up a tall south face is
  cut deeper so it reaches the top. The closing of its faces must leave the
  ground around it connected. Dense maps simply get fewer or none.
- **Mission maps** author their plateaus and ramps (the Cordon's line, a
  redoubt on a rise, an overlook over a crossing).
- **Ridges.** A mission names a band of high ground along any path through
  its plan's landscape (`RidgePlanner`, the heart's cordon ridge). A brook may
  ask for its **valley sides**: a ridge along each bank, clear of the water,
  stopping short of every ford and climbed from the country, so the fords and
  bridges are the ways down to the water. The mission says where
  the high ground is and the map says where it is broken: wherever a road, a
  lane, a building, a landing or other reserved ground crosses the band, the
  band breaks into a pass, so a ridge never puts a cliff across a road. Each
  unbroken run becomes a hill segment, a plateau with one ramp up from the side
  the mission names. A segment is left out when it would not fit or would cut
  the host's ground apart, proved a segment at a time against the segments
  already kept. Nothing grows on a ridge's ramps, and no stock hill grows on
  ground a ridge names. A brook's two valley sides are never a matched pair:
  each stands its own seeded distance back from the water and is its own width
  through. Elevation is stamped once: when a mission layout has
  ridges of its own, the landscape's ridges go up with them in the works phase
  (`mission-layout.md`, Ridge); otherwise the landscape pass raises them.
- **Relief.** A band says what its segments look like (`RidgePlanner.Relief`).
  **Natural** ground (the default: a hollow's rim, a valley side, an outcrop)
  is not a capsule of the band's width: each segment swells and narrows along
  its run, from a waist that keeps its top one body (a radius of 4.5) to the
  band's half width plus its **spread** (three tenths of its thickness), its
  crest wanders from side to side in the room its width leaves, and it tapers
  to a spur at each end, all on smooth seeded waves tens of cells long. Its
  ramp climbs where it is broad. Natural ground that will not stand so is
  tried within the band's own width, then as the plain capsule. A **berm** is
  built ground, an engineered bank the colony graded (owner, 2026-10-25: the
  cordon's ridge and the Cordon's line): its band's width the whole way, its
  faces unroughened and straight, its ends cut square two cells inside each
  pass, so a break at a lane is clean (`ElevationPlan.Plateau.graded`). The
  ground a natural band holds or seals reaches its spread further.
- **Outcrops.** A mission may scatter natural high ground about its country,
  off every line it drew: short bands at seeded places, bearings and bends,
  climbed from a seeded side, kept clear of its lanes, rides, brook, hollow,
  knoll and places (the heart's, `HeartCountry.outcrops`). They are the
  country's hills and mesas, not the story's, and are broken or left out as any
  ridge is.
- **Held ground.** A ridge raised after generation fits only open country:
  among built places every stretch is broken. So a landscape may **hold** a
  ridge's ground before the places grow (`Landscape.held`,
  `RidgePlanner.heldGround`): the band, its apron and a margin for what the
  landscape pass reserves beside built things. Growth, claims and town fields
  keep off it, and the landscape pass then raises the ridge there among them.
  The ground round each place's seed is never held, so a place always has
  somewhere to start. Held ground is a reservation, not a stamp: elevation
  still goes down last, and whatever generation laid across the band anyway
  (a weld road, an access route) is a pass like any other. A landscape may
  hold the ground from the fields alone (`Landscape.withUnploughed`): the
  places grow exactly as they would have and only ploughing yields, for a
  mission whose places must not move.
- **Sealed ground.** Held ground keeps the places' growth off a band, but what
  generation routes still crosses it: the welds that join road networks, the
  access routes the landscape keeps open, a track, a landing's approach, a
  layout road. A band a mission wants to stand whole (a hollow's rim) is
  **sealed** (`Landscape.withSealed`): held from growth, claims and fields
  whatever the landscape says, and gone round by every route, which reaches
  what lies beyond through the gaps between the bands, the passes the mission
  drew. Sealed ground holds a band's sides and never past its ends
  (`RidgePlanner.sideGround`), so a pass stays as wide as it was drawn, and
  its margin covers a street's half width and a building's reservation, so
  nothing grown up against it reserves the ridge's own ground. A weld that can
  join two networks only across it still does, so the map is always one road
  network; a track and a landing's approach stop at it. Its segments are
  proved over the whole map, as a terrace is: a ring's inside joins the
  country only through its passes.
- **Under works.** A mission layout's ridge (`mission-layout.md`, Ridge) is
  raised before the layout's works are laid, so a trench resolved along it
  is dug on its crest (the Cordon's line). Places are built before it and stay
  on the low ground of its passes, unless the ridge carries them as terraces.
- **Terraces.** Built ground stood on a crest (`TerracePlanner`): the place's
  whole box goes onto one flat top, its cliff stands in open country a margin
  beyond it, and wherever a road crosses that cliff a ramp at least a tank's
  width carries the road down. A side whose outline a road runs along, or
  whose cliff would land on built ground, is pushed out a cell at a time, so a
  road beside the place runs on the top and is ramped where it leaves; a
  terrace with no road over its edge has no way up and does not stand. A
  terrace may name its **supply side**: that side is ramped whether or not a
  road was laid across it, the ramp cut from open ground below the side as
  near its middle as the ground allows, and a terrace whose supply side has
  no such ground does not stand. Without it a place whose own road was not
  laid could be climbed only from wherever another road happened to cross,
  and what was sent up from behind it stood below the cliff. The
  terraces are planned first, each kept only when the whole map's ground stays
  one body once its cliff closes (a terrace's top joins the low ground through
  ramps that may lie far from any box round its faces, so the proof is the
  whole map's, not a local one). The ridge's band then runs on into a
  terrace's top and merges with it instead of breaking into a pass, never
  cutting a ramp of its own into it. The brush raises whatever the place built
  inside the terrace's outline (its **built** ground) and refuses a face or a
  ramp on it as on any built ground. Room for the cliff is kept before the
  places grow (`Landscape.kept`): boxes round the place's seed that the other
  places' growth, claims, road arms and fields keep off whatever the held
  ground says. A required terrace that cannot stand fails its map (the
  Cordon's redoubts); an optional one leaves its place in a pass.
- **Knolls.** A place stood on high ground of its own (`Landscape.withKnolls`,
  `KnollGround`): a flat top round its seed (moved in from the map's edge
  rather than shrunk), the place growing only on it, and a ring of open country
  round it that nothing else grows, claims, ploughs, welds or tracks across,
  for the cliff and its ramps. The place claims nothing in a lane round the
  top's edge, so the strip of top between its walls and the cliff stays one
  body. Its roads leave by **gates**: on each side the mission names, a
  straight road from its streets out across the ring, which the terrace ramps
  down. The top is raised as a terrace with the map's other elevation, whether
  or not the layout has a ridge, and reported under the knoll's name. A knoll
  whose ground would come within a seed's room of another place's seed is
  refused before anything grows and its place grows as it would have; a
  required knoll that is refused or cannot stand fails its map.
- City blocks do not get plateaus; a city's height is its buildings. A city's
  countryside margin may.

## Look

Elevation is drawn by the ground, never by a UI mark: nothing is painted on the
ground to say "this is high". It reads from three things the ground already
draws.

- **Baked cliffs.** Grass-topped raised ground is not drawn with tiles. The
  generator hands the layout to the renderer (`CellTopology.cliffArt`), and the
  renderer bakes the owner's look from the layout's own fields once per battle
  (`CliffArtLayer`, `CliffShading`): a layered escarpment and cut stone steps,
  described below. It bakes only the ground within reach of a face or a ramp,
  in chunks of one texture. Each texel is a premultiplied colour laid over
  whatever ground the tiles drew, inside the ground's colour target, so the
  composite shadows and lights it like the rest of the ground. Faces and ramps
  are therefore drawn from the same fields that decide which cells are face.
  `-Dbattle.render.cliffLook=false` is the control: the generator hands
  nothing over, and grass-topped cliffs draw with the cliff tiles as below. Ice
  and rock tops always draw with the tiles.
- **Cliff faces** (the tile look) are the natural terrain's face bands (`mapgen-nouns.md`,
  Authored natural terrain) clipped from the same signed field that decides
  which cells are face, so the face drawn and the cells nobody can stand on
  agree. A talus apron lies at the foot; the face's shade follows its bearing
  to the light. The first tier draws in the terrain surface's raised layers
  and the second is stacked after them, so an upper tier's apron and face lie
  over the lower tier's top; a third tier stands and fights but draws as the
  second's top.
- **Plateau tops** are the map's own ground: grass-topped where a quarter of
  the tops is grass (a dirt patch on a meadow does not make bare rock), ice
  where most is snow, rock otherwise.
- **Shadow.** Each tier stands `TIER_RISE_METERS` (4 m, a storey) higher in the
  macro-relief field (`surface-relief-nouns.md`), a face at the tier it holds
  up and a ramp by how far up it stands, so a ramp is a slope in the relief
  and the battle's sun casts a plateau's shadow across
  the low ground on its far side and a higher tier's across a lower one, with
  no shader change. Raised ground is read at the cell corners and runs
  smoothly across each cell (`MacroReliefField.groundCornerMeters`), while
  what stands on it stays flat per cell: held flat, a plateau is a pile of
  square blocks to the sun, and its shadow and lit faces step along every
  diagonal of its outline. The shadow's reach counts the highest tier as well
  as the tallest building, or a raised ground's shadow is cut short.
- **Ramps** (the tile look) are a floor of the talus material laid over the ramp's corridor,
  clipped to its sides, in the cap layers so it covers the cliff bands its cut
  would otherwise show.
- **South faces** (`ElevationPlan.southFace`) may be drawn taller: a face whose
  outside looks down the screen is widened by up to three cells, because the
  camera looks at a south face and over a north one. The extra width is face
  in the battle too. The owner chose one-cell faces with tall south faces,
  drawn with the cliff tiles (2026-10-22). Ramps get art of their own that
  reads as a slope: a floor that climbs and walls that taper up the cut.
  The owner chose cut stone steps, darkened to sit with the cliff's
  grey-browns and rough-hewn rather than ruled (2026-10-22). The cliff's edges
  are a layered escarpment (owner, 2026-10-22): strata ledges along the face,
  a broken turf lip over exposed soil, and a gravel apron feathering into the
  grass, every edge wandering below cell scale.
- **Graded banks.** A berm's faces are drawn as built
  (`ElevationLayout.gradedCorner`, `CliffShading`): a turfed bank over packed
  earth worn in broad patches, with no strata, ledges or scree, its edges
  barely wandering, a clean trodden line along the crest and a drainage ditch
  along the toe. Where a berm meets natural ground the two looks blend across a
  cell.

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
