# Battle Rendering

Status: ACTIVE — the layered command pipeline is shipped; asset consolidation and view-resident dense rendering remain deliberately unqueued extensions.

Written: 2026-08-23

Updated: 2026-10-24 — Roofs have styles, cover the walls under them (whole inside, an eave outside) and show what broke them, cell by cell (Roofs, law 25).

Earlier 2026-10-23 — Texture leases: a battle, the company deck, the Armory previews and the ops dialog's UI images each hold their own textures and give them back (`battle-textures.md`).

## Vocabulary

- The **battle render pipeline** is the presentation path from current battle state to a painted world frame. It is a per-frame view of the simulation, not simulation state or a second gameplay authority.
- The **game render tier** decides what to show and in what order. It owns battle-specific layers, producers, presentation policy, and the frame context.
- The **render engine** is the reusable mechanism that projects coordinates, buffers commands, batches primitives, and brackets hostile GL state. It does not know what a unit, roof, faction, or objective means.
- A **render layer** is one named stratum in the world stack. Its ordinal is paint order; it is an occlusion contract, not a depth-sort hint.
- A **prop** is scenery: authored, placed at generation, drawn from a sheet, and never a simulation actor. Size and subject do not promote one — a parked truck draws in the same layer, from the same registry, and with the same cover model as a crate. A second render path for scenery that merely looks important is duplication, and it drifts: the one this replaced had grown its own list, its own sheet cache, its own footprint stamp, and a cover rule nothing else in the game used.
- A **render system** is a GL-free, per-frame producer. It reads the frame context and appends commands for its one layer without putting render data on simulation entities.
- A **roof** is drawn over every building cell and over the walls that carry it (`RoofCover`, `RoofTiles`). Where nothing outside touches a cell, a floor, a partition, a party wall, it is covered whole, so from outside an interior reads as one roof. Where a cell faces the outside, an exterior wall or doorway, the roof stops at an **eave** `RoofCover.EAVE_DEPTH` of the cell in from the building's side, with a trim and a short shadow along its open edge, so the wall's material and its worth as cover stay readable; an outside corner keeps only its inner square, an inside corner all but the square the outside touches. A roof over a wall is presentation only: whether a roof shields is the floor cell's `isRoofIntact`.
- A roof is drawn in its building's **roof style** (`RoofStyle`): clay tile, poured slab, corrugated sheet or armour plate, from the fort lot's works family, else the wall material, else the building kind. Its courses run along the building's long side, from a seamless two-by-two swatch, so a roof has no cell seams.
- A roof is broken **cell by cell, by what hit it** (`RoofDamage`): a shell or the edge of a bomb **holes** it, a bomb's core, a crash, creep or the wall under it falling **collapses** it, fire, a room's flashover and a wall alight **burn it through**. A second blow keeps the worse. Every broken state is open to the sky, which is all a rule reads; each draws its own frame of the style's sheet, cut to the cell's cover, and an eave keeps no trim once broken. An eave over a wall takes its own damage and has no shield to lose.
- A **swing door** hangs in every one-cell doorway on a battle map (`SwingDoor`, `SwingDoorRenderSystem`): a leaf hinged on one jamb that swings into the building as someone comes in line with the doorway and falls shut as they leave. Its angle is read each frame from where people stand, never stored, so it needs no home for state and paints the same in every host; it does not lock, block or shade anything. A wide doorway is a gap and has none, rubble removes it, and ship decks draw their own sliding compartment doors instead.
- A **field-cache marker** reads the battle's optional salvage ledger. Unknown caches emit no commands; discovered caches retain a last-seen marker, while current recovery progress requires current visibility. Its pickup art is a nonblocking interaction cue, not a new scenery or cover authority. Bitmap world labels carry the same text and font metrics into live and headless drains.
- The **frame context** is the current simulation view plus camera, layout, alpha, selection/highlight state, and host-owned frame inputs. It is temporary and is not retained as gameplay state.
- The **draw list** is the pooled, per-frame command buffer. A **draw command** describes one presentational operation: a sheet quad, whole sprite, solid geometry, line, ribbon, polygon, or a bounded own-GL escape.
- The **drain** replays each layer's commands in strict submission order while coalescing only adjacent compatible work. It turns a deferred command stream into GL calls without changing the painter's meaning.
- A **custom command** is an explicit boundary for a pass that owns its own GL lifecycle or persistent target, such as an FBO-backed accumulator. It is not a shortcut for ordinary geometry.
- A **sheet quad** is a sub-rectangle batched from a shared sheet; a **sprite** is a whole texture rendered through the host API. They are presentation forms, not simulation identity.
- A **render appearance** is a type-shared render-side description of what an entity kind can draw. Dynamic pose, health, visibility, and interpolation remain current simulation inputs.
- An **allegiance** is the presentation reading of a unit's simulation faction from the player's chair: player, ally, neutral, or enemy. Faction is the side a unit fights for; allegiance is how the person watching should read it. The number of ownership buckets a player can distinguish at a glance stays four however many factions the simulation fields.
- A **durability bar** is the ownership-coded gauge above an entity reporting its remaining combat durability. It carries one **row** per capacity — structure below, armor above it, since armor is spent first — and each row fills against its own maximum, so a capacity at full reads as full whatever the other is doing. It is a per-frame read of current capacities, never a second durability authority.
- A **notch** is one fixed quantity of a capacity marked off along its row. It is deliberately not called a cell: cells are the simulation's grid, and a notch measures durability, not space. The quantity is per capacity and identical for every entity in the battle, so notch count and density read magnitude directly: a marine is one notch, an emplacement a handful, a heavy mech a full comb. Notches measure the entity, not the bar — a longer bar shows the same notches further apart. Armor and structure take different quantities because the authored capacities differ in size; forcing one scale on both leaves the larger capacity illegible.
- A **frame census** is what one frame submitted, per layer: commands
  collected, quads, sprites and custom passes drained, draw calls, texture binds,
  and the nanoseconds collection and submission each took. It is an instrument,
  not a budget the renderer enforces — nothing in the pipeline reads it, and it
  is null in the game.
- A layer is **collection-bound** when its cost is building its command stream
  and **submission-bound** when its cost is handing that stream to the driver.
  The distinction decides which lever is worth pulling: a hundred thousand cheap
  commands that coalesce into six draws are not helped by emitting fewer of them,
  and four hundred sprites that cannot coalesce at all are not helped by
  emitting them faster.
- A **framing gate** withholds detail that could not be read at the current
  framing. It is stated in screen pixels per cell rather than in camera zoom,
  because zoom 1 is cover-fit and therefore means a different number of pixels on
  every map size. It is never an occlusion or visibility decision: what it
  withholds is decoration on something drawn anyway.
- The **fog field** is the player's fog, resident as one map-sized
  single-channel texture: one texel per cell holding the alpha that cell's
  shadow draws at, sampled by one quad over the map rectangle. Its invalidation
  is the vision service's **changed extent** (`fog-of-war-nouns.md`), not the
  cell topology's change log — a cell's terrain and who can see it move for
  entirely different reasons.
