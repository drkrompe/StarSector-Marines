# Story — Voids: depth a map can't walk into

Status: IN PROGRESS

Written: 2026-11-07

Updated: 2026-11-10 — rises: the ravine wall, verticality up, the battle on the floor between cliffs; the gorge's camera height settled at 120 m; the lava rift, a natural style, has a look experiment; the glacial crevasse, ice, has one; the open pit, the first void a generated map lays, cut on a Raid mining site; a natural drop or rise deepens and shallows along its length, and a shallow lava river runs a few metres down; generated outdoor maps cut a natural drop across their country, its style picked by the world's palette, with a dry canyon for barren and desert worlds; the arid canyon has its own sandstone look and wall, the first dry style, and barren rock its dry gorge, and a toxic world its toxic gorge; rises climb in terraced banks; a frozen world's town may shelter in a great crevasse. 2026-11-10: drops and sheltering walls are placed by a map's design, never rolled, and skinned by the palette; the bank is sixteen cells and eight terraces; the crevasse town's houses are cut into its walls.

Read `elevation-nouns.md` (tiers, faces, sight, look) and `surface-relief-nouns.md`
(macro relief, the sun) first.

## What the owner asked for

Urban maps lack verticality: a city's height is only its buildings, every
building is one height, and the generator gives city blocks no plateaus
(`elevation-nouns.md`, Generation). The owner wants to borrow the trick of
Star Wars RTS city maps (2026-11-07): **unwalkable space that implies height**.
The battle stays a flat grid, but the eye reads ground that falls away
several storeys below.

The owner named it **void** and wants it to serve more than cities
(2026-11-07): different **styles** for different needs, such as a crevasse, a
cliff, a deep river, and a skyscraper's edge over a futuristic city below.

The owner's MoonLightEngine technique of raymarching the tiles into a
virtualized 3D space is the fallback ("nuclear option") if the cheaper look
does not sell the depth. This story keeps it to the inside of a void (slice 5).

## Model

A **void** is the inverse of a plateau: ground **below** the map's tier, which
no ground unit stands on. It belongs in the elevation vocabulary next to tier,
face and ramp, and folds into `elevation-nouns.md` when it ships.

- A **void cell** is unwalkable for every ground unit and is permanent natural
  ground (`CellTag.NATURAL_SOLID`): wall damage, demolition, a wall-crushing
  hull and crashes never fill or open it. A void is fixed for the battle like
  every other part of the elevation map.
- A void has a **depth**, a whole number of storeys below the tier around it
  (the tier rise, 4 m). Depth is read only by the sun and the look. To the
  battle every void is simply a drop. How deep each style should be is not
  decided: the owner will feel it out from renders (2026-11-07), so depth
  stays a dial until then.
- A natural void's or rise's depth may **vary along it** (owner, 2026-11-09:
  "Anything stopping us from varying the depth of the gorges"): **depth
  marks** (`NaturalVoid.DepthMark`, `NaturalRise.HeightMark`) say how deep
  the floor lies, or how high the top stands, at places along it, and between
  them the depth blends smoothly. Every ledge keeps its share of the floor's
  depth wherever it lies, so a gorge that shallows keeps its ledges, closer
  together. Only the depth changes: where the lip, the ledges and the floor
  lie across the void is the steps' insets, so a mark never moves an edge
  the battle reads. An open pit ignores marks: its benches are cut level and
  its haul road is laid from their depths. A void without marks is as deep
  everywhere as its steps.
- A void's **edge** is its band of cells along walkable ground. It is drawn
  as a wall going down. To the battle it is the void itself, not a face, so it
  needs no width of its own.
- A **bridge** is walkable ground across a void: ordinary ground or deck cells
  at the tier of the ground they join. Nothing walks under it, so "one cell, one
  level" holds. A bridge's width decides what crosses it, exactly as a ramp's
  does: infantry need one cell, a vehicle or mech needs its clearance.
- A **rise** is the void turned up: unwalkable ground **above** the tier, which
  no ground unit stands on, a ravine's walls rising over the floor the battle
  is fought on. Its **height** is a whole number of metres above the tier
  around it, read only by the look; its **foot** is its band along walkable
  ground, drawn as a wall going up. To the battle a rise is high ground
  without a top anyone reaches: elevation's inaccessible raised mass
  (`elevation-nouns.md`, Purpose), not a plateau, so it needs no face width
  and no ramp. It is natural solid, unwalkable, and **blocks sight** as higher
  ground does; its foot gives the cover a cliff foot gives. That sight rule is
  the one battle difference between a rise and a void, and the look tells
  their cells apart by it.
- Roads over roads (overpasses) are **not** voids and are out of scope. They
  need two walkable levels on one cell, which this model forbids.

### Void styles

A **void style** says what a void looks like and where a generator puts it.
Every style plays the same in the battle. A style is presentation and
generation, never a battle rule, as an emplacement style is
(`ground-turret-tiers.md`).

| Style | Where | Edge | Floor | Bridge |
| --- | --- | --- | --- | --- |
| **Glacial crevasse** | Frozen worlds: a split in the ice sheet under a snowfield | Blue-white ice in strata with meltwater streaks, snow on the ledges, a snow cornice | Old dark ice with frozen meltwater pools, in a deep blue and a cold mist | A snow bridge or a laid plank span |
| **Arid canyon** | Desert worlds, where a living world lays a gorge river: a canyon cut through a sand plain | Banded red and ochre sandstone in terraces, sand lying on the ledges | A dry wash: pale sand braided between gravel bars, boulders fallen from the walls, a dust haze | A stone bridge or a laid plank span |
| **Dry gorge** | Barren rock worlds, where a living world lays a gorge river: a gorge cut through a stone plain | Grey regolith and bedrock strata, grit on the ledges | Dust and rubble thick with fallen basalt blocks; no water, no haze | A rock bridge or a laid plank span |
| **Toxic gorge** | Toxic worlds, where a living world lays a gorge river | Corroded olive rock in strata, sulphur crusted on the ledges, weeping stains | Acid sludge creeping down the gorge, crust along the walls, bubbles, yellow-green fumes | A rock bridge or a laid plank span |
| **Sea cliff** | A map's edge or a bluff: the land ends | Sheer rock or a cut quay wall | Surf and spray far below | None, or a causeway |
| **Gorge river** | A river cut deep, in place of a brook | Steep banks, rock and roots | Running water far below | A footbridge or road bridge |
| **Lava rift** | Molten and volcanic worlds: a rift split open in a basalt plain | Columnar basalt lit orange from below, a scorched lip | Molten rock crusting as it flows, glowing through its cracks | A stone road bridge |
| **Foundry melt** | A forge works' foundry in a ruins site's plant (`ruins-expedition.md`): pour channels and casting beds cut in a hall floor, 2 m down | Firebrick walls under a laid cast-steel kerb strip | Molten metal kept hot, no crust | A grate catwalk, the drop's own crossing |
| **Bore shaft** | A mantle bore's shaft hall in a ruins site's plant (`ruins-expedition.md`): a shaft sunk in the hall floor under its headframe, 30 m down | Bedrock strata under a laid cast collar strip, drawn as the dry gorge's | Rubble | None: the headframe spans it |
| **Open pit** | A mine's head works: an open-cut working beside the shafts | Blasted bench faces with drill traces, benches stepping down evenly, a haul road spiralling down them | Wet ore rubble and a still sump | None; ore conveyors on gantries climb out to the works |
| **City drop** | City blocks: a skyscraper's edge, a light well, the gap between arcology blocks | Poured concrete, ribbed steel, pipe runs, lit windows, a railing | A futuristic city far below: lights, haze, traffic | A deck span or skybridge |

A **sea cliff** is not an elevation cliff face. A face holds a plateau up
between two walkable tiers. A sea cliff drops from walkable ground to nothing
anyone stands on.

A **gorge river** is not the map's water. Water lies level with its bank
and its shore is walkable. A gorge river is a void whose floor is drawn as
water. Both are see-through and unwalkable, so they play alike.

### Battle

Tactically a void is the inverse of a cliff. A cliff blocks movement and
blocks sight up it; a void blocks movement and **blocks no sight**: a gap you
can shoot across but not walk across. It reuses water's tactical profile
(`CellTag.SEE_THROUGH`, `CellTag.NO_EDGE_COVER`), so most of the battle already
honours it:

- **Movement.** Every ground authority already refuses an unwalkable cell
  (`elevation-nouns.md`, Movement). Routes go round, or over a bridge. The swarm
  does not cross a void and, like every ground unit, is never in one.
- **Sight and direct fire** pass over a void as they pass over water. The fog,
  AI perception and the fire ray agree because they already share the
  see-through rule.
- **Cover.** No edge gives any: a void is not a silhouette. A city drop's
  railing is drawn only, not cover (owner, 2026-11-07: railings visual for
  now).
- **Indirect fire and grenades** that land in a void are lost: no ground to
  burst on and nobody there. A blast on the ground beside a void spreads over
  it as over water.
- **Fire** does not spread into or across a void. It is not fuel.
- **Aircraft** fly over voids. A void cell is never a landing, drop or crash
  cell (a crash over a void lands at the nearest ground on its track, or is
  lost; settle in slice 1).
- **Nothing falls in.** No knockback, rout or vehicle slide puts a body into a
  void. Ground units never enter one, so there is nothing to fall.

### Look

The void is read from the ground, never from a UI mark (`elevation-nouns.md`,
Laws):

- **Depth in the relief.** A void cell stands its depth below the datum in the
  macro-relief field (`MacroReliefField`), so the battle's sun casts the near
  edge down into the drop with no shader change. The relief encoding holds 4 m
  below the datum today (`GroundHeightPass.MACRO_DATUM`): one storey. Deeper
  voids need that headroom raised, which costs height precision above the
  datum, or a depth clamp, under which the shadow still reads.
