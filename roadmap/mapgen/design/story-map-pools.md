# Story map pools

Status: ACTIVE — every pooled story map (Colony Breach, the Cordon's and the
Heart's, at every density) is one owned seed laid out on the mission's home
palette and skinned for the world it is fought on (`PlanetSkin`); so is every
generic Raid's map, one per Raid site, laid against a reference market
(`raid-mission.md`, The owned map). Conquest and the other generic contracts
stay fully random until they own a map too.

Updated: 2026-11-10 — a sealed layout: on a world nobody lives on in the open the owned seed is laid out with every town sealed. Earlier, 2026-11-10 — one owned map per mission, every world skinned (owner: "All of these should use a single seed, that we own and make good. They should be skinned for the planet and make sense for the planet"); the per-biome entries and their candidates are gone.

Written: 2026-10-23

Earlier, 2026-11-10 — one map per biome. 2026-10-31 — a story map per biome the campaign always plays; a patch may place layout structures by name. 2026-10-28: a Colony Breach patch places the lift, bores and holdouts.

A story mission's map is the generator's, but the mission does not take any
seed the dice offer. It **owns one map**: a seed the team has looked at and
made good, with a small authored **patch**, generated on the mission's **home**
palette. Every world plays that map, and a **planet skin** re-dresses it for
the world. Generation still makes the map every time, so the map rides
generator improvements; the pool decides which map it is.

## Why

A story map's outcome swings on small behaviour changes, and evidence over a
sample of random seeds is a sample that keeps moving. One owned map makes
evidence **complete** (it is the map the player gets) and **comparable** (the
same map, run to run and world to world). It also lets a person own the map:
look at it, name what is good about it, and fix the one thing wrong with it.
The owner, 2026-11-10: "Currently our story missions are the only ones that
play okay", so the authored story maps are what every world plays.

## The pool

One data file per story map, `mod/data/world/pools/<map>.pool.json`
(`StoryMapPools`, installed with the other world catalogs at application
load):

```json
{ "version": 3, "map": "cordon-line", "home": "ARID",
  "entry": { "seed": 3, "note": "rear south; redoubt guns 2-2-2-2",
             "patch": { "pin": [...], "ruin": [...] } } }
```

**Home.** The palette the map is laid out on (`StoryMapPool.layoutFor`): its
generation, acceptance and patch coordinates are the home's. A palette feeds
generation in many places that move the layout (rock chance and its draws,
formations, woods, sludge pools, the desert's green basin), so a world never
generates its own map: it plays the home's layout, skinned. Every pool's home
is ARID today, the ground the Wellhollow campaign plays and its maps were tuned
on.

**The owned seed.** The battle seed itself: the map it names on its home
passes the mission's acceptance on the first generation, with no re-roll. The
note is for people: what the map is and why it is the one.

| Map | Seed | Why this one |
|---|---|---|
| `colony-breach` | 38 | Asharu, the campaign's map; lift, bores and holdouts placed by the owner |
| `heart-burn` (and its town and city) | 2 | the Wellhollow story map The Burn was tuned on |
| `heart-front` | 5 | the Wellhollow story map, its airbases placed |
| `heart-front-town`, `heart-front-city` | 2 | the first that grows two mothers |
| `cordon-line`, `cordon-road`; town; urban | 3, 15; 14, 8; 6, 6 | the ARID entry Wellhollow played on a first attempt: the Cordon pools named no story map, so the map a player met first is kept |

**Density.** The Cordon is laid out at its world's density (rural, town or
urban, from the market size; `cordon-nouns.md`, Density), and the heart is the
same (`heart-map-intent.md`, Density). A density changes the whole map a seed
names, so each density is a story map of its own with its own pool.

**Choosing.** There is nothing to choose: every world, roll and attempt plays
the owned entry (`StoryMapPool.pick`, `story`, `entry`). A fixture that names
the owned seed plays it with its patch; any other seed plays the old unvetted
path, re-rolls and all, laid out on the fixture's own biome, which is how a
replacement is tried before it is owned. Nothing on the campaign path reaches
the unvetted path.

## Planet skin

`PlanetSkin.apply(map, home, world)` re-dresses the finished map in place.
**The layout never moves**: no cell changes whether it can be walked, seen
through or built on (`PlanetSkinTest`). It swaps:

- **Ground.** The home's wild ground and its lawns become the world's own wild
  ground (`SurfacePalette.inlandGround`: grass, sand, stone, snow, hardpan);
  bare earth, stone and snow stay what they are, drawn in the world's material
  (`CellTopology.setGroundPalette`). The home's basin (the desert's green
  middle) goes with it: the world is one surface.