- A **ground atlas** is every sheet the `GROUND` layer can draw from, composited
  into one GL texture at battle load, with each sheet keeping an origin inside
  it. A ground quad — resident or sparse — addresses that texture at its own
  sheet's origin plus its own source rectangle, so the layer is one texture
  rather than six. It is a change of address and not of picture: the same quads
  in the same order over the same rectangles.
- A **decoration atlas** is the same change of address for the `DOODADS`
  layer, with two differences that follow from what props are. It holds
  *cuts* rather than sheets — each placed prop's source rectangle, plus the
  states its catalog family can swap it to (`-damaged`, `-destroyed`, blight
  stages) — because the catalog is ninety sheets and a map places a few hundred
  rectangles from a handful of them. And it is planned **per battle**, from that
  battle's placements, the first time the layer sees them; a new battle's
  placements retire the last one's.
- A **texture lease** is one owner's claim on the textures it draws, held in the mod's
  **texture ledger**. A battle loads its **battle texture set** — what its map places, its
  roster wears and its mission can bring — under its own lease behind a loading screen, and
  releases the lease when it ends; the ledger unloads (and deletes on the card) only what no
  other lease holds, so a screen that shares an image never loses it. The company deck preloads
  what its scene draws behind the dialog's other pages and holds it until a battle or the
  dialog's close, so its screens never show a load; the Armory's previews hold the
  families they show while the Armory is open, and the ops dialog's retained-UI images go when
  the dialog closes. What remains on the never-released session lease is small: fonts, crests,
  planets. `battle-textures.md` owns the model, the inventory and the laws.
- The **resident ground** is the ground that is a function of the battle's cell
  topology, baked once into vertex buffers and drawn from them. Every cell that
  draws a tile owns four vertices in it and keeps them, in cell coordinates, so
  the camera is a modelview transform rather than a pass over the data. A cell
  that changes is patched in place over its own slot.
- A **sub-layer** is one stratum of the resident ground: at most one quad per
  cell, its own buffers, and a fixed place in paint order. The base terrain is
  the first; the nature scatter laid over it and the doorway decals over that
  are the second and third. Sub-layer order is the whole of the painter contract
  between them, because within one sub-layer no cell's quad overlaps another's.
- A **ground feather** is the noise-edged reach of one outdoor ground into its
  weaker neighbour, so a boundary between grass, earth, sand, gravel or stone
  reads as a mixed, irregular edge rather than a cell's square side. It is
  presentation only: the cell keeps its ground for movement, cover, damage and
  every other reading, and walls, paved road, ruins, interiors and authored
  surfaces outside the feathering list neither give nor take one. A fixed
  priority decides who bleeds into whom (green growth over bare earth, earth
  over sand and gravel); a road's soft shoulder feathers as the ground it is
  drawn as. The bleed is one field over the map — opacity falling off with
  distance from the stronger ground's cells, wobbled by a noise fixed edge by
  edge so neighbours always agree — and it reaches less than half a cell, so
  each quarter of a cell is decided by the three neighbours at its corner and
  is drawn as a clipped quarter of a tile baked from the material's own art
  (`GroundFeather`, `GroundFeatherBaker`). Its quarters are ordinary resident
  sub-layers laid straight over the base terrain, under road edges, raised
  terrain and decoration, so the live mesh, the streamed fallback and the
  review drain draw the same picture. Surface relief stays per cell; the
  feather changes colour, not height. Control switch:
  `-Dbattle.render.groundFeathering=false`.
- A **shore** is where natural water meets natural ground, drawn as a contour
  instead of the cells' square steps. A field over the cells carries +1 at a
  water cell's centre and -1 at a land cell's, and at each corner twice the
  mean of the four cells' signs, nudged where water and land meet by a bounded
  noise anchored on the corner; every cell is four triangles fanned from its
  centre, and each band — a translucent wet film over the bank, the light
  shallows, the open water — is a triangle clipped where the field passes its
  level (`WaterShore`). A staircase reads as a slope and a straight bank
  wanders a little about its cell edge. It may not lie about where a unit
  can walk: a centre is always drawn as its own side, and the line moves only
  about the corners and edges between water and land. Water cells on the shore
  draw a neighbour's ground under their bands, so what the contour cuts away is
  bank; the relief composite treats them as ground (no waves to tear the
  contour), and water stands level with its bank, because a sunken square cell
  casts the steps back. Built edges stay square — a pool, a quay, a road or a
  bridge has a made edge — so only water and the natural grounds take part.
  **Shallow water** (a ford: walkable ground tagged `SHALLOW_WATER`) is water
  to the waterline and ground to the open water, so the shore reads two
  fields — wet and deep, the same field on a bank with no shallows — and a
  ford is shallows from bank to bank with open water beginning beyond it. Its
  bed shows through as pebbles on every ford cell, where the open water does
  not cover it (art from `art-source/terrain/bake_water_ford.py`; the owner
  chose the plain pebble bed over a line of stepping stones).
  Hive creep meets the same contour: it stands back from the shore's water and
  wet bank as from a cliff face (`ShoreWaterMask`, the field sampled
  continuously by `WaterShore.fieldAt`), so its edge runs along the wet band.
  Over a ford's bed, where the wet field and the deep field disagree, it lays
  only faint veins and a film.
  The bands are resident sub-layers over the feathers and under road edges,
  raised terrain and decoration; their art is derived from the water tiles
  (`art-source/terrain/bake_water_shore.py`). The owner chose this organic
  contour, noise and all, over the same contour without its noise and over
  the cells' own steps.
- A **visible cell rectangle** is the camera-derived dense-world cull. It reduces work for cell-backed terrain passes; it does not replace the simulation's cell grid.
- A **view cull** is the same idea for things that are not cells: the camera's
  viewport in cell space, asked whether a body's own drawn extent can land in
  it. It is not visibility and not a framing gate — fog decides who may be seen
  and a gate decides what is too small to read, while this decides only whether
  the drain was going to keep the pixels. What a caller passes it is the extent
  of the thing it is about to draw, never a shared margin: a marine is a cell
  across and a parked transport is twelve, so one number is either wrong for the
  transport or useless for the marine.
- An **embedded scene host** is a bounded consumer of the ordinary battle camera,
  simulation view, and selected render layers. It owns its viewport and framing,
  but it does not acquire the standalone battle's HUD, input, audio, or update loop.
- A **headless scene drain** replays an embedded scene's ordinary draw list through
  the retained Java2D canvas. It replaces only the host graphics backend; it does
  not reconstruct tiles, props, actors, camera placement, or layer order.
- A **map battle scene** promotes a generated `MapResult` through ordinary battle
  setup and exposes its production render systems without mission HUD or input.
  Authoring previews frame this scene; they do not own a parallel tile painter.
