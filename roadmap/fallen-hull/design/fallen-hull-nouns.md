# Fallen hull — Nouns

Status: DRAFT — the model for a derelict capital ship down on a world and the
watch it pours out; its ground (The ground) and its battle rules and the stripper and hauler are built on
the recommended picks (What is built). Its first instance is the authored arc
`long-survey-arc.md`, played on owned seeds before any campaign phenomenon.

Written: 2026-11-10

Read `derelict-lore-guide.md` in full first, then `derelict-watch.md` and
`derelict-security-network.md` (What is built): this doc reuses their
vocabulary (zone, node, flock, cradle, rounds walker, line, siege and vault
frames, hall frame, tender, repair drone, service bay, reserve bay, vent, wreck,
wake, contingency) and never restates it. Read `colony-breach-nouns.md`
(Campaign: blooms) for the Rav's spread, the pattern this one must stay apart
from.

## The fantasy

The owner (2026-11-10): "a derelict capital ship crash land mission set where
the derelict security faction pours out as a problem the player can help to
face", with a mechanism by which it can propagate "similar to bore holes", and
"large scale ground combat with walkers and swarmers vs the factional
defenders' forces".

A centuries-dead Explorarium survey mothership has come down on a settled
world. It was built to prepare worlds for colonists, and on the ground it does
what it was built for: it surveys the country round it, claims it on a grid,
clears what stands on its claim and secures it. Nobody is coming. The colony on
the claim is an obstruction. The world's own military holds the line while the
hull's autoforge prints walkers and **survey stakes** and carries them out, a
lattice point at a time, feeding on the metal it strips from everything it
claims. The company is hired to help: get people out, kill stakes before they
rise, hold the line beside the garrison, and at the end go into the hull and
put its forge out.

## Origin (canon and mod choice)

- **Canon.** Explorarium survey motherships explored and prepared worlds for
  colonization; they carry autoforges and are "in essence a von Neumann probe"
  (`derelict_mothership`, `derelict-lore-guide.md`, Canon anchors). They are
  found derelict in the Sector with `derelict` defenders.
- **Mod choice.** One fell (`long-survey-arc.md`, Why it fell). Its survey
  program is intact and its sub-gamma control runs it to the letter: survey,
  claim, clear, secure, build a landing. Everything below is mod direction.
- **The exception to "networks do not spread".** A site network keeps the
  numbers it was built with (`derelict-lore-guide.md`, What it is). A fallen
  hull is not a site network: its autoforge is the one derelict thing that
  makes more, and only from feedstock it hauls home where anyone can see it.
  Every stake it plants becomes an ordinary site zone the moment its link to
  the hull is cut. (Pick `hull-spread-law`.)

## The hull and the Rav

The two must never read as the same thing (`derelict-lore-guide.md`, The flock
and the Rav):

| | Rav (a bloom) | A fallen hull |
| --- | --- | --- |
| Origin | a weapon under the ground, woken by a dense colony | one machine come down on top of it, by accident |
| Grows by | eating people; a fed bore buds another wherever it is | hauling metal home; the forge prints stakes and walkers in a fixed order |
| Spreads | organically, toward food | on a **claim lattice**: regular points outward from the hull, visible and predictable |
| A new outpost | a bore erupts | a stake is carried out by a **stake train**, planted, and rises in stages over minutes |
| Cut it off | collapse the bores; starve it by lifting the colony out | cut the **relay chain**: a stranded stake keeps what it has and builds nothing more |
| Ended by | the mother bore collapsed | the **autoforge** inside the hull put out |
| Wants | food | the claim cleared for colonists who never come |

## Vocabulary