- **Baked edges.** The edge's wall going down is baked once per battle from the
  layout's own fields, in the way `CliffArtLayer` bakes an escarpment, in the
  void's style.
- **The floor.** The bottom is a deep layer in the void's style, drawn with
  more ground-plane parallax the deeper it is, so a drop shifts against the
  ground at its edge as the camera moves. Units never draw in a void and are
  never displaced (`surface-relief-nouns.md`, Laws).

## Slices

1. **Grid and battle.** The elevation map records void cells, their depth and
   their style. The brush stamps them with water's tactical tags and natural
   solid. Settle crashes and air drops over a void. A behaviour scene,
   `across-the-gap`: two squads either side of a void see and fire on each other
   while their routes go round by a bridge; a control with the void filled in
   as ground.
2. **Generation.** A **void** stroke in the elevation plan, naming its style,
   preflighted and proved like a plateau: it never covers roads, buildings,
   doorways, fixtures or reserved cells, and every place a host requires stays
   connected, through bridges where it must. `-Dbattle.mapgen.voids=N` asks on
   every map, for evidence, as `-Dbattle.mapgen.plateaus` does. Run
   `storyMapPools` after it. Each style's generator follows the table: city
   drops in city blocks, bridged where streets cross; crevasses in ice and rock
   country; a gorge river as a brook's alternative; a sea cliff along a map's
   edge.