- **Outdoor night lighting** is an authored map atmosphere with named power
  circuits and local fixture pools. It retains readable ambient terrain and
  shares the ordinary world-render path across live and headless hosts. Circuit
  restoration replaces its presentation state; it never grants observation,
  alters targeting or changes navigation. Outdoor floodlights have grounded supports
  and oriented heads that remain visible when unpowered; interior strips retain
  their separate fixture identity. Appearance never changes the authored light pool.
  Ship-compartment lighting is separate.
- **Aircraft engine light** is the nozzle and exhaust's own emissive alpha mask,
  drawn once after environmental shade. It follows the admitted aircraft's pose,
  appearance offsets and smoothed thrust demand. Its localized reflection uses
  the hull's own painted colour and alpha, so adjoining plating catches light
  while gaps stay transparent and the rest of the hull retains night shading.
  Both effects share the exact hull pose and the same per-engine burn response;
  engine emission does not light the ground or grant observation through fog.

## Ownership and flow

`BattleScreen` owns the session loop, input, audio, host chrome, and the outer scissor boundary. For each world frame it supplies a frame context to the battle renderer. The renderer asks its ordered systems to collect the current presentation into one draw list, then drains selected layers in `RenderLayer` order.

Systems pull fresh state every render frame, so camera motion, interpolation, visibility fades, recoil, and live pose remain responsive even when the simulation advances at a lower cadence. Simulation objects carry conceptual identity and state; render-side flyweights and the asset service resolve that identity into sheets, sprites, frames, and presentation policy. No simulation entity owns a `SpriteAPI` or a mutable renderer handle.

A fitted boat's artwork belongs to its hull pattern, while its campaign identity,
handling and durability belong to the actual boat. Flying hulls, grounded hulls
and shadows use the same render-side pattern lookup. Asset caches never require
an entry for each named boat or fitting combination; fitting a boat cannot make
its hull disappear. Live and headless hosts share this resolution.

A controlled Marine's applied sprint lowers its primary weapon in both sheet
and layered appearance while retaining manual cursor bearing. A valid aim point
alone cannot raise the weapon through that movement state. Gait continues to
follow actual applied travel; presentation neither grants the speed boost nor
spends stamina. `direct-control-nouns.md` owns those decisions.

Modular infantry may separate the shoulder shells from the torso. These are
optional armor parts whose authored stride, aim, and bracing motion follows
torso registration independently of helmet look. Continuous joint backing
preserves the body under articulation. Ordinary weapons paint below the torso
and shells; an over-shoulder launcher paints above them but below the helmet.
An armor family without separate shells retains its ordinary composition.
Articulation never changes collision, durability, or equipment identity.

An equipped integral system may carry a separate hardware sprite. Its catalog
presentation selects an actor, torso, or left/right shoulder attachment;
shoulder attachments inherit the actual shell joint's position, scale, and
bearing, never helmet look. An armor without a separate shell uses the matching
torso registration instead of borrowing another family's shoulder animation.
Offsets remain local to the selected attachment. Live battle and Armory compose
the same equipped definition through the same layer helper.

Carried hardware remains present while idle, active, recharging, or empty. A
running-system timer is not a firing animation clock. A committed missile launch
supplies a separate short firing pulse and independent pod bearing. The pod
braces at the physical muzzle, kicks, and settles back onto its carried joint;
its authored sprite muzzle registers to that same launch point. The firing
hardware continues to travel with its wearer while the released round follows
its own committed flight. Under-body hardware paints
before the torso; over-body hardware paints after its shoulder shells and before
foreground handheld equipment and the helmet. The suit mount neither consumes
the carried special slot nor hides the primary. Capability effects, shots, and
projectile origins continue to come from the simulation. An absent firing pulse
leaves the carried pose intact; recharging does not repeatedly play a launch.

The direct-control aim lane is read-only world presentation derived from current
firing origins, pointer aim, and deterministic structural terrain. Live and
headless rendering share its clear segments, obstruction markers, and dashed
blocked segments. Observation bounds clip that geometry before it can reveal
hidden structure; the preview never performs a random shot resolution or
manufactures a target, contact, or visibility change.

Within a layer, producer submission order is paint order. Across layers, enum order is paint order. The drain may batch adjacent commands with the same compatible primitive and state, but it flushes whenever batching would invert that order. Every loaded sheet that can emit a sheet-quad command must be registered with the live drain after that sheet becomes available; a host whose asset lifecycle loads a sheet after its terrain batches are built registers it at that later lifecycle seam. A foreign sprite render or a custom pass is treated as GL-state pollution until the engine has re-established the state required by the next batch.

The standalone host normally renders every layer. A host can request a subset through the same pipeline, but it must supply the camera and context each selected producer needs; omitting a layer does not manufacture unavailable state. This is how the combat bridge presents ground content in vanilla combat without forking the ground renderer. The Mech Lab uses the seam as `GROUND + DOODADS`, then `UNITS`; Barracks adds `SHOTS` to its actor pass so retained overlays can sit between physical room content and live range activity. The live host drains those commands through Starsector/OpenGL; headless UI evidence collects the same systems and replays their sprite, sheet-quad, fill, and line commands through Java2D. The headless camera receives the canvas's actual content-box extent, and its resolved commands paint one-for-one in that box; the authored canvas surface must not apply a second non-uniform stretch that turns square battle cells into rectangles. Its embedded frame profile suppresses HP bars and surface-relief FBO work; fog is absent because these hosts do not request the fog layer.

Player-view headless evidence retains battle visibility, fog-clipped outdoor
lighting and unit decorations through the same producers as the live battle.
Its host declines GL residency and surface-relief targets, so the strict
Java2D drain receives the ordinary streamed commands without silently dropping
unsupported custom operations. The graphics drain does not decide whether a
lamp may illuminate unseen ground; that policy belongs to the host's player
view independently of its rendering backend.

An embedded scene may seek a battle-owned ambient task route before collecting a
pure pose frame, or it may advance a bounded simulation when authored activity needs
real time-dependent state. In the latter case an ambient primary-fire beat enters the
ordinary infantry and ballistics services; the renderer only consumes the resulting
entity pose, active `ShotEvent`s, and impact effects. The same weapon definition selects
the fire clip in standalone positional audio and embedded campaign-local positional
audio. Because vanilla weapon clips are combat sounds rather than UI cues, an embedded
host anchors them at the player fleet instead of routing them through the UI-sound API.
Selecting `SHOTS` also requires that host to load the shared projectile and bolt cache;
otherwise a valid simulation event may have no drawable body. Carried
special-equipment poses may remain dry drills. Route ownership, interruption, and
whether an action has physical consequences remain simulation concerns; the render
pipeline never manufactures a shot.

Mission readouts occupy a final `MISSION_MARKERS` annotation layer, above lighting
and physical effects. Their beacons, progress and bitmap labels are public mission
information, not surfaces for night shading; moving them never moves the physical
world or grants visibility. Both live and headless hosts consume the same commands.