| Noun | What it is |
| --- | --- |
| **Fallen hull** ("the hull"; spacers: "the Surveyor") | The crashed mothership: an impact trench, a debris field, and the hull itself as a roofed megastructure the company can enter (`ruins-expedition.md`, interiors). It is the network's root zone and holds the autoforge. |
| **Autoforge** | The hull's fabricator: the network's heart, the vault of the hull's interior. It prints the **build queue** while it has feedstock. Put out (destroyed or its power cut), nothing more is printed anywhere, and every stake is stranded. |
| **Build queue** | The fixed order the forge prints in, from the survey program: stakes and walkers at their costs, never chosen by what the company does. Readable: the briefing and the rail can say what comes next. |
| **Forge cycle** | The forge's clock: each cycle, with feedstock for the next item in the queue, it prints it. Cue: the forge's hammer and the hull's stacks venting. |
| **Feedstock** | Metal: the forge's one input, held as a store at the hull and at each established stake. Never people. |
| **Stripper** | A derelict-line walker with cutting arms (unarmed or lightly armed) that cuts up structures, fences, vehicles and wrecks on the claim, the company's and the garrison's included, into feedstock. |
| **Hauler** | A load-carrying walker or crawler that takes stripped metal from strippers to the nearest store along a **haul road**. Destroyed loaded, its load is lost. |
| **Claim lattice** | Points on a regular grid round the hull, spaced a zone's perimeter apart, laid by the ground, never moved by play. The forge plants the nearest open point that links back to the hull. |
| **Survey stake** ("stake") | A prefabricated outpost the forge prints, carried out by a stake train and planted on a lattice point. It rises in stages (below) into an ordinary security zone with its own node. |
| **Stake train** | The escort that carries a printed stake out: its walkers round a hauler with the folded stake. Kill the hauler before it plants and the stake is scrap. |
| **Relay chain** | The links from each stake back to the hull through the stakes between. A stake builds (rises a stage, takes feedstock, receives printed walkers) only while its chain holds. |
| **Stranded** | A stake whose chain is cut, by its node, a node on its chain, or the hull's autoforge going out. It keeps every unit and stage it has and plays by the site-network rules from then on: fixed numbers, repairs from its own bay. |

### A stake's stages

| Stage | What stands | What it does |
| --- | --- | --- |
| **Planted** | A folded pylon, armoured, unarmed | Nothing yet; counts down to raising while its chain holds |
| **Raised** | The pylon opens into a node and a cradle: a new zone, dormant | Its flock launches as its zone wakes; joins the network's relays |
| **Established** | Folded posts, a service bay and a feedstock store | Strippers and haulers work from it; printed walkers are posted to it; the next lattice point beyond it becomes plantable |

A stake killed planted is scrap and leaves no wreck. A raised or established
stake is fought as a zone: kill its node and it is stranded and blind.

## Laws

