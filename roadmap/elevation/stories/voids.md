# Story — Voids: depth a map can't walk into

Status: IN PROGRESS

Written: 2026-11-07

Updated: 2026-11-07 — the industrial stepped sky port has traffic: vanilla hulls flying its bays and skyways, freighters parked on its terraces.

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
- A void's **edge** is its band of cells along walkable ground. It is drawn
  as a wall going down. To the battle it is the void itself, not a face, so it
  needs no width of its own.
- A **bridge** is walkable ground across a void: ordinary ground or deck cells
  at the tier of the ground they join. Nothing walks under it, so "one cell, one
  level" holds. A bridge's width decides what crosses it, exactly as a ramp's
  does: infantry need one cell, a vehicle or mech needs its clearance.
- Roads over roads (overpasses) are **not** voids and are out of scope. They
  need two walkable levels on one cell, which this model forbids.

### Void styles

A **void style** says what a void looks like and where a generator puts it.
Every style plays the same in the battle. A style is presentation and
generation, never a battle rule, as an emplacement style is
(`ground-turret-tiers.md`).

| Style | Where | Edge | Floor | Bridge |
| --- | --- | --- | --- | --- |
| **Crevasse** | Ice and rock country: a narrow, jagged split | Blue-white ice or fractured rock, a snow or scree lip | Dark, narrowing to nothing | A snow bridge or a laid plank span |
| **Sea cliff** | A map's edge or a bluff: the land ends | Sheer rock or a cut quay wall | Surf and spray far below | None, or a causeway |
| **Gorge river** | A river cut deep, in place of a brook | Steep banks, rock and roots | Running water far below | A footbridge or road bridge |
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
     (`VoidBackdrop.slips`), slowing in and gathering speed out.
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
4. **Look, the other styles.** Crevasse, gorge river and sea cliff on the same
   bake, each shown to the owner.
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
- A map without voids behaves exactly as before.

## Open (owner)

- Depth: felt out from the slice 3 and 4 renders: whole storeys per style, or
  one "bottomless" look.
- Later, not now: whether a railing ever gives low cover. It would be the one
  place a style touched the battle.

## Acceptance

- In `across-the-gap`, the squads trade fire across the void and route round it.
  With the void filled in as ground, they close.
- Generated maps with voids keep every required place connected
  (`garrisonEvidence`, `storyMapPools`).
- Renders of each style sent to the owner at 1920x1080. The edges and floor read
  as depth, with no mark painted on the ground.