The current world order is `GROUND → DECALS → VEHICLES → DOODADS → HIGHLIGHTS → FOG → UNITS → HAZARDS → SMOKE → ROOFS → DRONES → OBJECTIVES → COMPOUND → CONVOY → SHUTTLES → SHOTS → IMPACT_FX → FLYBY`. The enum is the authority for this order; the sequence here makes the standing occlusion contract legible without replacing it. A cell that current clear-air observation would reveal but smoke actually conceals receives half the ordinary fog shadow, then smoke paints above it with a bounded opacity boost. This exposes a muted terrain silhouette as context while leaving the actual cell unrevealed and its actors hidden; naturally unseen smoke remains under ordinary fog.

Ground is a dense, cell-backed surface. Current camera culling range-loops the visible cell rectangle for dense passes and AABB-rejects eligible sparse scenery. This preserves cell truth while avoiding off-camera collection. If terrain or decal work becomes the measured ceiling again, future dense render tiles may cache a view-resident projection of cell blocks. A tile is a derived, view-admitted presentation block, never a new simulation grid or coordinate system. Ground and decals may keep separate backing while sharing tile addressing, invalidation, and eviction policy. Evicted ground rebuilds from cells and evicted decals replay retained sources; unavailable tile backing falls back locally to the present cell path without changing paint order.

The fog layer finishes by painting observed solid props through the ordinary
prop sprite emitter. A revealed opaque cell in the prop's rotated and mirrored
surface footprint admits the sprite; an empty corner does not. Most physical
props use their rectangular footprint by default, while irregular canopies
carry authored masks. Sprite alpha preserves fog in gaps and around the prop,
and the reveal bitmap remains unchanged. Actors, terrain and further props in
its shadow remain concealed. This presentation uses actual observation, never
the smoke-free counterfactual. Flat debris keeps ordinary ground fog. A grounded
ship is subject to the same rule: seeing its near solid surface admits its
whole artwork, even when its center and far side are unobserved. Its physical
footprint remains impassable and opaque; admitting the artwork grants neither
observation nor a firing lane through the hull.

Night lighting admits lamp pools through the observed artwork's actual alpha,
including hull surfaces above unobserved ground. The same sprite commands,
source rectangles, rotations and mirrors form a framebuffer-resolution coverage
target; a rectangular cell footprint cannot admit lighting. Transparent corners
and holes retain the ordinary fog-clipped shade exactly. Partial edges mix the
premultiplied lit and fog-clipped treatments continuously, and overlapping props
combine coverage without applying the lighting twice. The final night pass
changes shading only, never repainting scenery over actors, roofs or aircraft.
Coverage grants neither observation nor an illuminated patch of unseen ground.

The shade itself is one function of the light pools, sampled bilinearly at
half-cell resolution: the lamps' pools baked once per map, fire's refilled each
frame. The battle's event lights (muzzles, impacts, blasts, bolts) add to fire's
pools while they last, on their own side of a roof, so gunfire and blasts light
the night as they light the ground composite by day. A host that can run a custom pass owning its own GL evaluates it on the
card, per pixel, with the pools as textures (`NightShadeField`); a Java2D host
builds it as tinted quads at a grid of points in each cell. Both evaluate the
same function (`NightLightingRenderSystem.shade`), which the field's GL evidence
holds it to, so the two differ only in where they sample, and the field is the
smoother of the two zoomed out. The field's textures cover the whole map and
stay resident: fire's pools and the cell mask take only the box that changed
since their last upload (the boxes fire's lights cleared and filled, the cells in
the topology's change log, the reveal log's rectangles), so a whole-map view of a
burn repacks the burn, not the map. Filling fire's pools is still CPU work each
frame, bounded by the lights in view. A field that fails, or a driver that stores its
pools at fewer than twelve bits (which would band the shade), logs once and the
next frame builds quads. Real-driver evidence reads the stored precision on
Windows and on the Mac's GL 2.1 context.

The headless drain rasterizes those same commands and shade meshes and performs
the same pixel composition. Real-driver evidence checks corners, internal gaps,
partial alpha, overlap and transforms. If the graphics targets or shaders fail,
the live pass retains ordinary fog-clipped darkness rather than admitting a
rectangular fallback. Night targets are released with the owning battle renderer.

Shared-edge windows are sparse GROUND features rather than painted properties
of either adjacent floor cell. The collector reads the live canonical barrier
list, culls against either neighboring cell, and emits frame and pane geometry
over the shared boundary after the floor/wall pass. Destruction removes that
same identity, so the next collected frame contains neither pane nor a stale
presentation-side tombstone. Their frame spans the complete shared-edge run so
it meets neighboring structure, while the pane remains inset inside that
frame. The band projects from the mathematical edge into the feature's retained
structure-owner cell rather than swelling equally into both sides. Its visible
thickness may deliberately exceed the navigation boundary so the feature
remains legible at distance; presentation geometry does not redefine collision.
Ordinary building facades and Conquest bunker panes use this shared-edge path.
Wall-cell windows remain a separate aperture treatment for heavy compound
perimeters whose aperture genuinely occupies a thick structural cell.

Player squad selection and hover are ground circles in HIGHLIGHTS, below fog
and actor bodies. They follow each live member's rendered position and physical
radius rather than tinting its occupied cell. Selected members, the exact
primary, and hover previews have distinct emphasis. Hostiles receive no
selection circles; the collector honors current visibility and direct-control
suppression. The selection model stays in the UI tier and remains independent
of simulation membership and orders.

Persistent impact marks occupy DECALS beneath props and actors. Standalone
battles retain their GL accumulator; hosts without it submit the retained
source marks as ordinary textured commands using the same position, scale,
rotation and frame. Battle-review captures include that layer. This source
replay is bounded by the simulation's retained decal list, while an already
stamped live accumulator can retain older pixels after source eviction.
Persistent cell-backed terrain scars do not depend on that decal retention.

Projectile particle trails advance outside the draw collector. A fixed cadence
on each shot's simulation clock determines emissions, with positions sampled
along its traveled path. Live and headless hosts use the same emitter. A paused
shot emits nothing, and a resolved flight stops emitting while its existing
particles dissipate. Motor effects originate at the projectile tail; an
unpowered charge's authored glow stays at its center. Launch and impact audio
remain tied to physical events, never to the number of rendered particles.

Aircraft crash sections use the simulation's immutable first-contact pose and
battle time. They start joined at that pose, then spin and skid independently
into their permanent positions. Hull colors remain unchanged throughout the
airborne fall; the charred tint begins at ground contact. The active crash and
its surviving ground wreck
use the same source-sprite partitions and motion; entity removal, camera entry
and repeated draws cannot restart the animation. Sections occupy the ground
actor layer from contact onward, and the intact airborne shadow ends at contact.
Small sparks and chips are transient impact particles, separate from the main
sections that remain as the wreck. Their appearance does not add damage or
change the physical crash corridor.

## Standing laws