- **Every new unit is printed.** Nothing appears from nowhere. A walker or stake
  that was not on the map at the start came out of the hull's forge, cost
  feedstock, and walked out where it could be seen. (`derelict-watch.md`'s "no
  growth" law holds for every stranded stake and every site network.)
- **Metal, never people.** The hull strips structures and wrecks. It kills
  people who are on its claim by the watch's ordinary rules; it does not take
  them, eat them or gain anything from them.
- **The lattice is fixed.** Where a stake can go is laid by the ground before
  the battle and shown once seen. The forge's queue is fixed. The company can
  read the spread and plan against it; the counterplay is denial, not guessing.
- **Honest knowledge.** The hull knows what its zones' sensors, flocks and relays
  tell it (`derelict-lore-guide.md`, Honest knowledge); a stranded stake knows
  only its own.
- **The claim is the perimeter.** The hull's walkers pursue to the claim's edge
  (the outermost established stake's perimeter), not past it, except in the
  contingency the autoforge's breach calls.
- **Allies are on the claim too.** The world's garrison fights the watch under
  its own authority (`bloom-mission-arc.md`, Law: a living field); its wrecks
  are feedstock like the company's.

## In a battle

- **Setup.** The ground lays the hull (trench, debris, interior), the claim
  lattice, the haul roads and the stakes that already stand at the chapter's
  point in the arc, each at its stage (`long-survey-arc.md`, chapters).
- **The forge clock.** Each forge cycle with feedstock for it prints the next
  queue item. A stake goes out by train to the nearest open, linked lattice
  point; a walker walks to the stake or zone the queue names.
- **Feedstock flow.** Strippers work what stands on the claim, haulers carry it
  to the nearest store, and a store's metal reaches the forge along the chain
  (abstracted: a linked store counts as the forge's).
- **The company's levers.** Kill haulers loaded; kill a stake planted, before
  it rises; cut a chain at its weakest node to strand everything beyond;
  scrap wrecks before strippers take them (`derelict-watch.md`, Denial); in the
  last chapter, put the autoforge out.

## The ground (built)

The crash site is laid by mapgen over a story map's own ground, from the pool
entry's `hull` patch op (`story-map-pools.md`, The patch), and published as
`MapResult.hullSite` (`HullSite`), the seam the rules read:

- **The hull** is one survey mothership plan (`HullPlan`, 88 by 32 cells, its
  bow tapering), built as a ship from the ship-interior kits, so going in reads
  as boarding a wreck. It **broke up coming in**: it lies in three **sections**
  (stern u 0..23, midsection 24..45, bow 46..87; `HullPlan.SECTIONS`) along one
  line on a cardinal heading, with 18 and 22 cells of ploughed ground between
  them and each shifted a few cells across. It came in stern first: the stern
  lies furthest along and the trench runs off the bow's tip. Nothing joins two
  sections but the ground; each is torn open at its broken ends, whose remaining
  wall is the hull's cut face (`fallen-hull.sheared-wall`) with its sheared deck
  dragged out over the ground beyond (`fallen-hull.shear-edge`). The interior
  is published as the ruins' `RuinsDelve.Interior` (entrances, aprons, halls,
  leaf doors) and the sections as `HullSite.sections`. A four-cell freight
  spine runs from the stern's cargo door to the **forge hall** (the interior's
  vault, in the bow section, the autoforge on its keel line near the bow),
  broken where the hull broke, with four-cell cargo docks into three holds (and
  a fourth, the **prow hold**, through the forge hall's far wall into the taper) and
  the **launch bay** (in the midsection) and a two-cell service door to the
  crew deck (`derelict-watch.md`, section 3). Ways in: the stern door, the
  bay's door on the port flank, the four **torn ends** (the big breaches), and
  three smaller **rents** in the flanks into the holds, each with an apron and
  two berths (a torn end's apron runs half the gap, so the two facing across it
  never meet). A breach is open sky over rubble, its edge the armour's broken
  stubs, and has no door.
- **Shell and bulkheads.** The solid hull within two cells of the outside or a
  breach is the **armour** (the hull-plate installation wall, whose door,
  window and rubble are the armour's hatch, port and torn breach roots); the
  inner walls facing a deck are the ship deck's own bulkhead, and the solid
  hull behind them is armour plate too. Each **compartment** (the spine with the stern door, and each hall
  with its dock) has its kit's deck: passage plate, worn hold plate, bay deck,
  crew deck, bolted plate in the forge hall. `HullDressing` fits it out with the
  hull's own pieces (`fallen-hull.*`) over the ship kits: a conduit run and
  frame ribs down the spine; cargo, survey racks and crushed bulkheads against
  the holds' walls; launch cradles in the bay; consoles and a sensor drum on the
  crew deck; plant, coolant, hoppers and switchgear in the forge hall with
  cables run toward the autoforge; torn plating in the breaches. Pieces with
  cover stand clear of the docks, the ways in and the autoforge's ground; wear,
  grime and debris lie flat on every deck.
- **The roof.** Every hull cell but a breach is marked `RoofKind.HULL`, so the
  whole ship roofs as hull plating (drawn from the ship's painting) whatever
  its walls are. The solid mass is one building (`RuinsRoofStage.roofSolid`)
  that never opens; the deck is split into **one building per compartment**
  (`CrashSiteFinishStage.splitCompartments`), so a unit that boards opens the
  compartment it stands in (`BuildingVisibilityPass`) and the rest of the ship
  stays roofed.
- **The trench** runs off the bow's tip for the patch's length (12 cells and
  more: the gaps between the sections are ploughed too), a furrow 24 cells wide
  at the hull easing to 8 where the hull first touched down and closing in a
  rounded nose, each side wandering on its own slow wave so it never reads as a
  staircase. It wraps the bow's tip where the taper leaves ground open. Ploughed
  earth and rubble, with a lip of trodden thrown-up earth of wandering width and
  scrap on its floor. **Debris** is up to 14
  short runs of hull plate standing beside the trench and round the hull, never
  on a road, building or apron and never touching another wall. A road the hull
  lies across is cut from the road graph; the trench cuts nothing.
- **The debris shower** (`DebrisShower`) is what the hull threw as it broke up:
  great pieces of shell, drive and engine (`basin-ship-scrap`'s), girders, pods,
  bells and plate, shards, craters and scorch (`fallen-hull.shower-*`,
  `crater-*`, `scorch-*` as the art ships them), strewn along the fall line from
  uprange of the trench, past the sections, to ahead of the stern, thinning
  along and across it and reaching into the town and the fields. It falls on the
  finished country, after the mission's structures and terraces are sited. A
  piece that is cover stands in the way with a ring of open ground round it, off
  roads, buildings, water, the hull, trench and aprons, mission places, tactical
  nodes, the landing's field of fire and the claim lattice's grid points; flat
  pieces lie on any open ground, streets and roads included (rubble, not a
  barrier). A few lone great pieces fly well out across the line. Where a great
  or middling piece comes down on a home or a store it **strikes** it instead:
  up to three are knocked down and more roofs holed or burnt, as the map's
  `hull-shower` initial-damage layer, applied when the battle is assembled.
  A strike keeps off buildings a mission anchors to, the landing's field of fire
  and the hull; a knock-down keeps its collapse off fighting positions.
- **The impact zone** (`ImpactZone`) is the ground the hull flattened coming
  in: within 20 cells of the hull or its trench every building is wrecked
  (walls down, roofs fallen in or burnt out), so nothing intact stands against
  the hull or the plough; out to 40 cells the blocks are battered and wrecked in
  patches that grow rarer outward; beyond that only the shower's strikes fall
  (UNTUNED). Open ground near the hull is scarred in scattered patches. It is
  initial damage (`hull-impact-*` layers), applied when the battle is
  assembled, as runs of cells along each row, so it never touches the hull, its
  trench or aprons, standing debris or fixtures, the landing's field of fire, a
  building a mission anchors to or a mission structure. It only takes walls and
  roofs down, so the lift, the shelters, the stake points and the haul roads
  stay as open as they were.
- **The claim lattice** (`ClaimLattice`) is a grid about the hull's middle,
  `SPACING` 40 cells (two stake perimeters of 20, so neighbouring perimeters
  meet; UNTUNED), each point moved to the nearest 5-by-5 of open outdoor ground
  within a quarter spacing or left a hole. **Haul roads** (`HaulRoads`) are one
  shortest-path tree from the hull's aprons, so roads merge into trunks; only
  an established stake's road is worn into the ground.
- **Starting stakes** are the patch's cells, each taking the nearest lattice
  point. `HullSiteCheck` holds the laws on the finished grid (every way in
  open, the forge, halls and aprons reached, a hall frame walking hall to
  hall), and a story map's acceptance fails on any problem the site reports.
- **The rules' reading.** `LongSurveyGround.rulesSite` fills `FallenHullSite`
  from it: the hull and its trench as the root zone's footprint (all three
  sections and the ground between), the forge, the
  bay's apron berth as the mouth, the lattice with `linkCells` a lattice step
  and a quarter (so neighbours that moved to their stake ground still link, and
  a stake's zone, half of it, meets its neighbours'), each haul road from the
  hull outward, the starting stakes and the ways in as egress. The chapter
  supplies the salvage, the walkers out and the store. Acceptance holds that
  every starting stake chains back to the hull.

## Campaign (later, not work)

A fallen hull is meant to become a sector phenomenon like a bloom, once the
story missions have found the feel (owner, 2026-11-10: story missions on a
controlled seed first): a derelict mothership's orbit decays over a settled
world, the hull's claim grows by stakes week to week as a market condition, and
the world's owner calls for help. Its stages, its clock and its interaction with
the event gate are not designed yet.

## Open picks

- `hull-spread-law`: (a, recommended) the hull alone spreads, through its
  autoforge and only from feedstock; site networks keep their fixed numbers.
  (b) No spread: the hull pours out what it carried and stops.
- `stake-look`: (a, recommended) the spire, a square armoured spike that
  opens four petals round its node mast. (b) the tripod, a round core whose
  three legs splay and anchor. The spire is built.

Answered by the owner (2026-11-11), each the recommended option and built as
it stands: `hull-lineage` (a), the hull is an Explorarium survey mothership;
`stripper-hauler` (a), two units, the stripper and the hauler; `autoforge-look`
(c), the ring forge.

## What is built

Everything is in `battle.derelict`, on the recommended option of every pick.
Numbers UNTUNED.

- **The rules.** `Autoforge` (the forge cycle, `CYCLE_SECONDS`; prints the next
  item only with feedstock for it, passes over an item with nowhere to go at no
  cost, prints nothing once put out; breached below `BREACH_SHARE` of whole);
  `BuildQueue` (the fixed program, round and round, read ahead by `peek`; the
  stand-in `survey()` order); `Feedstock` (a store, and the forge's draw over
  the hull's and the linked stakes' stores, all or nothing, the hull's first);
  `ClaimLattice` (links within `linkCells` of a point or of any hull section;
  `nextPlantable`: the open point nearest the plates that links to the
  hull or to an anchor, an established stake whose chain holds, its parent the
  hull where it can be, else the nearest anchor); `SurveyStake` (planted,
  raised, established on `RAISE_SECONDS` and `ESTABLISH_SECONDS`, counting only
  while it builds; a store from established; killed planted it is scrap;
  stranded it keeps its stage for good); `RelayChain` (a stake's chain is cut by
  its own node, any node on the way to the hull, or the forge going out);
  `Salvage` (metal on the claim; a wreck of the watch's cut to nothing is
  stripped, `Wreck.strip`, and a wreck the company scrapped is denied);
  `StripperWork` (nearest cuttable salvage, cut to `CAPACITY`, then wait for a
  hauler); `HaulerWork` (the fullest stripper, then the nearest store whose
  chain holds; destroyed loaded, its load is lost); `StakeTrain` (planted on
  reaching its point, scrap if its hauler dies first, the point open again);
  `HaulRoads` (a walk with both ends near a road follows it, waypoint by
  waypoint; any other goes straight).
- **The workers.** `MechVariant.SECURITY_STRIPPER` (MG-18, medium, unarmed) and
  `SECURITY_HAULER` (MG-30, heavy, unarmed), automated like the rest of the
  derelict line, with `DerelictWalkerFrame.STRIPPER` and `HAULER` on them
  (`worker()`). Both are drawn as the sentinel at their size until the art
  session's looks ship (`stripper-hauler`, answered: two units).

- **The seam from the ground.** `FallenHullSite` is what the crash-site ground
  fills and the battle stands on: the hull's laid box (`Rect`) and its
  sections as they lie (one `Rect` each; a hull in one piece is its own only
  section), the forge's
  cell (the vault, deep in the forge hall), the bay mouth (open ground outside
  the hull where everything printed walks out), the hull zone's perimeter (its
  reach off the sections' plates), the
  lattice points and their link distance, the haul roads (polylines of cells),
  the stakes already standing with their stage (`StartingStake`), the metal on
  the claim (`Pile`: headframes, fences, vehicles, the town's structures), the
  walkers already out (`Posting`: frame and cell), the egress cells the hull's
  walkers hold in the contingency, and the hull store's metal at the start.
  `FallenHullFixtures` (tests) stands one in on open ground meanwhile.
- **The battle.** `FallenHull.install(sim, site, queue, livery)` stands the
  hull's zone (`HULL_ZONE`), keeping the ground within its perimeter of any
  section (`SecurityZone` with keeps; nearest keep first where zones overlap),
  with the autoforge as its node
  and vault (an armoured, unarmed structure at the forge cell), its flock at the
  mouth, its service bay and repair drone; the ground's stakes nearest first,
  each chained through the anchors before it (one the chain cannot reach is
  stranded from the start); the walkers already out, each in the zone keeping
  its post; and the network's `DerelictSecurityDirector`, which runs
  `FallenHull.pulse` each half second before its walkers take their postures.
  Each pulse: a gone forge is put out; a planted stake whose pylon is gone is
  scrap and its point open; `RelayChain` strands what is cut; stakes count
  down; trains walk their haul road out and plant (the escort guards the pylon,
  the carrier goes to work hauling) or are scrapped (the escort goes to the
  frontier); the watch's lying wrecks and the company's and garrison's fallen
  machines become salvage; strippers cut the salvage inside the hull's and
  every linked stake's perimeter, and haulers carry it to the nearest linked
  store (none while the forge is out); then the forge cycle prints, a stake
  as a train to the next plantable point, a walker posted round the newest
  anchor (the frontier), else the mouth. A raised stake is an ordinary
  `SecurityZone` (`zoneOf`: `STAKE_ZONE_BASE` plus its point) with its node,
  its relay to the zone its chain runs through, its docked flock and its repair
  drone, and its escort reposted to it; established, it adds a service bay,
  `DerelictPost.PER_ZONE` folded posts and its store. The forge's breach
  (`Autoforge.BREACH_SHARE`) calls the contingency. The director gained
  `addWalker` (a printed walker joins with its body armed), `repost` and
  `attachHull`; unarmed walkers (tender, stripper, hauler) are walked by a
  withdrawal, since a lance with no gun declines every attack goal
  (`DerelictSecurityDirector.walk`).

Evidence: `AutoforgeTest`, `ClaimLatticeTest`, `SurveyStakeTest`,
`HullWorkersTest`; `sceneEvidence -Pscene=fallen-hull` (train-plants: the
forge prints a stake, its train walks out and plants, and sixty seconds on the
stake rises into a zone linked to the hull's with its node and a flock;
train-scrapped: the hauler killed on the way leaves the stake scrap and the
point open; stranded: an established stake's node destroyed strands it and the
planted stake beyond, which never rises; feedstock: a stripper cuts a pile and a
hauler carries the metal into the hull's store).

Not built yet: the claim-edge pursuit law (walkers keep their zone's perimeter,
as at a site network); a stake's rounds walker on a round; cutting a stripped
structure out of the map (a pile is metal at a cell); the forge's power cut
other than its destruction; the stripper's and hauler's own looks and the
stake's three stages drawn (stand-ins: the sentinel at size, the node's
structure for pylon and node); the briefing and rail reading the build queue
(`BuildQueue.peek`); a seed's real hull site from the ground.

## Boundaries

- Units, their sizes and their doctrine stay in the derelict domain
  (`derelict-lore-guide.md`, `derelict-watch.md`); new units here are chassis of
  the derelict line (`mech-lines.md`, law 1).
- The arc, its world, cast, chapters and seeds: `long-survey-arc.md`.
- The ground (hull, trench, lattice, haul roads): mapgen, through the owned
  story maps (`story-map-pools.md`); built as The ground describes.
