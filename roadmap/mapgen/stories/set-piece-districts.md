# Set-piece districts

Status: IN PROGRESS

Written: 2026-10-25

Read `set-piece-blocks.md` (a set piece is a block whose relationships are
authored), `mapgen-nouns.md` (compound claims, the road reservation, working-yard
templates), `cross-leaf-footprint-planning.md` and `precincts.md` first.

## Scope

The owner (2026-10-25), on the factory row: "that facility still seems rather
small. Once again there's nothing stopping us from creating larger facilities
around a road / block of buildings to tell more interesting stories." The
examples:

- a **main street**;
- an **industrial zone** of warehouses and factories;
- a **larger multi-building facility**, with streets and interiors inside a
  cordoned area.

A set piece today is one compound member and the street frame beside it, because
the compound's other members sit across the **road reservation** and the set
piece may not touch it. The freight yard shrank from 20x26 to 14x18 for that
reason, and the reservation crossing every pose is most of the factory row's
misses. A **district** is the next size up: one authored layout over a whole
multi-leaf claim, **the streets between its members included**.

## The noun

A **district** is a set piece whose ground is several adjacent leaves and the
streets between and around them:

| Part | What it decides |
|---|---|
| **Claim shape** | which leaves it takes: along both sides of one street (a **spine**), or a block of leaves round a crossing (a **quarter**) |
| **Streets** | the streets inside the claim are the district's own. It may straighten, widen, narrow or dress them, but it keeps every way the outside network had through or into the claim |
| **Members** | each leaf gets a **role** from the district's program (a shopfront, a warehouse, a freight yard, an admin block), facing the street the program says |
| **Edge** | open (its buildings face out onto the surrounding streets as any block does), or **cordoned** (a wall round the claim, with a **gatehouse** wherever a street crosses it) |
| **Marks** | what it publishes for missions: entries, gatehouses, spine, plazas, docks, the same way the freight yard publishes `LoadingBay` and the factory row `ServiceLane` |

A district **never** closes the network: a street the road graph needs keeps a
way through, gated or not. A district that cannot claim its shape is not built,
and its leaves fill as they do today. Districts reuse the set pieces already
built as member roles (the freight yard, the factory row and the market square
are blocks a district can place), so the kit grows in both directions.

**Why districts sit on compound claims, not precincts.** `precincts.md` places
grown places: a garrison is a programmed precinct with a wall drawn around what
grew. A district is smaller and authored: it lives inside a zoned settlement,
claims leaves the partition already cut, and lays a designed street plan over
them. It follows the shape `SpaceportDistrictPlanStage` already proved: a
connected cluster of leaves, chosen from the zoning pocket, published as one
compound, with one filler for all of it. That stage is the precedent to
generalise, not a second mechanism beside it.

## The seam

Built: `DistrictPlanStage`, `DistrictProgram`, `SpineClaim`, `QuarterClaim`,
`DistrictStreets`, and `District` published under `BspKeys.DISTRICTS`. The spaceport campus is now the
first district (a `CLUSTER` with no streets of its own) and compound claiming takes every planned
district's compound first. What the next programs build on:

- A program names a seed `BlockKind` with a `CompoundFiller` registered under it, and answers two
  read-only questions: `request` (a `SpineRequest` or `QuarterRequest`: the spec, which leaves may be
  members, and a **pocket** weight per leaf) and `plan` (each member's role, the seed member, and the
  street mask with its claim's streets rewritten). Programs run in `BspCityGenerator.DISTRICT_PROGRAMS`
  order, each over the leaves the ones before left; a member must still carry an eligible kind, so a
  district never takes a compound seed, a planned freight yard or another district's leaves.
- **Pocket**: a claim stands in its zoning pocket on balance, its members' weights adding up to at least
  their count; the best claim stands deepest in its pocket, then has the most members, then the longest
  street.
- **Entries are mouths.** An entry cell is a street cell of the claim beside a street cell outside it; the
  touching entry cells where one outside street meets the claim are one mouth. A rewrite is committed only
  if every mouth keeps a street cell, any two mouths joined through the claim's streets before still are,
  and no trunk cell changes. So a program may narrow a street at its mouth but not close it.
- **Quarter**: a rectangle the members and their streets tile exactly, up to `Spec.rows` rows of two to
  `Spec.columns` members either side of a **main cross street** that runs its whole length. Each row's
  members share its full depth with a cross street between neighbours running that depth, and the rows need
  not line up (a row of two may face a row of three). `Quarter.streets` are the main streets first, then each
  row's, every one whole. Found in both frames (main street along x, then y); no inner street may be road
  inside a trunk's band, which the plan stage passes as a mask. Among quarters as deep in their pocket and as
  many members, the larger ground wins.

1. **A district plan stage**, after the leaves are labelled and before the
   compound claim and the road graph, the same slot `IndustrialTemplateParcelStage`
   and `SpaceportDistrictPlanStage` use. For each district the map's character
   asks for, it finds a cluster of leaves of the claim shape in the right zoning
   theme and hands it to the program. It refuses before changing anything.
2. **Claim shapes**:
   - **spine**: a street segment of at least N cells, with M leaves fronting it
     on each side. Main street and the industrial zone use it.
   - **quarter**: a 2x2 or 2x3 block of leaves and the cross streets between
     them. The cordoned facility uses it.
3. **The district owns its inner streets.** Its road cells and their
   reservation are rewritten by the program, not left to `RoadGraphStage` to
   publish as ordinary streets. The program then publishes them back as roads,
   so vehicles, the road graph and the tactical links still see one network.
   Unit tests pin this: every entry the outside network had into the claim
   still connects to every other.
4. **One district filler** per program, dispatched like a compound filler,
   placing member roles (existing fillers and set pieces where they fit), street
   dressing and the edge.
5. **Marks** on `MapResult` for each district.

## The first three

- **Main street** (spine, COMMERCIAL or CIVIC pocket): one straight street
  four to six leaves long, shopfronts both sides with their doors and windows
  on the street (`BuildingPlacement.frontOnly`), awnings, planters, street lamps
  and parked cars as cover along the kerbs, a market square or plaza at one end
  (the market-square set piece) and a landmark (civic hall, chapel) at the
  other. It is the long, straight, enfiladed street, and its cross streets are
  the ways onto it. Marks: the spine, the plaza, the landmark door.
- **Industrial zone** (spine or quarter, INDUSTRIAL pocket): a service street
  with freight yards and a factory row on it, warehouses with loading docks,
  a tank farm or a vehicle depot, pipe gantries over the street, and freight and
  hauler traffic as cover. Gates and yard walls make it a maze of short, walled
  sightlines. Marks: the yards' `LoadingBay`s, the row's `ServiceLane`, the
  service street.
- **Cordoned facility** (quarter, any pocket the program allows): several
  buildings on internal streets inside one wall, with gatehouses where streets
  cross it: an admin block, labs or workshops, a depot, a yard, a courtyard. It
  is the place a mission is about (a research station, a depot, a civil-defence
  centre), with a single defensible perimeter and inside streets to fight
  along. Marks: gatehouses, the courtyard, each building's role.

**Main street, as built.** `MainStreetProgram` asks for a spine of two or three members a side, a
street at least 24 cells long and no wider than a primary trunk, members of shops, homes, offices,
plazas or parks. Its pocket weighs the zoning's civic core 2 and mixed town 1, plus 1 for a lot the
labeller already made a shop, an office or a square. The larger end leaf is the plaza, the leaf at the
other end the civic hall; its rewrite narrows each cross street between shops to three cells.
`MainStreetLayout` stands each member's shops behind a pavement of one to three rows as a frontage row
(`town-plan.md`): shopfronts five to seven cells wide sharing party walls, doors and windows on the street and
on the service yard behind; a member too short for two stays one shop, and the hall is one building with its
doors on the street alone (`BuildingPlacement.frontOnly`). It dresses the kerb with planters and counter stalls every third cell and a parked van per
shop frontage, all kept out of doorways and corners, paves the plaza with a bandstand where it is ten
cells a side, and paves the spine and its cross-street stubs as city street, so the street finish lays
asphalt where a colony would have left a lane. It publishes `MainStreet` on `MapResult.mainStreets`: the
centre line from the plaza end to the hall end, the carriageway width, the plaza's bounds and the hall's
front door.

Coverage (`generatedSpaceEvidence --tests *MainStreetPlacementEvidence`): 15 of 24 medium city maps and
14 of 24 colonies (the freight yard and the market square plan their parcels first). Cities zone few civic
or mixed pockets, so the residential quarter weighs as much as the mixed town and a high street can stand
among houses (owner, 2026-10-25); without it cities carried 5 of 24. Look:
`createSnapshots -Psnapshot=main-street-look`.

The kerb's rhythm is lamp, planter, lamp, stall, every third clear cell, each lamp's arm over the
carriageway, and each shop door with a cell of frontage either side has an awning over the pavement in front
of it, one colourway to a shopfront (striped, green canvas or steel, read off its door's place, no random drawn). The art
is `street-dressing` (geometry-first, `templates/street-dressing-guide.py`).

**Street dressing kit for the next two programs** (`StreetDressing`, art `industrial-street-kit`): the
industrial zone lays its pipe gantries with `pipeRun` (a column and beam at each end and every `bay` cells,
overhead rack between: across a street it is one gantry on its two kerbs, along one a pipe rack); the
cordoned facility's gatehouses use `boomGate` (post and a three-cell arm a figure walks under) and
`bollard`; `jerseyBarrier` is there for a program that wants a chicane. Overhead pieces draw in the roof pass and block nothing. Preview staged on a
main street: `createSnapshots -Psnapshot=street-kit-look`, which also frames the owner's style choice for
main street (cast-iron lamps and canvas awnings against cobra-head lamps and steel canopies).

Open on main street: the market square takes the plaza role once its lot can be a district member (today it joins
two parcels after compound claims, so a district member is never its lot).

**Cordoned facility, as built.** `CordonedFacilityProgram` runs after main street and asks for a quarter
of up to two rows of three, inner streets no wider than five, members at least seven a side, of works,
public buildings, shops, dense blocks, yards, squares, parks, rubble or homes, never on the map's border
cells. Its pocket weighs the zoning's industrial, civic and military quarters 2 and the mixed town 1, plus 1
for a lot already labelled a works, a civic building or a yard, so a facility stands among houses only
where its zoning carries them. The member nearest the quarter's middle is the **courtyard**, the largest of
the rest the **admin** block (the compound's seed), the next the **depot**; a quarter of five or six gives
its smallest remaining member to a **yard**, and the rest are **labs** (workshops at a depot). Its
character is read from the zoning at its middle: a **depot** in an industrial or harbour quarter, a
**civil-defence centre** in a civic, residential or military one, otherwise a **research station**.

Its rewrite is the gatehouses: every end of an inner street on the quarter's edge with street beyond it
narrows, for three cells in, to a lane three wide nearest the street's middle that meets the street
outside; the cells either side become the gatehouse. An inner street that ends against a lot rather than a
street is no way in, so the rewrite closes its last cell and the wall runs across it. `CordonedFacilityLayout`
then walls the quarter's outer rim at the strength its character asks (`Fortification.GARRISON`'s, or
`CivilDefenceCompound.WALL_HP` for a civil-defence centre), dressed as a precinct wall
(`InstallationWalls.perimeter`) with the military base's firing slits (`CompoundWallApertures`) and
turreted corners. Each gatehouse is solid either side of its lane to a cell past each kerb, its lane
striped as a checkpoint, turreted posts at the wall line, a boom gate across the lane on the first row inside
(its post on open ground beside the lane, off the road reservation) beside a sandbag course facing out, and a
bollard before each jamb on the street outside where that is off the reservation.
Buildings back onto the wall with a pavement onto their streets and face the main cross street, doors and
windows there only (`BuildingPlacement.frontOnly`): the admin block a civic headquarters, labs a clinic's
rooms (a workshop's at a depot), the depot a vehicle bay behind a striped forecourt with an APC, cargo truck
or utility truck parked on it. The yard is striped hardstanding with crates, pallets and drums; the
courtyard a brick square with a stone border, a planter at each corner and a radar mast in the middle
except at a depot. The inner streets are paved city street with hedgehogs and cobra-head street lamps by
turns on their kerbs every four cells, off the road reservation and clear of crossings and gatehouses. It publishes `CordonedFacility` on
`MapResult.cordonedFacilities`: its character and wall bounds, each gatehouse's lane with the step out and
the step in, the courtyard, and each building's role, shell and front door.

Coverage (`generatedSpaceEvidence --tests *CordonedFacilityPlacementEvidence`): 11 of 24 medium city maps
and 21 of 24 colonies, nearly all two by two (one colony carries a two by three; two by three blocks are
rare in the partition). Main street keeps 15 and 14; running the facility first moves neither by more
than one map. `storyMapPools` passes 144 of 144 with no seed swapped. Look:
`createSnapshots -Psnapshot=cordoned-facility-look`.

Open on the cordoned facility: no tactical node or mission reads the mark yet; a research station did not reach the
look stills, its seeds' battles laying another map.

## Order

The seam and main street land first, as one piece: main street is the simplest
program and proves the claim and the street rewrite. The industrial zone and the
cordoned facility then build on the seam in parallel, each as its own program.

## Acceptance

- Unit tests on the claim shapes and on the street rewrite: every outside entry
  into a district still connects to every other.
- A district that cannot claim is not built and its leaves fill as today
  (`storyMapPools` passes, and moved maps are named).
- Coverage stated per family: of 24 medium city maps and 24 colonies, how many
  carry each district.
- Owner look stills at 1920x1080 from the look preset: the whole district at
  play zoom and a close look along its main street.