1. Paint order is semantic. Change `RenderLayer` order or same-layer producer order only after re-deriving the affected occlusion contract.
2. Collectors are per-frame, read-only presentation consumers. They never mutate simulation state or perform GL work.
3. The engine owns batching and GL containment; a custom pass owns its complete local GL lifecycle. No middle ground leaks state across the boundary.
4. Commands are ephemeral and pooled. They describe this frame's projection and must not become persistent entity data or a second source of position, health, or visibility. A decoration that would need to remember a previous frame — a trailing chip bar, a damage flash — needs a home for that state before it can exist, not a retained draw command.
5. Presentation identity is resolved render-side from stable conceptual identity. Asset handles and host graphics objects do not cross into the simulation tier.
6. Batch fewer calls without changing what paints on top. Grouping is valid only when it preserves strict painter order and required blend/texture state.
7. Dense terrain culling and future residency optimize the view, not the simulation. Navigation, LoS, walls, fog, occupancy, and saves remain cell-addressed.
8. A custom/FBO path is justified by a real target or stateful rendering need. Ordinary sprites, fills, lines, arcs, and ribbons belong in the auditable command vocabulary.
9. Camera zoom changes framing, never world ratios. Every actor, prop, overlay,
   and tile in one hosted scene derives from the same cell projection.
10. Headless evidence may substitute a graphics drain, never a scene model. A
    snapshot of an embedded battle scene must collect the same simulation,
    camera, selected render systems, command order, and authored assets as live
    — and **assets resolve the way the game resolves them**, the mod first and
    the installed game second, because a drain that can see fewer roots than the
    running game is showing a different scene. The install is already where the
    compile-only game jars come from, so this asks for nothing a build does not
    already have; where it is genuinely absent, what came from it is simply not
    drawn. A scene renderer and the canvas it draws into must agree on those
    roots, or a command is collected and then cannot be painted.
11. Ownership coding is redundant by construction. An allegiance is carried on hue *and* at least one non-color channel, so a busy field, a colorblind reader, and a pulled-back camera all still resolve whose unit it is. Decoration measured in screen pixels stays legible at any zoom; decoration measured in cells does not.
12. Each capacity reports on its own row against its own maximum. A reader asking "is the hull hurt?" must not have to subtract the armor capacity to find out, and a capacity's row stays comparable with the same capacity on every other unit on the field.
13. A quantised scale degrades by dropping a tier, never by smearing one, and it drops for the whole bar at once. A divider tier too fine to resolve at the current bar length is omitted entirely, so the bar falls back to coarser notches and then to none instead of turning into noise — and one row never ends up visibly finer than the row above it over a rounding error.
14. Decoration may be withheld until it carries news. An emplacement shows no bar until recorded fact says it has been fired on, so a quiet turret line reads as terrain rather than as a row of gauges. Withholding keys on something the simulation already records; the renderer never maintains its own idea of what has happened.
15. A battle-map preview is a camera and an annotation surface, never a renderer.
    It may add labels, guides, or diagnostic marks around the collected scene, but
    terrain, walls, apertures, doors, and props must come from the production
    render systems. Raw atlas and topology diagrams remain diagnostics and must
    not present themselves as battle-scene evidence.
16. A correction measured in screen pixels is bounded by the piece it corrects.
    A seam is about a pixel wide at any zoom, so closing one is a pixel-sized
    job — but applied to geometry that shrinks with the camera, a fixed pixel
    eventually dwarfs the sliver it was meant to touch up. Whichever bound bites
    is the right one: the pixel while the detail is visible, a fraction of the
    piece once it is not. Unbounded, the overlap that closes a wreck's seams at
    a readable zoom tripled every piece of it on a whole-map frame and fused the
    tears shut.
17. A bar belongs to whatever can be shot, not to one layer's cast. Anything carrying armor and structure wears the same gauge with the same ownership coding wherever it is drawn — infantry and emplacements in `UNITS`, drones in `DRONES`, convoy vehicles in `CONVOY` — so the player reads one instrument rather than a per-layer dialect. Each layer emits its bars as a sweep after its bodies, so no body paints over a neighbour's gauge.
18. A frame is measured through the shipping pipeline, not through a model of
    it. `renderEvidence` collects and drains the real `BattleRenderer` into a
    real OpenGL context and reports per layer and per framing; the Java2D
    evidence renderer re-implements the painting and could not report a texture
    bind if it wanted to. Counts are exact and must repeat frame to frame; times
    are a measurement and do not. The report says which layer is the ceiling, and
    a lever is judged against it rather than against an argument about it.
19. Detail follows zoom. A framing gate may withhold decoration — a body's cast
    shadow, a spent round's spark, a smoke field's scatter — once its subject is
    too few pixels to read. Thresholds are stated as constants naming the
    measurement that set them, they live in the collector so a gated layer
    collects nothing rather than draining to nothing, and paint order and world
    ratios are untouched (laws 1 and 9). A camera that cannot say how big a cell
    is withholds nothing: the gates drop what cannot be read, and not knowing is
    not that.
20. Ground that is a function of the topology is resident, not resubmitted. Base
    terrain is one quad per cell and cells do not overlap, so it is baked into
    buffers rather than streamed through the command path every frame, and a
    change to it is a patch of the affected cell and its four neighbours rather
    than a re-mesh. Every cell keeps its own sub-rectangle of the sheet it draws
    from, which is what makes this possible where merging runs is not — a merged
    run wants a repeat wrap and a tile cut from a packed sheet has none to give.
    A collector stays GL-free by asking a pure predicate whether the mesh
    already holds this battle's ground (law 2); the bake and every patch happen
    inside the layer's own custom pass at drain time (law 3), so the frame that
    bakes a battle also draws it the ordinary way and the mesh serves from the
    next one. Any failure at all — no buffer objects, a failed allocation, a GL
    error — returns the layer to the per-cell stream with the same picture.

    **The test is what a piece depends on, not whether it is a base tile.** The
    scatter laid over a cell and the decal that marks a doorway are as much a
    function of the topology as the tile beneath them, and they join the mesh as
    further **sub-layers**, patched from the same change log. What stays in the
    stream is what the mesh cannot express or cannot invalidate: solid fills and
    crosswalk stripes and window panes are colour rather than art, and
    shared-edge features are a live list a battle adds to and destroys from
    rather than a property of the grid.

    A sub-layer is a stratum of the mesh, at most one quad per cell, and
    sub-layer order is paint order — which is the whole of the contract between
    them, since within one a cell's quad cannot overlap its neighbour's. Where
    something that is *not* resident paints between two of them, the draw is
    split rather than the order bent: the fills and the stripes sit above the
    base terrain and below the scatter, so the mesh draws its base sub-layer,
    they are streamed, and the decoration sub-layers draw after. That works
    because no two cells' pieces overlap, so sweeping all the stripes before all
    the scatter is the same picture as interleaving them cell by cell.
21. What is derived per cell from resident data is resident too. The GROUND
    redirect's height and normal fields are a quad per cell over the same grid
    and change for the same reasons, so they are baked once per battle and
    patched from this topology's change log rather than rasterised again every
    frame. The consequence for the log is that it is **one list, not a list per
    reader**: a caved-in roof and a wall aperture move no ground tile and are
    recorded anyway, because the field that stands a cell at its roof is
    resident, and a tag one reader needs costs the other a re-resolve of five
    cells. `surface-relief-nouns.md` owns what those fields mean; what belongs
    here is that residency is the pipeline's answer to per-cell work of any
    kind, and that a resident consumer's invalidation is the topology's to
    record.