- **Woods.** The same trees and undergrowth on the same cells in the world's
  flora (`Flora`, identical footprints): the home's own on its palette, a living
  world's species, boreal conifers on a frozen world, grey dead scrub where
  nothing lives. Loose plants come off a world where nothing grows; field stones
  take the world's set; cliffs take the world's finish (`TerrainPlan.Finish`).
- **Fields.** Open crops, grain and pasture only where things grow under air
  that lets them: a verdant or desert world with breathable air
  (`PlanetSkin.fieldsOn`). On a frozen, bare-rock, volcanic, irradiated or toxic
  world, or under air that kills, every worked field (`MapResult.fields`: the
  surveyed country's plots, the town's own fields and the lots' kitchen plots)
  is grown under cover: the plot is cleared to the planet's own ground and a
  **covered farm** stands on it (`PlanetSkin.covered`): rows of pressurised
  hydroponic sheds (`hydro-sheds`) along the plot's crop rows, a lane between
  rows and between sheds, each row of long sheds finished with a short one or
  a pump house, and short sheds on a grid on a plot too short for a long one.
  Each roof carries two skylights lit by warm amber grow-light (owner, over
  ImageGen's pink: "Warm amber"). On a dark map the sheds are lamps too
  (`PlanetSkin.growLights`): amber pools down each shed's middle, drawn with no
  fixture, so the roofs glow and spill onto the lanes. Each shed is sunk into
  the ground, only its lit roof ridge above grade and earth banked up all four
  sides (owner: "embed them into the earth"); the bank is painted as shading
  only, so it is snow on a frozen world, crust on a toxic one and scorched soil
  on an irradiated one. In the sim a shed is an earth mound
  (`PlanetSkin.BANK`): every cell stays walkable and see-through and vehicles
  pass, and the bank is light cover (half height 0.18, below a sandbag
  parapet's 0.4) to whoever lies on or beside it. The pump house stands above
  grade and is coverless. Pathing and sight are the home map's. Paved
  hydroponic bays were dropped (owner, 2026-11-10: "Do a bunch of paved beds
  make sense? Why would a planet have those?"), and so were polytunnels
  (owner: "They don't read like hydroponics"); sheds were picked over
  geodesic domes and glass halls (planet-skin-farm).
- **Buildings.** Every town wall in the world's habitat (`HabitatStage.rebuild`,
  `BiomeSkin.habitat`): shielded on an irradiated world, sealed on a toxic or
  airless one (bare rock is airless unless its profile says otherwise),
  insulated on a frozen one.

It does not raise a sealed perimeter round a town, add a drop or grow new
water or woods: each would move the layout; the sealed layout (below) is how a
town is sealed. A drop belongs on a map only where
its terrain explains one (`owned-maps.md`, Relay Nine). Living worlds (jungle,
tundra, ocean) reach a story setup as verdant today, since the setups take the
world's palette alone.

## Sealed layout

A world nobody lives on in the open plays the mission's **sealed layout**: the
same owned seed and patch on the same home palette, generated with every town
sealed (`SealedTown.seal`), then skinned as any world is. Which worlds is the
world's alone (`PlanetSkin.sealsTowns`): wherever its habitat is enclosed, so
irradiated, toxic and airless worlds, and bare rock, which is airless unless its
profile says otherwise. Every zoned place the mission names (its colony, towns,
suburbs, depots and hamlets) is walled where it stands under its own name;
garrisons, compounds, workings and the landing keep their own boundary, and no
mission place is dropped. The owner chose one owned layout per case over a wall
the skin draws on an open town's outline (2026-11-10): a wall changes what can
be walked, so it belongs to the layout. The regeneration check plays every map
on every palette, so a sealed layout is checked wherever it is played.

A sealed wall keeps a lock onto every stretch of open ground it borders
(`SealedTown.groundLocks`), not only where roads cross it: on a city map the
suburbs' wall ran the map's width with no road out to the landing field, and
sealed the company out of the whole map.
Every sealed place is walled as one, so a sealed layout's town plan is laid
with its envelope's ground held back (`mapgen-nouns.md`, Habitat): it is a
different street plan from the open layout's, from the same seed.

`createSnapshots -Psnapshot=planet-skin-look` (`-Dsnapshot.planetSkin.maps=`,
`.worlds=`) frames each mission's map whole under every palette through the
look preset, with close frames on its fields and woods, and on a world that
plays the sealed layout a close frame on a lock.

**Colony Breach.** The colony (`colony-breach`) is generated on its home alone,
never on the market behind it, with no re-roll, then skinned for the world the
campaign gives it; a seed that no longer seats its shelter, lift, bores,
holdouts and opening swarm fails, in the campaign and in the regeneration
check alike. Its entry takes no pin or ruin yet, only the colony's own
placements (The patch, below). The debug stress test and the lower-risk
rescues stay random.

**How the first pools were chosen.** Seeds 2 to 25 screened on every biome
(the Cordon passes on 60 to 72% of them, the Heart on all); for the Cordon the
three with the fewest gunless redoubts, both rear edges represented, and on
the Road a corridor at least 80% on road; for the Heart the first three seeds
that grow two mothers (the seed alone decides two or three), the same three on
every biome.

## Seeds age; the regeneration check

A seed names a map only for one generator version, and mapgen changes
constantly. Two ways to cope:

| | Seed + patch (chosen) | Baked maps |
|---|---|---|
| Generator improvements | arrive on every pooled map | never arrive; each map is a frozen copy |
| Storage | a few numbers | a whole map result per entry, with a format to version |
| A mapgen change that breaks a map | caught by the check below | cannot happen, and neither can a fix |
| Hand-tweaks | small ops that ride the regeneration | edit anything |

The pool keeps seed and patch, and **`gradlew storyMapPools`** regenerates
every pooled seed and fails when one no longer meets its mission's
acceptance. Opt-in and excluded from `test` (it generates whole maps), and it
is what a session runs after a mapgen change that may move a story map. A
failure names the map and the seed and what it now lacks; the fix is a patch,
a swap for another seed, or the mapgen change itself. Writes
`build/reports/story-map-pools/summary.md`. A Cordon row's detail carries
the line's gap readings (`CordonGapLaw`: each neighbouring pair's widest
gun-to-gun step, `!` where it breaks the law, and the towers' own distance)
and its picket nests per course, with how many were added to close the gaps
and how many of those stand at street mouths. When the redoubts went up onto the
ridge's crest, a Cordon map whose redoubt could not stand there stopped being
a Cordon map, and the rural and town pools were re-screened over seeds 1 to
40: a swapped-in seed's note says every redoubt is on the crest.

**Acceptance** is the mission's own re-roll test, so the check and the setup
cannot disagree:

- **Cordon**: a clear landing field; every redoubt walled; the base packed a
  shed and a carrier can drive from its apron to every redoubt; four redoubts
  west to east spanning the map; on the Road, the compound walled and a
  drivable corridor from the rear edge to it; and the gap law holds on every
  neighbouring pair, its picket nests included (`cordon-nouns.md`, Picket
  line). Both vehicle proofs run on the
  edge-aware clearance mask the convoys lay their routes on, so a fence or low
  wall along a cell edge that would stop a hull fails the map rather than
  jamming a carrier or a truck in play.
- **Heart**: the bores seat (`HeartBores.plan`), and nothing the layout laid
  stands on a way in the start-side third (`LaidLayout.onWays`, cells of
  structures and works, and of the farmland's own farms, on a road, farm track
  or lane track, read off at depth 0.34, where the creep has not reached).

Every Heart and Cordon row's detail carries that reading, `on roads N
start-side [piece cells], M deeper [...]`; deeper cells, and every Cordon
cell, are reported without failing the map.

and the battle then stands up on it (the patch applied).

## The patch

The smallest vocabulary that covers what a person needs to fix on a map they
otherwise like. Two ops, each riding an existing seam rather than a new
map-editing path:

- **`pin`** `{place, x, y}` moves a plan precinct's seed (a redoubt, the
  logistics base, the compound) **before generation**, so the map grows round
  the pinned place and every acceptance test sees the result. This is the
  op for "the base sits in the wrong spot" or "redoubt 3 is in the trees".
- **`ruin`** `{radius, points, mode?}` is a stroke of the authored damage
  layer (`mapgen-nouns.md`, Authored damage layers), applied **at battle
  assembly** through the ordinary structural destruction boundary: walls
  breach to rubble, roofs open, edge barriers and props are wrecked. It is the
  op for "clear this cell or edge", "drop this doodad": it clears by
  ruining, which is honest on a bloomed front and leaves no impossible gap. Mode
  is `RUIN` unless it says `BATTERED` or `SCAR`.

A ruin is applied after the setup's map tests run, so it **cannot make a
failing seed pass**: a seed must pass acceptance on its own (with its pins).
Ruin is polish on a good map, not rescue for a bad one. Moving a place is a
pin; there is no op to add a building or road, because a map that needs one is
a map to swap out.

A Colony Breach entry places the mission's own pieces on the generated map
instead (`colony-breach-nouns.md`, The colony), each in cells read off the map:

- **`lift`** `[x, y]`: the evac lift and its cordon, on the clear pickup site
  nearest the cell (within 12 cells).
- **`bores`** `[{x, y, mother?}]`: the first wave in eruption order and exactly
  one mother, each on the doorstep of the place nearest it (within 10 cells) or
  else on the open ground nearest it (within 16).
- **`holdouts`** `[[x, y]]`: in redoubt order, the held building each cell falls
  inside.

They replace the setup's choices and its clearances; a placement that cannot
seat fails the map like any other acceptance.

Any entry may also **place** a mission layout's structures by name
(`MissionLayout.placed`, `mission-layout.md`):

- **`place`** `[{name, kind, x, y, turns?}]`: the structure's footprint
  corner and quarter turns, cells read off the map. A deferred layout piece
  that resolves a structure of that name takes the placed one instead of
  siting it, and whatever it lays round it (an airfield's hamlet) is laid
  round the placed one. A placed structure not laid at its cell fails the map
  in `storyMapPools`. Broad Front's story map places its two allied airbases
  this way (`heart-nouns.md`, Allied airbase). Asharu's ARID entry is placed
this way from the owner's Asharu Placement Board (`tools/picks/pages.md`).

Patches are edited on the workbench's Maps page (`layer-authoring-workbench.md`)
or headless through `map_render` and `map_save`: pins, ruin strokes and
placements on the owned entry's whole-map picture, any world shown through the
planet skin, the mission's acceptance on home (`StoryMapAcceptance`, the same
check `storyMapPools` runs) before every save, and the file written back in its
own form.

## Evidence plays the pool

`commanderEvidence -Pmission=cordon-line`, `cordon-road` and `heart` play
**every pooled seed** by default, side by side, with the mission's checked-in
fixture as the template for everything but the seed. The report gains a
matrix, one row per seed, and each seed keeps its own folder with its trace,
summary and timeline. A balance reading therefore covers every map the player
can get. `-Pfixture=` still plays exactly one fixture, for a candidate or a
question about one map.

## Boundaries

- Conquest, skirmish and every generated contract but the Raid stay fully
  random: their value is variety, and their evidence is a matrix over
  constructed fixtures. The Raid's maps are pooled like a story map's but laid
  against a reference market, not the battle's (`raid-mission.md`, The owned
  map); its acceptance is that a raid stands up with every machine of its
  works reachable.
- A pool is per story map, not per mission: The Burn and Broad Front are
  different forms at different sizes, so each has its own.
- The pool does not hold the setup's tuning, timetables or rosters; those
  stay in the mission's code and story docs.
- The check generates each map on its home with no market behind it. The
  story campaign passes only the world's biome, never the market's profile,
  and the skin moves no cell, so the map it plays is the map the check saw.