3. **Look, first style.** The city drop, on the owner's first target: a **sky
   port**, a battle fought on the roof deck of a megastructure over an endless
   city (2026-11-07). Its look experiment is built ahead of slices 1 and 2,
   on a hand-made deck (`createSnapshots -Psnapshot=sky-port-void-look`).
   - The map carries a `VoidBackdrop` (a seamless city plate, its depth in
     metres, the metres one copy covers, the haze colour, lit or dark
     windows); `VoidBackdropRenderSystem` draws it first in the ground layer,
     so the tiles cover it everywhere but the void cells, which draw nothing.
   - The view is a pinhole camera looking straight down from a height equal
     to the view's width, so a point `d` metres down stands at
     `h / (h + d)` of its distance from the view's centre. The city moves
     slower than the deck when panning and grows less when zooming, with no
     shader change.
   - Every edge of the deck has a wall going down to the city, drawn storey
     by storey at each storey's own scale, with window bands and pilasters
     running toward the view's centre. A wall that faces away projects in
     under the deck and is covered by it; faces are drawn far to near, so a
     nearer face covers one behind it at a re-entrant corner.
   - The structure casts on the city: its footprint swept from straight below
     to the sun's reach, so the shadow is the whole tower's.
   - A railing along every edge, drawn only.
   - Depth here is the structure's height, not a storey or three: 150 m,
     400 m and 1 km are on the voids picks page, the plate scaled with depth
     so its blocks stay city-sized. Day and dusk plates are
     `art-source/voids/sky-port/`.
   The owner picked 400 m and the hazy day plate, and holds the idea but
   wants more work on the city's look and feel (2026-11-07). Left: the
   city's look (below); the walls' look past placeholder bands; negative
   relief in the composite; slices 1 and 2 for generated maps.

   **The city's look.** One flat plate reads as wallpaper: it has no volume
   of its own, and its tiling shows. Candidates, cheapest first:
   - **Neighbouring towers**: other megastructures' roofs at their own depths
     below the deck (60 m, 150 m, 250 m), each with walls down to the city
     drawn as the deck's are, so the space between the deck and the city
     has things in it at several depths.
   - **Layered city**: the plate split into a far floor and a nearer layer of
     tower tops with transparent streets between them, each at its own
     depth, so the city itself has parallax.
   - **Air between**: haze or cloud banks and streams of air traffic at
     depths between the deck and the city, drifting with the battle's
     time.
   - **Less repetition**: a larger plate, or several plates mixed, so the
     tiling does not show at 400 m.

   **The megastructure's look.** A sheer box with window bands reads as a
   skyscraper, not a sky port. The owner imagines something more Blade
   Runner or Star Wars (2026-11-07): an arcology, not an office tower.
   The wall drawing already projects any profile storey by storey, so the
   structure can change shape as it falls:
   - **Setbacks and flare**: the walls step outward in terraces or flare
     toward the base, ziggurat-like, so lower tiers stand out past the deck
     and are seen from above with their own roofs, lights and machinery.
   - **Buttresses and fins**: great ribs down the faces, and cantilevered
     landing platforms and docking arms at lower levels, with beacons.
   - **Surface**: dense machinery, vents and lit strips rather than office
     window bands; light pools and haze clinging to the lower tiers.
   From the concept pass (`art-source/voids/sky-port/concepts/`) the owner
   picked the **industrial hive spire** for this first pass, and expects
   factions to mix in the megacity canyon and spire styles later
   (2026-11-07). The owner sees the sky port as a large, almost stepped pyramid
   with layers and entrance points feeding different traffic into central
   freight corridors. The first pass draws that:
   - The `VoidBackdrop` names its **tiers**: each terrace's depth and how far
     it reaches past the deck's edge. A level is the deck's footprint grown
     by its outset; levels are drawn from the deepest up, walls (far faces
     first) then the roof over them, so a face turned away is covered.
   - Walls carry a seamless industrial facade and terraces a seamless roof,
     both ImageGen (`art-source/voids/sky-port/industrial/`), with the hive
     city as the ground; haze thickens with depth over each.
   - **Freight bays**: dark mouths, lit at the sill with beacons at the
     corners, along the foot of every wall that stands on a terrace, each
     feeding a lane with guide lights across the terrace to its edge, where
     traffic would come in.
   - The tiers above shade each terrace, and every wall's foot darkens the
     roof it stands on.
   - The camera's height is a map's own (`eyeMeters`), fixed in metres at
     every zoom: zooming is a lens, not a dolly. Tied to the view's width it
     plunged as the view closed in, so at play zoom the canyons were all
     wall (owner, 2026-11-09). The cluster stands it at 240 m.
   Seen straight down, a stepped pyramid is mostly its terraces' roofs: its
   walls show only past the edge of the level the camera is over, so
   scale and movement carry the depth:
   - **Traffic** of vanilla hulls (the owner's suggestion: Hounds,
     aeroshuttles and the like; four in five fly the backdrop's first two
     hulls, the shuttle and the Hound), planned by `VoidTraffic`: smooth paths
     through control points, each flown now and then on an irregular
     schedule (owner, 2026-11-09: too much, too fast, too close, straight
     lines in a repeated pattern). Whether a slot is flown, when it sets
     off, its speed and its weave off the line all come from a hash of the
     slot, so nothing repeats; a craft's place is a function of the clock
     alone. Bays take arrivals swooping in from one side and slowing to the
     mouth, and departures gathering speed away to the other; street
     routes keep right, and most turn at a crossing onto the street
     across, easing to its depth through the turn. Craft move on the
     presentation clock (the wall clock in the game, which passes none),
     about 9 m a second down the streets, are drawn in the painter's order
     of their depth, fade into the haze, and show an engine glow.
   - **Parked freighters** stand on the terraces between the bays, on their
     own shadows.
   - Hulls below the surface are sized to the structure (`craftMetersPerPx`,
     0.09 m a pixel in the cluster (owner, 2026-11-09: smaller), not to the battle's own craft,
     which are drawn small to sit beside infantry; the bays and lanes are
     sized for them.
   - **The deck surface** is the sky port's own tileset (`sky-port-deck`),
     set as surface overrides: heavy 4 m deck plates in the terrace roof's
     style, with drainage grilles and hatch covers, laid as a 12x12-cell
     pattern so a plate spans three cells and its seams and bolts read at
     battle zoom; landing pads of smooth heat plate (an 8x8-cell pattern)
     inside a hatched keep-clear rim; the marked apron for freight lanes; plant
     blocks in dark concrete on staging deck. One-cell plates were tried
     first: at battle zoom their seams vanished and the tread aliased.
   The owner turned from one roof to a **tower cluster** (2026-11-09): a
   crowded tower space with walkways, so a map can start on one tower and
   move to the next. It needs no battle rule: tower roofs are surface, the
   gaps are void, and walkways are bridges.
   - **The station** (owner, 2026-11-09: towers spread so far apart it was
     unclear why a city would build them so; take modern train stations
     for a model). The map grew to 360 by 240 cells and the cluster became
     one terminus: a landing deck on the map's west edge, where the company
     lands; a wide and a narrow viaduct climbing to the concourse, the head
     hall, a tier up; five platforms running east from it, each reached by
     a stair bridge down; and staggered footbridges across the track
     canyons between the platforms. Craft fly into the canyons to berth at
     the platforms' heads, as trains run into a terminus
     (`VoidBackdrop.slips`), slowing in and gathering speed out. A mission
     may **call** a craft to a slip's berth for a set time of the battle
     (`VoidBackdrop.Call`): the renderer flies it in on the battle's own
     clock, stands it at the berth while it is due and flies it out, and the
     slip carries no other traffic (`sky-port-heist.md`).
   - **Towers and walkways by shape.** The renderer opens the surface by a
     walkway's width (7 cells): whatever survives stands on a tower, the
     pieces on one tier together one structure, so where their terraces
     grow into each other they join in one podium; the
     narrower ground left over is a walkway, drawn as a deck on braced
     girders 2.5 m deep with the drop under it. The map marks neither; a
     generator lays ground and the look follows.
   - **Neighbouring towers** (`VoidBackdrop.neighbours`): rectangles with a
     roof depth, stepping down in the backdrop's tiers deeper than their
     roof, so the drop between the walkable roofs is crowded at several
     depths. Each roof is shaded by everything standing higher.
   - **The city** round the cluster is a street grid (owner, 2026-11-09:
     the hand-placed towers were messy and without reason): 60 m plots less
     a 24 m street, each trimmed to keep a street's width from every roof
     the battle stands on, roofs deeper the farther from the port. Its
     towers take two bold setbacks rather than many slight ones, which read
     as slivers. **Skyways** (`VoidBackdrop.skyways`) fly down the streets
     clear of the port, one to a street, 95 to 160 m down its canyon above
     the deep terraces that narrow it, each street at its own depth and
     the cross streets at others; marker lights run a chase down each
     lane's edges.
   - **Dusk and light** (owner, 2026-11-09: a sickly grey city devoid of
     light). The drop fades into a deep slate blue, not grey, and the
     lights already painted in the facade, roof and ground art (windows,
     lit catwalks, lamps, red beacons) are lifted out as light masks
     (`*-glow.png`, derived by `derive_glow.py` under
     `art-source/voids/sky-port/industrial/`) laid over each surface after
     its haze, so they shine through it. Every roof's edge carries a warm
     strip and lamps, a structure's crown red beacons at its corners
     blinking out of step, and each wall standing on a terrace a warm
     floodlight thrown up from its foot. Unlit metal takes a cool ambient
     tint against the lights' warmth. All of it is the backdrop's
     `VoidBackdrop.Atmosphere`; a backdrop without one draws as before.
   - **Fog** banks at two decks (165 and 300 m in the cluster): soft puffs
     from vanilla's fog sheet on a drifting grid, thick where a slow noise
     says so and clear between, drawn in the painter's order at their
     depth so the towers rise out of them. The shallow deck is cool under
     the sky; the deep one warm, lit by the city below, and thinner, so its
     lights show through.
   - **Steam, searchlights and running lights** (owner, 2026-11-09: more
     effects). Vents on the towers' own roofs breathe steam: puffs from the
     fog sheet leaving the roof one after another, rising, swelling, thinning
     and leaning with the wind, whiter than the fog banks so they read as
     columns. Searchlights stand at some corners of the playing surface,
     their beams swinging slowly out over the drop: a chain of soft lights
     widening and fading along the throw, drawn additively, since light
     composited over grey fog does not brighten it; the surface's tiles cut
     a beam off where it crosses the deck. Lamps on the city's deep crowns
     were tried first; the nearer towers' walls hid their beams. Every craft
     carries a red and a green light at its widest and a white strobe at
     its tail, and its engine glow is round. Steam vents on the terraces were
     tried and dropped: the fog banks drowned them.
   - **Rounded corners** (owner, 2026-11-09: the buildings read as cubes).
     Each structure's roof takes a corner radius hashed from its place
     (square, 6, 10 or 14 m; a plain sum of its position put every tower of
     the street grid on one radius once the map grew), and each terrace below rounds on the same centres, its radius
     grown by its outset, so every step is a ring of even width; the playing
     surface's own corners stay as the map lays them and only its terraces
     round. A rounded corner's wall is facets textured corner to corner; its
     roof is the ring between its arc and the arc above.
   - **Painter's order in depth slices.** Ordering whole levels by their
     roofs let a deeper tower's walls cut through a nearer one up close.
     Every wall band (8 m), roof, freight bay and craft is drawn by its own
     depth, deepest first and far faces first within a slice, so a tower's
     upper walls cover a deeper roof and its lower walls go behind it. Each
     band is drawn in its true slanted shape with an opaque underlay, the
     facade cut to fit inside it, so walls neither step nor show through.
   - Roofs below the surface carry a dark veil as well as haze, so no roof
     in the drop reads as walkable; freight bays open only onto terraces at
     least 8 m deep, and freighters park only on ones at least 20 m deep,
     which keeps a cluster's shallow setbacks clear of traffic.
   - The look suite's default layout is the station (`SkyPortClusterSetup`)
     in the city.
     `-Dsnapshot.skyPort.layout=deck` keeps the single roof deck;
     `-Dsnapshot.skyPort.eyes=120,240,400` compares camera heights.
   - The station ships as a diagnostic battle (`SkyPortClusterSetup`): **Sky
     Port Station** on the debug missions' Diagnostics tab, and
     `playBattle -Pbattle=sky-port`. Two squads land on the landing deck; no
     enemy and no end. The look suite and the scene lay the same ground.
   - **A raised concourse.** The concourse stands a tier (4 m) above the
     rest, written straight into the elevation map (`ElevationMap.of`)
     rather than stamped by the brush: the drop round every deck is void, so
     no cliff face is needed, and the only walkable cells of different tiers
     that meet are a ramp's head and the concourse. The last six cells of
     each viaduct and stair bridge are the ramps, the only ways up, and the
     concourse's holders see down across the drop under the skyline rule.
     `BuiltStairRenderSystem` draws
     a ramp laid on built ground as treads; the void renderer stands a
     raised tower's roof that much higher, so its walls start above the
     surface.
   - **Walkway routing** is proved by the `walkway-crossing` behaviour scene
     (`sceneEvidence -Pscene=walkway-crossing`) on the same station: a rifle
     squad sent from the landing deck to the concourse's north end takes the
     wide viaduct and not the narrow one; from one platform to the next it
     crosses the footbridge and never goes up to the concourse, and with
     that footbridge cut it goes up a stair bridge, across the concourse and
     down another; in every loop no one stands on a void cell, and no one
     stands on the concourse without having climbed a ramp. The lone roof in
     the map's far corner holds the scene's other side.
   Left: **U-shaped stairs** (owner, 2026-11-09): two ramps joined by a
   landing, turning back on themselves, to read as a floor transition
   inside a building though cells never stack; craft in the slips standing
   at their berths a while rather than turning straight round; towers more
   than a tier up
   (chained ramps, or lifts); the walkway's own surface past the ship-bay grate; cranes and
   gantries. The play area's size and chokepoints will
   change (owner, 2026-11-07); the structures follow whatever footprint the
   surface has.
4. **Look, the natural styles.** Crevasse, gorge river, canyon and sea cliff,
   each shown to the owner. The gorge river is first; its look experiment is
   built on a hand-laid meadow (`createSnapshots -Psnapshot=gorge-void-look`).

   **Beside the city drop, not inside it.** A natural drop differs from the
   city drop in every part the city's drawing is built from. Its edge is an
   eroded line that wanders off the cells, not runs along them; its walls are
   rock strata stepping down in ledges, not facades over terraces grown from a
   footprint; its floor is water, scree, dark ice or surf, not a city plate;
   it has no towers, walkways, traffic or lit windows. So a map carries
   `NaturalVoid`s instead of a `VoidBackdrop` (never both), drawn by
   `NaturalVoidRenderSystem` beside `VoidBackdropRenderSystem`. A map may
   carry several drops, each owning its own void cells
   (`CellTopology.addNaturalVoid`, `dropAt`) and drawn by its own instance
   from them alone: an outdoor drop across the open country, at most one,
   and a drop cut indoors (`NaturalVoid.indoor`, a foundry's melt) beside it. What they
   share is the camera and the rules, not code: the pinhole eye in metres
   above the surface, painter's order by depth, the battle's sun, and the
   ground tiles drawn after, covering everything but the void cells. Neither
   changes the other's pictures.

   **The natural drop's shape** (`NaturalVoidField`), worked out once per map
   and sun:
   - **Distance in.** Every point of the void has a distance in cells to the
     nearest ground that stands. The `NaturalVoid` names its **steps**, each
     a depth and an inset; a step begins where the distance, roughened by
     that step's own slow and fine noise, passes its inset. So the edge and
     every ledge wander independently of the cells and of each other, a ledge
     may pinch out where two walls meet, and the drop narrows as it falls.
     The steps nest, so the drop never rises again. The last step is the
     floor. The distance is signed and smoothed over a cell or so, so no
     line follows the cells' stair steps.
   - **The lip** is the strip between the smoothed edge and the first wall:
     rock and earth at the surface. It **frays** a little way onto the
     walkable cells beside the drop, drawn over the ground tiles: opaque to
     a wandering line just past the cells' edge, so the stair-step corners
     of the ground are covered, then breaking into clumps in the turf.
     What lies deeper under that strip of ground is drawn too, so it shows
     past the edge once the camera scales it toward the view's centre.
   - **Walls** stand on each step's line, the quad between the line at its
     top's scale and at its foot's, the strata image laid along them,
     repeating down the face in metres (`gorge-wall.png`, ImageGen,
     `art-source/voids/gorge/`). **Ledges** are flat at their depth: rock,
     moss and fern, pale scree where a ledge breaks over the face below.
   - **Light.** Each piece of ledge, floor and face is lit once: the sun if
     its march toward the sun clears the rock and the bridges, and how much
     sky the walls round it leave open, so a narrow floor stands dim and a
     face turned from the sun is in its own shade. Depth adds a cool tint.
   - **Bridges by shape**, as the city's walkways: ground narrower than
     seven cells with the drop on two opposite sides, joining wider ground at
     two ends a span apart, is a built span; a spur or notch of the bank,
     joined along one side only, stays ground. The drop runs on under a
     span, it is a slab to the sun, and its deck edge shows over the drop.
     The map marks neither.
   - **Off the map.** The drop is worked out a margin past the map's edges,
     the edge cells carried outward, so a drop that runs off the map runs on
     rather than ending in a wall.

   **Gorge river** (first pass): a lip, two ledges and the water 50 m down;
   a meadow's grass to the edge; a timber footbridge and a stone road bridge; the river runs along the gorge,
   pattern drifting downstream and fading into the next every few seconds,
   white along the walls and where the stream breaks, the sun glinting where
   it reaches. The suite renders camera heights of 120 m and 160 m (the
   city's 240 m makes a 50 m drop read as a ditch). The owner called the
   first pass good and named no height, so 120 m is the working camera for
   natural styles, accepted by default (2026-11-09). Left: the owner's pick
   of depth; a mist
   over the water; boulders and fallen trees in the stream; trees and roots
   hanging over the lip; which way the river runs on a map that names it
   (now it runs toward the map's east or south).

   **Rises: verticality up** (owner, 2026-11-09: "keep working with this for
   verticality up as well", the player on the bottom of a ravine). A map
   carries a `NaturalRise` beside its `NaturalVoid`: the same steps with
   heights for depths, drawn by a second `NaturalVoidRenderSystem` from the
   same field (`NaturalVoidField` with a rising shape), not a new renderer.
   The look suite is `createSnapshots -Psnapshot=ravine-rise-look`: a ravine
   floor between cliffs, a side canyon and a rock stack standing in the floor.
   - **The camera turned over.** Higher is nearer the eye, so a ledge
     `h` metres up stands at `eye / (eye - h)` of its distance from the
     view's centre: a rise's walls lean **out** from the centre, its rim
     slides outward faster than the floor as the camera pans, and the eye
     must stand above the top. The rise is drawn lowest first: its foot,
     each face then the ledge on it, the top last. A face turned from the
     camera is never drawn; the faces before it and the top cover where it
     would be.
   - **Never over a unit.** A rise is drawn in the ground layer after the
     tiles and before every unit. So it is seen whole where it leans out past
     its own cells (a stack's top seen from beside it, a mesa from above),
     and a unit at the foot of a wall, or standing where a top leans over the
     floor, is always drawn over the rock: the picture never hides or shows a
     unit the battle sees otherwise (Laws). Clipping a rise to its own cells,
     as the tiles clip a drop, was tried first: a stack's top leans out of
     its footprint and was cut away, leaving a broken ring of wall.
   - **The rim and the top.** The steps nest as a drop's do, so a rise never
     overhangs: a lip that juts is drawn by its ledge and its face's shade,
     not by rock over air. The top is the plateau beyond the rim: turf with
     clumps of wood, thickest a little back, bare rock breaking through along
     the rim. The foot is talus, fraying onto the floor over the tiles as a
     drop's lip frays onto the bank.
   - **Sun.** Faces and ledges are lit as a drop's are, a face's sky taken
     just out from it at each height. The rise casts on the floor: its shadow
     is worked out on the walkable ground round its foot, as far as its height
     throws it, and laid over the tiles under the units, so a narrow ravine's
     floor lies in its walls' shade but for where the sun runs along it.
   - Narrow floor between two rises is a pass, never a bridge.
   - **A bank, not a cliff** (owner, 2026-11-09: "widen out the hills ...
     to make sure the player has good views of the action at the bottom ...
     it doesn't need to be a sheer drop"). A rise climbs in a terraced bank
     (`NaturalRise.bank`): eight even terraces to its top, the top standing
     sixteen cells back from the foot (the owner's pick of three, 2026-11-10;
     ten and six, and six and four, were the others). Each face is short, so it leans out little,
     and the high ground stands well back from the floor, so the camera sees
     the fighting at the foot rather than the lip of a wall leaning over it.
     The first pass's cliffs (two ledges and the top 1.6 cells back) leaned
     over much of a narrow floor; `-Dsnapshot.ravine.sheer=true` still draws
     them for comparison. Drops keep their sheer walls: the player looks
     down into them, not out of them.

   **The crevasse town** (owner, 2026-11-09: "a plausible 'habitable' region
   for cold planets ... a settlement might have built itself into a massive
   crevasse to shelter from the planet's winds / storms"). A frozen world's
   town on the floor of a great crevasse, the battle fought among its
   streets, the crevasse's walls rising along both sides. Look suite:
   `createSnapshots -Psnapshot=crevasse-town-look` (an Assault on a remote
   garrison and on a town, frozen, and the same town verdant).
   - **The ice wall.** A rise of style `ICE_WALL`: the terraced bank in the
     crevasse's ice (the glacial crevasse's wall image), snow drifted on every
     terrace, the top a wind-scoured snowfield with blue ice blown bare along
     its rim (`IceVoidLook.snowfield`). Its shadow on the snow floor is the
     sky's blue, lighter than on rock. The gale streams snow off every lip it
     leaves for the crevasse, high over the floor and settling only a little
     (`GlacialVoidRenderSystem`, rising); on the floor no snow drives: the
     town lies out of the wind. `-Dsnapshot.ravine.ice=true` draws the
     ravine suite in ice.
   - **Placed by the map's design** (see placed by design, below). A map
     whose design shelters its town (`Landform.Shelter`) holds two bands of
     relief `SHELTER_WALL` along a pair of opposite edges before anything is
     laid (`CrevasseTown.shelter`): along the battle's axis, so the attack
     runs down the floor rather than over a wall. The palette skins the
     walls: ice on a frozen world, the ravine's rock elsewhere. Any world may
     stand a sheltered town (the owner's pick, 2026-11-10). Held ground, as a rim round a settlement
     is (`Landscape.held`): the town, its roads and its fields grow on the
     floor between them. The ordinary settlement and precinct programs lay
     the town; nothing new builds it, and a frozen world's own building
     style (the per-palette settlement adaptation, FROZEN: insulated, compact,
     heated blocks) is what it builds.
   - **The walls raised.** The landscape pass raises those bands as the ice
     rise, never as hills (`CrevasseShelter`): in from each edge about 17
     cells, wandering, on the open ground the town left there (yards and
     verges as well as wild ground, never a building, door, fixture or road),
     notched by a pass wherever the town, the landing, a spawn or a route
     reaches the edge. Proved as a drop is: every stretch of walkable ground
     connected before stays connected, dead ground behind a wall (600 cells
     or fewer) is taken into it, and anything else refuses the walls; a map
     whose walls would stand along less than half of their edges keeps none.
     A crevasse town lays no other drop. The walls run about 20 cells in, so
     the sixteen-cell bank has its terraces.
   - **Houses cut into the walls** (slice 2). Along each wall's foot the
     town builds terrace rows and blocks (`BuildingStamp.TERRACE_ROW`,
     `TERRACE_BLOCK`), each one's blind back to the bank and its doors and
     windows facing the floor (`CrevasseTerraces`). Its back is set at the
     bank's shallowest foot under it, and where the foot wanders deeper it is
     dug into the bank, so it stands half in the wall. Never in a pass, never
     on a road, yard or anything reserved, an alley between neighbours, and
     proved as the walls are. The houses come after the town was finalized,
     so the landscape pass finishes them itself: their walls' hit points and
     tags, and their entries in the town's buildings, numbered on so no
     earlier building's id moves (`BuildingFloodFill.populate`).
   **Buildings in depth** (owner, 2026-11-10: "a similar trick for walls to
   get that partially 3d look"; kept for every building). The rise's pinhole
   camera turned on buildings (`BuildingFaces`): each building's roof is
   lifted to its eave at the height of what it was built as
   (`BuildingFaces.heightClass`, the one place it is decided; an author's
   height from the map patch would go there): sheds, huts and pillboxes a low
   3.2 m storey; homes one to three storeys, mostly two; stores one, shops two;
   works one tall bay, the biggest tall works at 15 m; civic halls two or
   three; a sealed town's blocks a storey higher; a tower's room a tower. Each
   building's own number steps a street up and down (UNTUNED). It is scaled about the view's centre as a rise's top is,
   and each wall turned to the camera is a face from its foot to the eave,
   tinted from the building's own and lit by its side. A wall turned away lies under its own roof. All faces
   are drawn before all roofs. A face is built on the outline of the roof as
   drawn (its eaves stop short of each outside cell, `RoofCover.rects`), never
   on its cells, so its top is the roof's edge at any view; the first pass
   used the cells and its walls slid against the roofs as the view panned.
   The resident roof mesh is baked by storey class and each class drawn at its
   own lift, so the stood-up roofs cost what flat ones did: on
   `renderEvidence -PrenderMaps=stress` the roof layer is about 0.5 ms at
   whole-map framing (0.17 flat), and about 0.3 ms on the 280x168 control's
   compound view full of buildings. Look suite:
   `createSnapshots -Psnapshot=building-faces-look`.
   Freestanding walls (wall cells no building owns) stand up the same way
   (`FreestandingWalls`): each connected run stands at its kind's height, read
   from its surface: a sealed town's perimeter 7 m, an installation's 5 m, a
   blast wall or a fort's works 3.5 m, a compound's masonry or an authored wall
   2.4 m, an olive garden wall 1.6 m; a ruin's stub, or a sealed shell too short
   to be a perimeter, no higher than a compound wall (UNTUNED). Faces turned to
   the view's centre run to a cap of its cells at that lift, drawn before the
   buildings' faces and re-formed when a breach changes the topology. Fences,
   sandbags and barriers are doodads and still lie flat. A diagonal wall stands
   as a staircase of cell blocks.
   **Wall styles and damage** (owner: "wall styles (windows/no
   windows/doors) and also damage next"). Each cell's length of face shows
   what stands under it, read from the building and its cells, never a tag
   of its own:
   - A window is drawn only where the battle has one (owner: "Windows are
     real things already. They should be modeled."): a window on a wall
     cell's outer edge (`SharedEdgeBarrier.Kind.WINDOW`, which sight and
     fire pass and a body does not) or a slit pane in a wall cell
     (`CellTopology.isWindow`). Every other length of wall is blind, so a
     pane on a face always stands over an opening a marine can really see
     and shoot through.
   - On homes, shops, offices and clinics the upper storeys repeat the
     window below them (`BuildingFaces.storeyFeature`); works and
     warehouses are blind above the ground.
   - A window's frame is read live: it darkens as its structure falls, and
     once shot out it stands as an empty frame, open to a body as the
     battle now has it. The cells that held a window are remembered for the
     map, so a shot-out one never reads as a door.
   - A door is drawn only at an outside doorway; works' and warehouses'
     doors are loading doors.
   - A doorway the map left with no door draws as a doorway; only the
     rubble a breach leaves (`CellTopology.isRubble`) reads as a breach.
   - The habitat comes from the wall's surface and the world's ground.
     Sealed (ceramic) towns get square ports and airlock doors, shielded
     (light-concrete) walls get slits, and frozen towns get deep-set
     insulated windows.

   **Wall dress** (`WallDress`, painted by `FacePainter`). A face is one
   to two cells tall at play zoom, so the language is bold:
   - A material by what the building was built as and its habitat, toned
     by its world, its own number picking among its kind's colours: an
     open town's plaster homes (adobe on arid worlds, basalt on volcanic,
     stone on rock), brick or sheet works, concrete forts, stone civic
     halls, white clinics; an insulated town's timber-clad homes in deep
     reds, ochres and blues under white trim; a sealed town's ceramic
     panels; a shielded town's poured concrete. Each has a course (block
     joints, panel seams or sheet ribs) and a tone shaded from a dark foot
     to a lit eave.
   - Trim: a dark plinth, a line at each storey, an eave band under the
     roof's edge, and a post at each outside corner.
   - The world's weather at the foot: snow (and snow on sills and eaves),
     sand, ash, sludge, moss or dust; a toxic world's run-off streaks
     down from the eave.
   - Windows cut in the building's way: framed and silled, some shuttered
     in an open town, a shop's front under a striped awning, a civic
     hall's tall windows, a sealed town's ports. Works are blind but for a
     glazed band under the eave, high over any fire; a sealed town's blind
     walls carry vents and downpipes. Doors are framed leaves, airlocks,
     or roller doors with hazard-striped jambs.
   - **A face's foot stands on its wall's outer side**, its top on the
     roof's edge (`BuildingFaces.segments`): the roof covers only the inner
     part of a wall cell, and a foot on the roof's line left the rest of
     the wall cell's flat art showing as a strip along the ground.
   - **What hangs on a wall hangs on its face** (`WallMounts`): a shop
     awning, a loading canopy or a wall lamp lies on the ground cell in
     front of its wall, its rail along the face's foot. Its rail's corners
     are stood up the face to the height it hangs from (an awning's rail
     3 m, its valance 2.5 m), at the very point the face is painted there
     (`BuildingFaces.onFace`); its outer edge takes the plain lift of its
     own height. So it moves with its wall as the view pans, comes down
     with the wall when the building is cut away, goes with it when the
     building is razed, and is drawn between the faces and the roofs, so a
     roof in front of it hides it. Look suite: `createSnapshots
     -Psnapshot=wall-mounts-look` (close, and a pan along the wall).

   Damage is read live from the battle's own state:
   - A wall is hurt against the most hit points it has held (maps seed 80
     to 400), never a fixed scale. Its lost HP soots its top, blows its
     windows (soot plumes over them) and tears ragged holes through it.
   - A breached cell opens dark to the room behind, its rubble spilled on
     the ground. Faces stand on the building's outline as built, settled
     once per map (`BattleRenderer.builtOutline`); only what stands on each
     piece is read again as the battle changes (`BattleRenderer.stamp`).
     Read live, destroyed wall cells moved the roof's edge in behind them
     and stood fresh walls up inside the building. A rubble cell is a
     breach, never a doorway.
   - Where a room's roof has fallen in or burnt, the wall over it loses its
     upper storeys to a ragged, charred edge, with rubble at its foot.
   - Once half a building's roof has fallen in or burnt
     (`BuildingFaces.DESTROYED_SHARE`), it stands as a broken shell no more
     than a storey high, its corners tallest, drawn over its roof's
     remnants, with rubble heaped round it.

   Look suite: `createSnapshots -Psnapshot=building-walls-look` (six
   worlds, intact, cut away and damaged).

   **Cutaway** (owner: "a bit unclear how it will play when we cutaway
   reveal into structures"). A building opens while one of the player's
   units stands in it or the pointer is over it (`BuildingVisibilityPass`,
   the roof reveal in `fog-of-war-nouns.md`); seeing in no longer opens it,
   so the street keeps its height where the fighting is, and units seen
   inside a closed building are marked on its roof. With the fade:
   - the walls turned to the camera drop to knee height
     (`BuildingFaces.standing`, 0.6 m);
   - the far walls' inner sides come up to frame the rooms;
   - nothing pops, and the walls stay opaque.

   A closed building's roof is drawn over the street just past it. A unit
   standing there gets a small mark in its side's colour over the roofs (an
   open building hides nothing), and fog of war still hides enemies.
   The owner chose the knee stub over a ghosted full-height wall. Look suite:
   `createSnapshots -Psnapshot=building-cutaway-look`.
   Each segment's kind and its building's habitat are settled once per
   topology change. Windows and doors are drawn only where a cell spans
   10 px or more. Whole-map stress ROOFS is about 0.5 to 1 ms (noisy);
   the 280x168 compound view stays at about 0.3 ms. Look suite:
   `createSnapshots -Psnapshot=building-damage-look` (an open and a frozen
   crevasse town, intact, damaged and destroyed through the map damage
   layer).
   Next: each terrace a walkable tier (elevation's terraces,
   `elevation-nouns.md`) with stairs, ramps and lifts between them, enclosed
   walkways crossing the floor, heat and glow from below (lit windows, vents
   steaming in the cold). Left: a compound that runs to an edge still breaks
   its wall; the garrison could be held off the bands as the town is.
   First pass (ravine wall): the gorge's strata wall, two ledges and the top
   36 m up, camera 120 m. Left: the owner's pick of height; a top with real
   art (a canopy image rather than flat colour, which blurs as the top scales
   up); trees and roots along the rim; the floor's own look (a dry wash, a
   stream); the floor running off the map's edge (now black past it); a rise
   and a drop on one map (the sight tag already tells their cells apart);
   culling the top's pieces by chunk, since a rise covering most of a map
   cuts many.

   **Lava rift** (owner, 2026-11-09: "Also consider lava/molten worlds"): a
   natural drop whose floor is molten rock. Look suite:
   `createSnapshots -Psnapshot=lava-void-look`, a basalt plain cut by a rift
   with a stone road bridge (`-Dsnapshot.lava.depth`, `-Dsnapshot.lava.eyes`,
   `-Dsnapshot.lava.stillsOnly`, and `-Dsnapshot.lava.setup` = `rift`, the
   default, `river` or `vary`).
   - **What it reuses.** Everything that makes a drop: `NaturalVoid` with style
     `LAVA`, its steps, the field's edge, ledges, walls, bridges and sun, and
     the drop's renderer and painter's order. The wall is its own ImageGen
     image (`basalt-wall.png`, columnar basalt, scoria and ash, under
     `art-source/voids/lava/`); the lip and ledges are scorched basalt and
     ash (`LavaVoidLook`).
   - **What is new: a floor that gives light.** The sun and the sky the walls
     leave open shade every other floor; the melt takes neither. It is drawn
     by `MoltenVoidRenderSystem` in the drop's order, in place of the water:
     the melt's heat drifting downstream, incandescent where it breaks against
     the walls; a crust floating on it (`lava-crust.png`, ImageGen), laid
     twice and drifting downstream, each layer fading into the other as the
     water's pattern does, cut along a wandering line a cell in from the
     walls; a bloom of light over it; a low smoke lit orange from below. The
     floor and crust ripple a little in the heat.
   - **What is new: its light on everything round it.** The melt lights the
     rock above it, strongest just over the melt and halving every 7 m up
     (`LavaVoidLook.wallGlow`): every wall row and every ledge takes it over
     its own sun and sky. Over the tiles, after the lip's fray, the ground at
     the lip takes soft pools of flickering light reaching a few cells out,
     less over a deeper floor (`lipGlow`). The lip ends hard and scorched at
     the drop and takes the plain's own tone back from it, so the ground
     tiles' stepped edge does not show.
   - **The air.** Embers rise off the melt on the wind, cooling from yellow to
     red, each hidden wherever rock stands between it and the eye; smoke boils
     up out of the rift at two heights above the surface and drifts
     downwind; ash falls over everything in view. All of it is in the ground
     layer, so every unit draws over it.
   - **Battle.** Unchanged: a lava rift is a drop like every other style
     (Laws). A rule that would be the first place a style touched the battle
     is proposed, not built: **heat at the lip**, a unit standing within a
     cell or two of a lava rift for long takes slow burn damage, or loses
     stamina, or AI routes prefer to keep off the lip. It would need its own
     tag on the lip cells, written by the brush, so the battle never reads the
     style. Held for the owner (Open).
   First pass: a lip, two ledges of cooled basalt and the melt 40 m down,
   camera 120 m, a daylight sun. The owner settled the depth at 40 m
   (2026-11-09): the style's standing depth, the look suite's default. A
   bridge's ends are road onto the deck, not lip: the drop's fray, the lip's
   light and the smoke over the rift all stop where the nearest open cell is a
   bridge (`NaturalVoidField.byBridge`), for every natural style; no puff of
   smoke spreads over a deck; and the crust runs on under a bridge, since the
   melt 40 m down shows past the deck's edge. Left: a dusk or
   night sky, under which the glow would carry the picture (the night
   lighting draws over the ground layer, so the melt's light needs to stand
   in it as a light source); heat shimmer as true distortion (now a ripple of
   the floor's own pieces; a screen distortion needs a pass of its own); a
   basalt ground tileset for the plain (now the natural talus); cooling flows
   on the plain, crusted and glowing through their cracks; lava falls where a
   side channel pours into the rift; the floor running out past the drop's
   margin in the wide shot, as the gorge's water does.
   - **A lava river** (owner, 2026-11-09: "the lava river might look good with
     flow and not need a 40m drop"): the same style set shallow, a low bank of
     cooled crust and the melt 4 m down (`NaturalVoidZoning.LAVA_RIVER`), a
     valid `NaturalVoid` with no new rule. The owner picked 4 m from 2.5, 4
     and 8 m (2026-11-09): the river's standing depth. A shallow melt runs where a deep one
     pools: the crust's drift and the heat's run quicken from a 24 m melt's
     crawl (0.22 cells a second, the rift's) to a 4 m channel's run (1.1)
     (`LavaVoidLook.runShare`, `MoltenVoidRenderSystem.crustSpeed`), so the
     plates are seen to travel. The pace is the melt's as a whole, from its
     mean depth; along it the channel's flow quickens it through a narrows
     or over a shallow (Flow, below). The 40 m rift stays the style's
     standing depth.
   - **A rift that shallows** (`-Dsnapshot.lava.setup=vary`): 40 m in the
     west, 12 m by the bridge, 4 m in the east, by depth marks.
   - **Lava flows** (owner, 2026-11-10: "We also need to consider "lava"
     planets. We might re-use our conveyor code, but need to rework this for
     lava flows"). The ore conveyor (`OreLine`) routes on the grid at right
     angles and lays a piece a cell; a natural flow wanders, has width and must
     strand nothing, which the drop already does, so a volcanic world's flow is
     built on the drop, not the conveyor. Older flows
     (`LavaFlows`, `CellTopology.lavaFlows`): tongues of cooled crust that broke
     from the melt, widening to rounded toes and now and then splitting, hot
     where they leave the melt and cold at the toe. They are walkable ground
     whose look alone changes (`LavaFlowShading`, one shader pass: the crust
     image laid dark, its cracks glowing by the heat, the margin a curve where
     the flow's blurred cover crosses a half, lobed and frayed by noise, never
     the cells' steps, a flow's root running on into the melt so it meets
     the river's lip as a curve), and they
     keep the landscape's later features off what they buried. The owner picked
     the river with its older flows (2026-11-10, of the rift, the river, the
     river with its flows and the flows alone): a volcanic world's drop is the
     4 m river (`NaturalVoidZoning.forPalette`), and its older flows lie beside
     it. Its scorched lip is laid whole out to the fray's wandering end, and
     the fray lies on the drop's own cells outside the smoothed edge, so the
     bank shows no cell steps (`NaturalVoidField`). Its lip, ledges and melt
     are coloured corner by corner, each corner the mean of the pieces that
     meet there (`CornerShading`), so the glow and ash run smoothly across
     the small pieces rather than stepping; the other drops stay flat. Look suite
     `lava-flow-look`.
     - **The source.** The river is fed from its end farthest from the town it
       runs past (the place whose claimed ground comes nearest the river), so
       the melt is seen to run down past it (`LavaBerm.source`,
       `NaturalVoid.Source`).
     - **The town's berm** (`LavaBerm`): an engineered bank one tier high,
       the storm bank's band (`RidgePlanner.Relief.BERM`, 10 cells through,
       27 to 40 long), on the town's side of the river within 20 cells of
       the town's ground, upstream of the town where there is room and
       slanting downstream off square to the river, so what comes down the
       bank is turned away; a levee alongside where the town crowds the
       river. Its ramp climbs from the downstream face. One older flow breaks
       out above it and ponds against its face. It is raised on the ground
       and access the ridge planner proves, at once when the map has no
       ridges of its own, else with them, its ground held from the hills
       meanwhile. A town too far from the river, or with no room, has none.
       Its crossings are causeways of dressed basalt (`Deck.BASALT`), where
       an outdoor drop elsewhere lays timber.
       A road across the river stands as a bridge; a walled place never
       stands on it (Where, below).

   **Glacial crevasse** (ice; owner, 2026-11-09: more environment types for
   the map toolbelt, ice first): a natural drop split through a frozen
   world's ice. Look suite: `createSnapshots -Psnapshot=ice-void-look`, a
   snowfield cut by a crevasse with a snow bridge and a timber plank span
   (`-Dsnapshot.ice.depth`, `-Dsnapshot.ice.eyes`, `-Dsnapshot.ice.stillsOnly`).
   - **What it reuses.** All of the drop, as the lava rift does: `NaturalVoid`
     with style `ICE`, its steps, edge, ledges, walls, bridges, sun and
     painter's order. The wall is its own ImageGen image (`ice-wall.png`,
     glacier ice in strata with grit bands, meltwater runnels and icicle
     fringes, under `art-source/voids/ice/`).
   - **Ice is lit through** (`IceVoidLook`). A rock face's shade is black; an
     ice face's and snow's is the sky's blue, and the deep tint is a dark
     blue that gathers slowly near the lip and fast toward the floor, so the
     walls run from blue-white to near-black blue. Snow lies drifted on every
     ledge, thickest back against the wall above, thinning to bare ice where
     the ledge breaks over the face below. The lip is a hard snow cornice,
     blue ice only right at the face, going over to the snowfield's own tone,
     so the snow tiles' stepped edge does not show.
   - **The floor** (`GlacialVoidRenderSystem`, in the drop's order in place of
     the water): old dark ice, milky in places, snow drifted at the walls'
     feet, pools of meltwater frozen glassy with a glint drifting on them; it
     takes the open sky's light, not the sun's. A cold mist lies low over it.
   - **The air**, over the tiles after the fray: spindrift blown off the lip
     wherever the wind leaves the ground for the drop, streaming out and
     sinking into the crevasse in gusts, each streak hidden where the ice
     stands between it and the eye; fine snow falling over everything in
     view. No snow blows off a bridge's end and no mist stands under a deck
     (`NaturalVoidField.byBridge`, `nearBridge`), as for the lava rift.
   - **Snow is added light over the tiles, never composited.** A translucent
     piece composited over the ground thins the ground target's alpha, which
     the composite reads as shade: white streaks and flakes landed grey on the
     snowfield, and the fray's fading outer pieces drew a grey seam along the
     lip. So the crevasse's fray ends where its cover is whole, as the lava
     rift's does, and no mist stands over the lip. Every natural style's fray
     still darkens this way at its outer end; on turf it passes for soil.
   - **Battle.** Unchanged: a crevasse is a drop like every other style.
   First pass: a cornice, two snowy ledges and the floor 36 m down, a crevasse
   9 to 16 cells across, camera 120 m, daylight. Left: the owner's pick of
   depth; the narrow crevasse below (two to five cells), which needs a
   narrower span rule: a crossing counts as a bridge only where the ground it
   joins lies `BRIDGE_CELLS` (7) apart, so a plank over a 7-cell pinch drew as
   a tongue of bank with the drop ending either side; snow bridges drawn as a
   sagging arch rather than a straight deck edge; icicles along the lip and
   seracs or fallen blocks on the floor; a dusk or polar-night sky.

   **Arid canyon** (owner, 2026-11-09: "Build everything", a drop for every
   ground palette): a natural drop through a desert's sand plain. Look suite:
   `createSnapshots -Psnapshot=canyon-void-look`, a sand plain cut by a canyon
   with a stone bridge and a plank span (`-Dsnapshot.canyon.depth`, `.eyes`,
   `.stillsOnly`; the suites of the dry styles share `StyleVoidLookSuite`).
   - **What it reuses.** All of the drop, with style `CANYON`. Wall image
     `canyon-wall.png`: cross-bedded sandstone in red, ochre and cream bands,
     desert varnish streaks, honeycomb pits (`art-source/voids/canyon/`).
   - **Dry styles share one set of rules** (`DryVoidLook`); each is a
     `Palette`. Shade in the drop falls toward the rock's own light thrown
     back across it, not black. The ledges alternate between two strata, with
     sediment lying back on each and scree where it breaks. The lip gives way
     to the plain's own tone and the battle's own light within 0.3 cells of
     the edge, exactly, so the tiles' stepped edge does not show and the fray
     ends where its cover is whole, as ice's and lava's do.
   - **The floor** (`DryVoidFloor`, in the drop's order in place of the
     water): talus along the walls' feet, a bed of pale sand with two
     meandering channels braided across coarser bars, lit by the open sky. On
     it lie the plain's own field stones (the nature sheet's sandstone set,
     the same the plain scatters), fallen from the walls, warmed into the
     canyon's light, each with a soft shadow away from the sun; none lies in a
     channel or in the talus. A thin warm dust haze lies low over it
     (`VoidHaze`, shared with the crevasse's mist).
   - **Two findings.** Floor pieces are a quarter cell, so anything noisier
     than that steps: boulders drawn into the floor's colour came out as
     pixel blocks, and channels that followed each piece's own flow bearing
     stair-stepped between pieces; the stones are sprites and the channels
     follow position alone. And a bridge over a wide drop reads as a strip of
     the plain: its deck edge is 1.2 m (`NaturalVoidField.DECK_METERS`), the
     same for an arch as for a plank.
   - **Battle.** Unchanged.
   First pass: four terraces to a floor 44 m down, a canyon 15 to 27 cells
   across, camera 120 m, daylight. Left: the owner's pick of depth; a natural
   arch's thickness, which needs the bridge's kind; mesas or hoodoos standing
   in the floor (a rise inside the drop); a dry falls where the floor steps.

   **Dry gorge** (owner, 2026-11-09: a drop for every ground palette): a
   barren rock world's gorge, the canyon's grey sister and its second dry
   palette (`DryVoidLook.REGOLITH`, style `DRY_GORGE`). Look suite:
   `createSnapshots -Psnapshot=dry-gorge-void-look`, a stone plain cut by a
   gorge (`-Dsnapshot.drygorge.depth`, `.eyes`, `.stillsOnly`).
   - Wall image `regolith-wall.png`: grey regolith and bedrock bands, blocky
     fractured ledges, scree, a few rust seams (`art-source/voids/dry-gorge/`).
   - The canyon's rules with its own colours: grey strata, grit on the
     ledges, shade toward near-black. The floor is dust and rubble with the
     plain's basalt field stones lying thick on it (a palette's
     `stoneShare`); no haze, an airless world's gorge.
   First pass: three ledges, a gorge 9 to 21 cells across. **No one
   depth** (owner, 2026-11-10: "Should we just vary instead of having a
   hard floor?"): the floor lies 28 to 55 m down, varying along the cut
   (Depth along a void, below). Left: dust sifting off the lip;
   impact-shattered blocks bigger than the field stones.

   **Toxic gorge** (owner, 2026-11-09: a drop for every ground palette,
   including the new toxic one): style `TOXIC`. Look suite:
   `createSnapshots -Psnapshot=toxic-void-look` (`-Dsnapshot.toxic.depth`,
   `.eyes`, `.stillsOnly`), its plain the toxic ground's crust where that
   tileset is in the mod and plain stone until then.
   - Wall image `toxic-wall.png`: acid-pitted olive and khaki strata, sulphur
     crusts caked along the ledges, chartreuse and rust stains weeping down,
     pale efflorescence (`art-source/voids/toxic/`).
   - The walls and ledges are the dry rules with the toxic palette
     (`DryVoidLook.TOXIC`): olive strata, sulphur on the ledges, the lip going
     over to the crust's own tone.
   - **The floor** (`ToxicVoidFloor`): acid sludge creeping down the gorge
     along the channel's own flow (`ChannelFlow`), far slower than water, scum
     drifting on it; dried sulphur crust along the walls' feet with a wet rim
     where the sludge laps it; bubbles swelling and bursting out in the
     sludge, never in the crust. Two decks of yellow-green fumes lie over it,
     drifting different ways.
   - **Placement.** Toxic worlds (`SurfacePalette.TOXIC`) are cut by it
     (`NaturalVoidZoning.TOXIC_GORGE`).
   - **Battle.** Unchanged: no hazard (as the lava rift's heat, undecided).
   First pass: three ledges, a gorge 10 to 22 cells across, the sludge 22
   to 48 m down varying along it as the dry gorge's floor does. Left: fumes rising off the lip; a faint glow off the sludge at dusk.

   **Open pit** (owner pick, 2026-11-09: "An open pit" on the mining site, and the
   site must read as planetary industry, a centralized works processing more
   than one dig). A made drop, not an eroded one: an open-cut mine beside a
   Raid mining site's head works, so the head works reads as the hub its
   shafts and the pit both feed. Look suite:
   `createSnapshots -Psnapshot=raid-mine-pit-look`.
   - **Benches.** `NaturalVoid` with style `OPEN_PIT` and six benches 7 m
     apart, each cut 2 cells back from the face above, to a floor 42 m down
     (`OpenPit.STEPS`; owner pick `raid-mine-pit-depth`, 2026-11-09). Cut, not eroded: the field's wander is a fifth of a
     natural drop's (`NaturalVoidField.Shape.CUT_WANDER`), so the benches run
     evenly round the pit. The faces are their own ImageGen image
     (`pit-bench-wall.png`, blasted rock with presplit drill traces, under
     `art-source/voids/open-pit/`); the lip is dusty spoil and the benches
     rolled gravel with scree where they break (`PitVoidLook`).
   - **The haul road** spirals from the lip to the floor: on each face, over a
     fixed turn round the pit's middle, a strip on the bench below sloping from
     the bench above's level to its own, with ruts and a crown, and its inner
     edge falling to the bench. It is drawn from the field's own walls; the map
     marks nothing.
   - **The floor** is wet ore rubble with a still sump where it lies farthest
     from the walls.
   - **The mass driver.** `NaturalVoid.belts`: the electromagnetic rail that
     fires sealed ore capsules from the loader on the floor up the benches on
     its trestles to the catch station on the rim, where they are stopped and
     opened and the ore line takes the ore on. It leaves the floor where the
     floor lies nearest the works. Drawn after the ground tiles and before every
     unit: the rail's bays end to end at each one's depth, each with its coil
     ring, the trestles' cross-beams and their shadow, and capsules running up
     it (`PitMachines.CAPSULE_EVERY`, `CAPSULE_SPEED`) with the glow of their
     hot noses. Drawn only.
   - **The ore line** (owner, 2026-11-09: the conveyor "another layer above
     and also built onto the grid system", its legs giving cover). From the
     first ground cell past the tower, `OreLine` finds a course on the grid to
     the receiving house's wall: straight runs turning only at right angles, over open
     ground, roads and walls but never a building or the pit, a turn costing
     eight cells of run. Its pieces (`ore-conveyor` sheet: straight, corner,
     drive) are overhead props drawn in the roof pass above the people walking
     under them; the straight run's belt scrolls toward the works with its ore
     (`ore-conveyor-motions`). A trestle leg stands every five cells on a
     straight run, where a solid prop blocks no road, doorway, route or ground
     (the landscape pass's reservations and `PlacementGuards`): a fixture
     giving medium cover.
   - **The processing chain** (owner, 2026-11-09: the belt "pulling ore to a
     processing facility with burners to refine the ore", the facility "a
     series of buildings that again plausibly make sense"; real buildings,
     dressed). The head works is the chain the ore passes through
     (`RaidWorksPrograms.mine`): receiving, stockpile, one mill line per
     machine (the raid's machines), smelter, loadout, each a real building the
     battle enters. The ore line links them in that order, each link from the
     wall of one hall nearest the next, never through a hall or a link already
     laid; a link that would wander more than twice its straight distance plus
     twelve cells is left out. Inside the works its legs keep off roads and
     doorways. Next: roof dressing by role (stacker and heaps, mill drums,
     furnace grilles and stacks), the smelter's burner glow and stack smoke.
   - **Placement** (the first generated void, ahead of the rest of slice 2). A
     plan's layout holds a ground site named `pit` (`OpenPit.SITE`) clear of
     growth; the Raid mining template lays it beside the head works
     (`RaidSiteTemplate`). The landscape pass (`OutdoorLandscapeStage`) cuts
     the pit from the open ground the reservations leave in it, before trees,
     hills and plateaus, so it never covers a road, building, doorway,
     fixture, prop, the landing or the routes between required places. Its
     outline is that ground rounded and opened by six cells, its largest
     piece kept; then it is proved (`OpenPit.plan`): every walkable cell left
     outside it stays connected as before, a pocket wholly inside the site on
     open ground is taken into the pit, and any other stranding refuses the
     pit and the map keeps none. No bridge is ever needed.
   - **Machines** (owner, 2026-11-09: a spacefaring megaproject, not a
     modern quarry; the conveyor must make plausible sense. Owner pick
     `raid-mine-megaworks` option c, the mass driver, 2026-11-10).
     `PitMachines` lays the ore's path: a cutting crawler the size of a
     building works a dry face on the floor, its back to the crusher and
     capsule loader at the driver's foot so its boom swings the ore back into
     the loader's hopper. Hover haulers on four lift fans bring the upper
     benches' ore: one comes in over the floor to tip into the loader, one
     lifts off a bench loaded, turned into the pit, and one flies back empty
     over another bench. They fly over the faces, so they are drawn after
     every band, their shadows on the ground beneath (thrown at most
     `HOVER_SHADOW_REACH`). A drilling gantry readies the next blast on the
     first bench with room. The haul road is left to the ground's own traffic.
     The rest is drawn at its depth's scale, after its bench and before the
     face above it. One ImageGen sheet, `pit-massdriver.png`, filled in over a
     geometry guide (`art-source/voids/open-pit/massdriver-guide.py`) after the
     approved concept. Scenery only.
   - **Battle.** Unchanged: an open pit is a drop like every other style.
   Left: spoil heaps and parked machines on the rim; more than one conveyor
   (to the stores, or from the shafts); dust over the working bench; the
   receiving house on the pit's side of the works, so the pit's line need not
   wrap round the compound; a shared trunk where one hall feeds several, not a
   line each.

   **The other styles**, each the same field with its own steps, wall image,
   lip and floor, and each its own ImageGen wall under `art-source/voids/`:
   - **Narrow crevasse**: the glacial crevasse closed to a split of two to
     five cells, few or no ledges. Its floor is not drawn: the depth tint
     runs to a near-black blue, "narrowing to nothing". Its bridges are snow
     bridges: the same span, its edge drawn as a sagging snow arch. Needs the
     span rule above to accept a crossing over a narrow split.
   - **Sea cliff**: the land ends at a map's edge. The void runs to the map's
     edge, so there is no far wall; the steps are fewer and taller, the
     floor is sea: swell drifting in toward the foot, surf breaking along it,
     a few skerries. A causeway is a bridge.
   The field takes these as they are. Each style adds its floor drawing and
   its palette, and a crevasse its "no floor"; none needs a new shape rule.

   **Flow** (owner, 2026-11-09: "a proper flow field that matches the
   crevasse that we can re-use for lava/water"). A drop's floor runs along
   it as one channel (`ChannelFlow`, worked out with the field): each run of
   the drop's cells along the field's border is an **end**, the shallowest
   end the **source** and every other an **outlet**, so a channel runs toward
   its deeper end (ends as deep: toward the east and south). Between them the
   **potential** is the steady flow of a sheet as deep as the floor, nothing
   crossing the walls, solved once by over-relaxation. So the flow follows
   every bend, parts round a pier and runs faster where the channel narrows
   or shallows. A channel with fewer than two ends is **still**: a pool, a
   lava lake, a closed crevasse. From it every floor piece takes **along**
   (the potential, about a cell a cell), **across** (the stream function,
   the same down a whole streamline) and **speed** (about 1 on an even
   reach). Patterns are laid in along and across, never on the map's axes,
   so they bend with the channel, and carried down it in two phases fading
   one into the other, each place as fast as its own speed, so a pace that
   changes along the channel stretches the pattern smoothly instead of
   tearing it:
   - **Gorge water**: streaks along the streamlines; white water where the
     flow runs past half again its even pace (`RAPIDS`).
   - **Lava**: the heat's streaks as the water's; the crust drifts by the
     flow's velocity at each corner of each piece, so the image bends with
     the channel and never tears between cells.
   - **Ice**: the glint on the frozen pools drifts along the streamlines.
   - **The air low over a floor** rides the flow too: each puff of the lava's
     low smoke rises off one place and is dragged down the channel a little
     faster than the crust, and each puff of a crevasse's mist drains down
     it toward the deeper end, as cold air runs downhill
     (`ChannelFlow.carry`, stepped a cell at a time so it rounds a bend; the
     mist through `VoidHaze`, whose canyon dust stays on the wind).
     Each fades over its life. Smoke above the surface and embers stay on
     the wind.
   - **A source**: a generator that knows which way its river runs says so
     (`NaturalVoid.Source`, `withSource`); the end nearest that point is the
     source, whatever the depths.
   A rise has no flow: its floor is the battle's ground. Left: a falls where a
   floor steps.

   **Depth along a void** (owner, 2026-11-09, Model). Every natural drop and
   rise takes depth marks: the field blends a **gain** from them, the share of
   the steps' depth the void stands at, at every point (weights falling with
   the square of the distance past 6 cells, `NaturalVoidField.gains`), and
   scales every step by it. A ledge's pieces stand at the depth of their own
   corners and a wall at the depth of each of its two ends, so a face runs
   taller at one end than the other and a floor slopes between marks. The
   sun, the sky and every floor's and air's drawing read the depth where they
   are; the wall image's strata thin where the face is shorter; a crevasse's
   floor takes less of its deep blue where it shallows. Look suites:
   `-Dsnapshot.gorge.vary`, `-Dsnapshot.ice.vary`, `-Dsnapshot.ravine.vary`
   and the lava rift's `vary` setup. **On a generated map**
   (`NaturalVoidZoning`): a zoning row may carry a depth band instead of one
   depth, and the cut is then marked every 30 cells along its longer run, at
   its middle, the depths drawn spread over the band so every cut reaches
   near both ends of it (`NaturalVoidZoning.marks`). The dry and toxic gorges
   carry one; the dry and toxic look suites shoot it as `varied` unless
   `.depth` fixes one. Left: the other natural rows (each still an owner's
   depth); a gorge river deepest where its brook's valley is, shallowing to
   its fords; a falls where a floor steps rather than slopes.

   **Placed by design** (owner, 2026-11-10: "We need to place these as part of
   map design ... I don't really want randomized maps, I would prefer we make
   fun maps, then skin them to match the biomes"). A natural drop, or walls
   sheltering a town, is a **landform** a map's design states (`Landform`:
   `Drop` and `Shelter`), never a share of generated maps: nothing rolls for
   one. Stated with the plan, before anything is laid, as held ground
   (`Landscape.held`, relief `DROP` or `SHELTER_WALL`), so the places, roads
   and fields grow round it; the landscape pass cuts the drop or raises the
   walls on what was held. The world's palette only picks its skin. A battle
   asks for one through `BattleSetup.createPlaceholder(..., landform)`; no
   story map or contract states one yet. Look suite:
   `createSnapshots -Psnapshot=natural-void-maps-look` (one design, a remote
   Assault cut south to north across its approach, skinned by each palette).
   - **Which style.** One table beside `SurfaceZoning`,
     `NaturalVoidZoning.forPalette`: verdant takes the gorge river, frozen the
     glacial crevasse, arid the canyon, rock and irradiated the dry gorge (irradiated
     until a glassed wall of its own exists), and a molten palette the lava
     river (`NaturalVoidZoning.LAVA_RIVER`, the owner's pick over the rift).
     Each row is the style, its steps, its wall image and the image's tile;
     the camera is the natural 120 m. A new palette does not compile until it
     has a row.
   - **The canyon and the dry gorge** are the dry styles above
     (`NaturalVoid.Style.CANYON`, `DRY_GORGE`), each row drawing its own wall;
     the basalt wall was tried on rock and read as holes at a wide zoom.
   - **Where.** A drop runs edge to edge along its design's line
     (`NaturalChasm`), wandering at most 14 cells off it, 7 to 12 cells wide.
     The design holds a band as wide as it can reach (`Landform.Drop`), and
     moves every programmed place's seed off that band's held ground, so a
     walled place's claim keeps off it (`FortReservations.dropGround`) and the
     drop runs past its wall. It takes only ground the landscape pass's
     reservations leave (`OutdoorLandscapeStage`), after the open pit and
     before anything grows: so it never covers a road, building, doorway,
     fixture, prop, landing or a spawn, and each of those that crosses its
     line is left standing across it as a bridge. A route between required
     places is not kept out: its five reserved cells left standing read as the
     drop stopping, so a crossing is left where the route's own line meets the
     drop's and the drop takes the rest of the route's ground. A landing's
     approach to the edge keeps only trees and rises off a flight path, so the
     drop runs under it.
   - **Crossings.** Every 34 cells along its line, and wherever a route
     crosses it, a crossing four cells wide is left standing; a regular
     crossing within 17 cells of a route's gives way to it, so no sliver of
     drop is left between them. Each is decked with its drop's deck
     (`NaturalVoidZoning.Deck`: a surface laid on the crossing, a doodad laid
     over it, or both), a timber footbridge outdoors and a basalt causeway
     over a lava river; the drop is opened
     before the crossings are taken out, so it meets each deck square and runs
     on under it. A snow bridge on ice was tried: plain snow over a crevasse
     read as two pits with ground between them, not as a way across.
   - **Proof.** Every stretch of walkable ground connected before stays
     connected after, through the bridges and crossings. A pocket of open
     ground of 40 cells or fewer that it would strand is taken into the drop;
     any other stranding refuses the drop, and a design whose line does not
     fit keeps none. So no spawn or required place is ever cut off.
   Left: maps whose designs place them (a story map's pool, a contract
   site); how wide; road bridges (a road crossing is left as ground, not yet
   drawn as a span); the brook's own gorge, the line below; a drop as a
   mission's dividing line.

   **Placement in generated maps** (slice 2 for the natural styles), each
   where the generator already lays the feature it deepens:
   - A **gorge river** in place of a brook (`Landscape.Brook`): the brook's
     path cut as a void its width plus banks, its fords becoming bridges,
     a footbridge or a road bridge by what crosses there. A brook's valley
     sides (`elevation-nouns.md`, Ridges) are not raised beside a gorge: the
     gorge is the valley. The same proofs as a brook's: every required place
     stays connected through the bridges.
   - An **arid canyon** on desert worlds (`SurfacePalette.ARID`), where a
     temperate map would lay a gorge river, and as a mission's dividing line
     between two halves of a map.
   - A **glacial crevasse** on frozen worlds (`SurfacePalette.FROZEN`), where
     a temperate map would lay a gorge river, and a short seeded split laid
     across open ground off the mission's lines, never across a road, with a
     snow bridge where a route must cross.
   - A **sea cliff** where a beach map lays its shoreline (`BeachShorelineStage`)
     on a map whose landing is not from the sea: the shore band cut as void
     at the map's edge.
   - A **lava river** with its older flows on molten and volcanic worlds,
     where a temperate map would lay a gorge river (Lava flows, above), and
     as a mission's dividing line.
   - All of them refuse what a plateau refuses (roads, buildings, doorways,
     fixtures, reserved cells), and `-Dbattle.mapgen.voids=N` asks for them
     on every map for evidence.
5. **Raymarched depth (optional).** Only if slices 3 and 4 do not sell the
   depth: the composite raymarches inside void cells into a virtual space below
   (the owner's MoonLightEngine technique), reading the resident macro-relief
   field for the voids' walls. Bounded to void cells, where nothing stands, so
   the picture can never hide or show a unit the battle sees otherwise. Never
   applied to building faces, which would lean over units the battle still
   sees. Costed with `renderEvidence`.

## Laws

- A void is never walkable, never blocks sight or direct fire, and never
  changes in a battle.
- Every void style plays the same; a style changes only the look and where a
  generator puts it.
- Nothing stands, lands or falls in a void.
- Everything drawn inside a void is presentation. It neither displaces nor
  hides a unit.
- A rise is never walkable, blocks sight as higher ground does, and never
  changes in a battle. Nothing drawn of it, however far it leans, hides a
  unit.
- A map without voids behaves exactly as before.

## Open (owner)

- Rises: whether a top leaning over the walkable floor (behind a rock stack,
  or a mesa seen from above) should give way to units more visibly, for
  example fading where a unit stands under it. Units are drawn over it now.
- Depth: felt out from the slice 3 and 4 renders: whole storeys per style, or
  one "bottomless" look.
- Later, not now: whether a railing ever gives low cover, or a lava rift's
  lip burns those who linger at it (slice 4, Lava rift). Either would be
  the one place a style touched the battle.

## Acceptance

- In `across-the-gap`, the squads trade fire across the void and route round it.
  With the void filled in as ground, they close.
- Generated maps with voids keep every required place connected
  (`garrisonEvidence`, `storyMapPools`).
- Renders of each style sent to the owner at 1920x1080. The edges and floor read
  as depth, with no mark painted on the ground.