22. Residency is not only for what is static. A **fog field** is one map-sized
    single-channel texture, a texel per cell holding that cell's shadow, drawn
    as one quad over the map — because what fog reports is a scalar per cell,
    and a scalar per cell is a texture whether or not it changes. The rest of
    law 20 carries over unaltered: a host that declines residency or any GL
    failure at all returns the layer to the per-cell stream with the same
    picture, the collector stays GL-free by asking a pure predicate, and the
    bake and every patch happen inside the layer's own custom pass at drain
    time. What is new is where the invalidation comes from — the fog service's
    own record of where its reveal moved, since the topology has no opinion
    about who can see. A patch is that extent **grown by one cell**, because a
    revealed cell's shadow is feathered by how many of its four neighbours are
    dark and a cell going dark therefore changes the picture of cells whose own
    state never moved.
23. A scale two paths draw is quantised to the channel it lands in. Fog is
    painted into an eight-bit alpha channel whichever path paints it, so its
    levels have always been discrete; naming them is what lets a texel and a
    vertex colour land on the same byte instead of on two floats the driver
    rounds apart. This is not a general licence to round: it applies where one
    picture has two producers, and the failure it prevents is a seam that
    appears only at the values whose product sits on a rounding boundary.
24. A layer that cannot stop submitting can stop rebinding. Where residency is
    the answer to submitting the same thing again, a **ground atlas** is the
    answer to submitting different things that differ only by which sheet they
    came from: a run of quads coalesces exactly as far as its texture stays the
    same, so six sheets in painter order is five hundred flushes and one sheet
    is one. Both halves of the layer address it — what is resident bakes the
    atlas coordinate into its buffer, what is sparse carries it on the command —
    and the resolution stays single, so the two cannot disagree about where a
    cell's art lives. Paint order, destination rectangles and blend state are
    untouched, and a slot carries a gutter because bilinear reaches one texel
    and the sheet next door is a neighbour no sheet had before. Law 20's failure
    clause carries over: any failure at all returns every quad to its own sheet
    with the same picture. What this is not is a licence to merge sheets a
    *reader* distinguishes — an atlas is a submission fact, and the catalog that
    says which block is on which sheet (`moddable-tilesets-nouns.md`) is
    untouched by it. Which coordinate frame the two meet in is the whole risk
    and is not visible in the code: a source rectangle counts rows down from a
    sheet's top and GL counts them up from a texture's bottom, and a slot placed
    in the wrong one draws every tile as a real tile belonging to some other
    sheet. Nothing but a picture catches that, which is why the acceptance is
    pixel equality against the per-sheet path on a real driver.

    **An atlas is a mechanism, not a ground fact.** The same pack applies
    wherever a layer's pictures are many, small, and on screen together — a
    body's authored composition is feet, body, weapon, head and muzzle flash
    from separate images, so a field of infantry is several images per body and
    a bind for each. Atlased, a whole-sprite call becomes a rotated sheet quad
    over its own slot and the layer coalesces like any other run. **The redirect
    belongs at one seam**, where a command is recorded rather than in each
    composer: a dozen emit sites that each had to remember the atlas is a dozen
    chances for one of them to disagree, and none of them should know it exists.
    Per-unit tint and fade ride the batch's vertex colour, which is where they
    already were; an additive draw of a held image is a quad of the same sheet
    drained as its own run beside the normal one, since the blend is per run and
    not per texture. And the set is **bounded by what an image is for** rather
    than by what will fit: an atlas pays where many of its images are on screen
    at once, which a composed body is and a vanilla aircraft hull is not, so an
    image past a stated size is not held whole. It is held at **the size it is
    drawn**: the largest its draws give it at the camera's closest zoom, filtered
    down once into its slot, because texels past that are never on screen. A
    downed pose is 360x520 and is never 75 pixels tall on a 560x336 map; a
    mother bore is 736 across and reaches 442 on a 400x240 one. Only an image that
    is big on screen as well (a creep spray drawn larger than the screen) keeps
    drawing as a whole sprite.

    **What the unit layers draw is what the unit atlas holds.** Its membership is
    a rule over draws, not a list: any image drawn in `UNITS` or `UNIT_SHADOWS`
    that the atlas lacks, by any emit path, is noted at the seam and taken in
    before the next collect. The list the battle seeds it from only spares the
    first frames a rebuild. A seed list kept by hand was how turret collars and
    downed poses each came to cost a bind per body unnoticed; under the rule the
    only way out is being too big on screen, and the atlas report names what it
    left out and how big it was drawn.
    An image first drawn mid-battle goes into rows the plan kept free, so it costs
    one upload rather than a rebuild on the frame a marine first falls.

    **A whole image is held as far as its art reaches.** A body part is drawn on
    a canvas sized for every pose, and a station arm's 150x240 holds art of
    about 48x97, so the unit atlas keeps an image's alpha bounds and one texel of
    its own transparent margin, and a draw of the whole image becomes a draw of
    that part over the matching part of the destination, its corners taken from
    the whole quad's own. Every texel left out is transparent and beyond the one
    texel a bilinear tap reaches, so what is drawn is what the whole quad drew.
    The bounds are read from the image's file when the atlas is planned, because
    the plan runs before any GL, and checked against the texels the texture
    really holds at upload, where a miss is logged rather than drawn short. **A
    side is trimmed only while the two sides across from it hold no art at the
    image's edge.** Trimming a side moves the ends of the edges that meet it, and
    an edge rasterized between other ends lies a fraction of a subpixel away: a
    pixel centred on it can change sides. Along a transparent border nobody can
    see that; along an edge the art reaches, it is a pixel the sprite path left
    out, drawn faintly. So a rifle whose barrel runs the canvas's height keeps
    its side columns. What remains between a trimmed and an untrimmed image is
    the one-level rounding residue below, which a smaller triangle and a moved
    slot each reshuffle.

    **A cut carries its neighbourhood.** A whole image's edge was always an
    edge, so a transparent gutter changes nothing there; a rectangle cut from a
    sheet had the next cell's art one texel away, and bilinear reads it. So a
    cut's slot carries an apron of the sheet's own texels around it, clamped at
    the sheet's edge as the driver's clamp would answer, and the gutter starts
    beyond that. What is left between a cut and its sheet is the arithmetic of
    the address itself: a power-of-two atlas states its coordinates exactly
    where a sheet 896 wide rounds them, and a tap that lands on a filter-weight
    boundary in one lands beside it in the other. That is a one-level
    difference on isolated texels, never an edge or a slot, and it is the
    accepted residue of moving art between textures of different sizes.

    **An atlas that leaves something out says so.** The fallback is the image's
    own texture and the same picture, which is exactly why nobody sees it: an
    image over the cap, a layout with no room, a texture the driver refused all
    look like an atlas that is working. Every atlas keeps the account — what it
    left out and for which of those reasons — logs it once per plan, and the
    render evidence prints it beside the layer it would have served.
25. Residency is not only for the ground. A **roof** is a function of the
    topology and of which building owns the cell, and it paints *above* units —
    so the mesh is a resident layer in its own painter slot rather than another
    sub-layer of GROUND, and law 20 carries over whole. What is new is the
    invalidation's two shapes. A blow to a roof is geometry and comes from the
    same `CellTopology` change log, and it re-resolves the cell it names and the
    eight beside it, and no more: a cell's frame is its own, but its cover is cut
    by whether its neighbours are outside, which a fallen wall changes. A
    building fading as the player walks inside it is not geometry at all — it
    is that building's alpha — so its cells, floor then the walls it carries,
    are baked as one contiguous span of fixed slots per cell, and a fade is a
    patch of that span's vertex colours. The stream's own rule that a roof
    under a threshold is not drawn survives as an alpha of zero, or a resident
    quad would put a roof over an interior the streamed path leaves clear.
26. A collector visits what the camera can see, and pays for the visit once.
    Culling is the standing answer to a collector's cost the way residency is to
    a submitter's: a body whose drawn extent cannot land in the viewport is not
    composed, not looked up and not emitted, and a cell-backed pass is bounded
    by the visible rectangle. The extent is the drawn one and is passed by the
    caller that knows it — a mount's authored `visualCells`, a hull's resolved
    visual length, a shadow ellipse's own computed length — because a shared
    margin is a number that has to be kept in step with authored art and fails
    silently when it is not. A camera that cannot describe a viewport withholds
    nothing, exactly as a framing gate does.

    **The acceptance is pixel equality, because the failure is silent.** A
    command count says how much was collected and a timing says how long it
    took; neither can see a body that stopped being drawn, and a frame missing
    one looks like an ordinary frame. So a culled frame is compared against an
    unculled one through the shipping pipeline at every framing, with a body
    walked out through the viewport edge in half-cell steps so the step where a
    margin was a fraction too small is the step that fails.

    **Culling reaches the emit and not the walk, and the walk is usually the
    cost.** Culling `UNITS` cut what it collected at a close framing by seventy
    per cent and its collection time by a tenth of that, because five of its
    sweeps each walked the entire live roster asking every body on the field
    what type it was so as to skip the ones that were not their business. That
    is two thousand identity probes to emit a few dozen footprints, and it
    happens whether or not anything is emitted. Resolving the type **once**, in
    one pass that sorts the roster into the strata that want it, was two thirds
    of the layer's collection cost — a larger win than the culling it was
    supposed to be an accessory to. The general form: before culling a
    collector, ask what it does per body that culling cannot reach.

    **A spatial index is the wrong instrument for this particular walk**, and
    the reason is paint order rather than performance. Submission order is paint
    order (law 1), so a stratum must be filled in roster order; the index
    returns bucket order, and it returns ids where fog needs dense roster slots.
    Recovering both costs a lookup per body, which is what the walk it replaces
    already pays. It remains the right instrument for a proximity question,
    which this is not: this asks a fixed rectangle about every body, not a body
    about its neighbours.

27. A run closes for what a program cannot carry, not for which texture or
    which fill. Every textured quad and solid fill drains through one GLSL 1.20
    program (`PagedQuadRun`): a run binds up to eight textures as **pages**, each
    vertex names its page, and a fill is a quad of one opaque white texel on a
    page of its own, so a flame and the ember over it, or a body on the unit
    atlas and a prop on another sheet, are one draw. An additive quad shares the
    run too: every run draws premultiplied (`ONE, ONE_MINUS_SRC_ALPHA`),
    where an additive quad is one whose alpha is zero, so a disc and the glow
    over it are one draw. The alpha channel keeps the normal blend's factors, so
    the destination alpha a normal quad writes is unchanged, because the ground
    composite blends by its target's alpha; an additive quad leaves the alpha
    under it alone. A whole sprite, and each image of a mesh drawn without an
    atlas, is a quad of its own texture on a page: the quad `renderAtCenter`
    draws, its texture rectangle (its padded extent) upright, turned about its
    middle, its colour rounded to bytes as the engine sends it. Painter order is
    untouched; what still closes a run is a line, a ribbon or a custom pass, and
    a full page table. The
    shader is fixed-function `GL_MODULATE`, premultiplied, accepted on colour within
    three levels (the premultiplied rounding) against the per-sheet path
    (`PagedQuadRunGlEvidence`), and a context that cannot build it or offers
    fewer than two units drains one run per sheet, as before. GL 2.1 with no
    extension, because that is the Mac's floor; a texture array would need more.
    The page rides in the position's z rather than a generic attribute, because
    the fixed-function arrays are what an async-renderer bridge is known to
    carry, and per-quad effects ride in z's fraction for the same reason (a
    secondary-colour or normal array wants entry points the Mac's headless
    binding does not keep): positions and texture coordinates go at four
    components, and the spare ones carry the effects, zero for an ordinary
    quad, which then draws exactly as before. A body is stamped with them as
    it is emitted, every one a pure function of simulation state and the
    lights the screen already keeps: the **hit flash**, mixed toward white for
    a moment from the tick a hit lands (`HitFlash`, read off
    `CombatService.ticksSinceDamaged`, before cover and armour have their
    say); the **light** on it from the event lights, brightest on the
    silhouette edge facing them (`BodyEffects`); and its **wear**: embers
    while the ground under it burns, char once it has burnt. A body the cap
    retires fades out by alpha; a dissolve in blotches was tried and declined
    (owner, 2026-10-24). There is no selection outline: it was tried and the
    owner kept the ground rings (2026-10-24). There is no faction recolour: it needs an authored mask the art does not
    have, and each faction's art already carries its own colours. Only the
    paged run draws these; the per-sheet fallback and the Java2D review drain
    draw the body plain.

Authored spline roads retain a `RoadSurface` beside their grid footprint. The
surface samples signed distances at cell corners and clips each cell's two
triangles to the carriageway. Asphalt/concrete joins use the same clipping,
with first-authored-route precedence. UVs follow each retained vertex, so a
partial tile cuts the existing material instead of stretching it. The navigation
raster remains the movement authority.

The shared ground resolver paints captured underlying terrain, then two clipped
sublayers per material, before crossing marks, scatter and doors. Each resident
sublayer still owns at most one quad per cell. Interior pavement uses a full
quad; topology changes suppress road geometry where walls, fixtures or changed
ground take ownership. Hard aprons retain their full-cell ground. Legacy maps
with edge tags but no spline surface keep their neighbor-aware tile treatment.
Replacing the retained surface invalidates the ground mesh through topology's
ordinary change log. Maps without spline geometry keep the original layer count.

Natural escarpments use the same clipped cell-contour geometry as roads, with
retained layers for talus, shaded rock faces and the summit. These join the
ordinary resident ground mesh and its streamed fallback before scatter and
door decals. Fully covered lower bands submit no geometry. Grass summits use
the map's existing grass art; rock faces resolve named procedural material
pools. This is a top-down relief treatment, not displaced combat geometry or
an independently walkable elevated surface. Replacing its retained surface
invalidates the same topology change log.

Freight-apron berth paint derives from the simulation's actual `LandingPad`
footprints and purpose. Map-only hosts retain those same pads through
`BattleSetup.buildMap`; mission setup may subsequently select its active berths.

Headless world-sheet draws quantize shared destination pixel edges at fractional
camera scales. This prevents image-only black seams between adjacent tiles;
it does not change live GL geometry or navigation. Odd-sized sprite sources
also use their exact floating-point center rather than integer half extents.

Convoy presentation follows actual chassis motion. Bounded dust streaks appear
only behind moving live hulls at readable scale; these are not smoke, concealment
or a combat effect. Freight uses its own industrial vehicle art. Turret mounts
rotate in chassis coordinates independently of the sprite's authored heading.
A heavy turret may recoil from the age of its actual shots; that visual motion
never moves the authoritative ballistic hardpoint.
The headless sprite catalog loads every ground-vehicle variant through the same
asset paths and slicing as the live host, including projectile resources and
static turret base/barrel layers;
a close review changes camera framing,
not actor size or scene composition.

## Boundaries and extension paths

`combat-durability-nouns.md` owns armor and structure, what depletes them, and in which order; the bar only reports that model and must not invent a third capacity or a different drain order. Combat telemetry is the recorded-fact source a withheld bar consults. Allegiance is resolved render-side from simulation faction; the simulation never gains a presentation ownership field. The player's side is `MARINE` by standing convention across missions, so `MARINE` reads as player, `ALLY` as ally, `CIVILIAN` as neutral, and anything else as enemy. The ally reading gained its producer with the allied faction (`ai-nouns.md`, Sides); every colour site that used to key on `== MARINE` reads `Allegiance.friendly()` instead, so a fourth faction can never draw in the enemy hue by default.

`surface-relief-nouns.md` owns the ground-relief composite that may redirect the GROUND layer while preserving the render pipeline's order. `air-nouns.md` owns airborne behavior; this model only guarantees the layered presentation space it consumes. `vanilla-combat-bridge-nouns.md` owns the vanilla host and selects the bridge's subset of ground layers. `moddable-tilesets-nouns.md` owns tile catalog and generation mapping, while rendering resolves their authored visual identity.

The current renderer keeps a practical asset service behind `BattleSprites`. `unified-sprite-registry.md` is a possible render-only consolidation once its asset-path contract is ready. Static ground residency is settled by law 20 and is a mesh; `dense-render-tiles.md` remains parked for tiled **decal** residency only, and its baked-tile answer was measured against and rejected for ground — a tile costs fill and VRAM per view and needs residency, eviction and anti-thrash policy, where a mesh is one upload and no per-frame CPU at all. Merging identical cells into runs was rejected for the same measurement: a run re-splits on every edit and needs a repeat wrap that a tile cut from a packed sheet cannot give. Camera-Z or perspective is a separate projection decision, not an incidental optimization of the existing fitted 2D camera.

**The frame is bounded by the view at every framing, and what `renderEvidence`
names next is per-cell work.** Culling closed the gap between what a framing
shows and what the collectors visited: `UNITS` collected 527 commands at a
compound framing and at the whole map alike, and now collects 160 at the
compound; `COMPOUND` went from 98 to 6, `UNIT_SHADOWS` from 54 to 18. On the
560x336 map at a compound framing that is 0.47 ms of our own against 0.35, and
the layer that was the ceiling there is no longer collection-bound at all —
`UNITS` reads 0.05 ms of collection against 0.12 of drain. What remains is
`GROUND` at the whole map: 0.27 ms of collection over 929 commands, every one of
them a cell that genuinely is on screen. Culling cannot reach that by
construction, so the next lever is either a framing gate (law 19) over
decoration nobody can read at three pixels a cell, or cheaper per-cell
resolution — and the gate was already measured once, for the relief composite,
and shelved because the frame was under budget without it.

**The instrument was measuring its own warm-up, and finding that mattered more
than the lever.** At three discarded frames the 280x168 control reported
`GROUND` costing 0.36 ms of collection at a lane framing and 0.14 at the whole
map — four times the visible cells for a third of the cost, through the same
loop. Thirty discarded frames put it at 0.08 and the ordering came right, and
every figure early in a run fell by more than half. Two of the three
"regressions" this lever appeared to cause were that gradient, and so was a
`GROUND` cost that had been read as evidence for weeks. A control run is only
worth what the instrument's own spread allows: it is now about 0.06 ms per
layer, which is most of what separates a culled frame from an unculled one at
the framings where culling rejects nothing.

**Culling reaches the emit, and the walk is where the cost was.** Rejecting
seventy per cent of what `UNITS` collected moved its collection time by a
tenth of that, because five of its sweeps each walked the whole live roster
asking every body what type it was — two thousand identity probes to emit a few
dozen footprints, paid whether or not anything was emitted. Resolving the type
once, in one pass, was two thirds of the layer's collection cost. Both halves
ship, and the accessory turned out larger than the lever; the general lesson is
law 26's.

**And a guard can cost more than what it guards.** The cull in the live-sprite
sweep is four float comparisons, and as a method call inside that sweep's very
large composition loop it cost a quarter of a microsecond per body — thirty
times what the comparisons can amount to — at the one framing where it rejects
almost nothing and therefore always runs to completion. Hoisted to primitive
locals it disappears. A test cheap in isolation is not cheap in every loop.

The guess this replaced is worth keeping for what it got wrong. It named
merging as the lever, which was right, and then predicted the win would be in
the drain, which was only half of it: the atlas took the drain from 2.70 ms to
0.66 and left collection untouched at 1.5, and it was residency — the thing the
paragraph explicitly ruled out for sparse decoration — that took the collection
away too. The two levers are very nearly redundant on this measurement, and both
ship anyway: with the decoration resident the atlas saves almost nothing at
whole-map framing, and it is still what makes the bake frame, every host that
declines residency, and the whole fail-soft path cheap. A lever that is
subsumed at one framing is not a lever that does nothing.

The guess this replaced is worth keeping, because it was wrong in an instructive
way. The doc said residency was unlikely to be fog's answer, since what fog
reports is exactly the thing that changes every frame. What that missed is that
*changing* and *being rebuilt* are different: the reveal bitmap changes on the
vision cadence, roughly a third of the render frames, and it changes in one
bounded region rather than everywhere. Residency is about not resubmitting, and
the question to ask of a layer is how often its data actually moves and how much
of it moves at once — not whether it is static.

Framing-gating the relief composite (law 19) was the other candidate and was
measured rather than argued: at whole-map the sun's terrain shading genuinely
is not readable — a wall is a pixel wide and its shadow is lost in the tile
noise — while at a lane's framing it plainly is. It is not shipped, because the
resident fields alone put the frame five times under the budget the gate was
proposed for, and a gate that buys nothing is a second picture to maintain.
