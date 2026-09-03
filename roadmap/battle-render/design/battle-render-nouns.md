# Battle Rendering

Status: ACTIVE — the layered command pipeline is shipped; asset consolidation and view-resident dense rendering remain deliberately unqueued extensions.

Written: 2026-08-23

Updated: 2026-09-03 — collectors visit what the camera can see (law 26), so a
compound framing collects a compound's worth of bodies rather than the whole
map's; the frame is bounded by the view at every framing and the remaining
ceiling is `GROUND`'s per-visible-cell work at the whole map.

## Vocabulary

- The **battle render pipeline** is the presentation path from current battle state to a painted world frame. It is a per-frame view of the simulation, not simulation state or a second gameplay authority.
- The **game render tier** decides what to show and in what order. It owns battle-specific layers, producers, presentation policy, and the frame context.
- The **render engine** is the reusable mechanism that projects coordinates, buffers commands, batches primitives, and brackets hostile GL state. It does not know what a unit, roof, faction, or objective means.
- A **render layer** is one named stratum in the world stack. Its ordinal is paint order; it is an occlusion contract, not a depth-sort hint.
- A **prop** is scenery: authored, placed at generation, drawn from a sheet, and never a simulation actor. Size and subject do not promote one — a parked truck draws in the same layer, from the same registry, and with the same cover model as a crate. A second render path for scenery that merely looks important is duplication, and it drifts: the one this replaced had grown its own list, its own sheet cache, its own footprint stamp, and a cover rule nothing else in the game used.
- A **render system** is a GL-free, per-frame producer. It reads the frame context and appends commands for its one layer without putting render data on simulation entities.
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

## Ownership and flow

`BattleScreen` owns the session loop, input, audio, host chrome, and the outer scissor boundary. For each world frame it supplies a frame context to the battle renderer. The renderer asks its ordered systems to collect the current presentation into one draw list, then drains selected layers in `RenderLayer` order.

Systems pull fresh state every render frame, so camera motion, interpolation, visibility fades, recoil, and live pose remain responsive even when the simulation advances at a lower cadence. Simulation objects carry conceptual identity and state; render-side flyweights and the asset service resolve that identity into sheets, sprites, frames, and presentation policy. No simulation entity owns a `SpriteAPI` or a mutable renderer handle.

Within a layer, producer submission order is paint order. Across layers, enum order is paint order. The drain may batch adjacent commands with the same compatible primitive and state, but it flushes whenever batching would invert that order. Every loaded sheet that can emit a sheet-quad command must be registered with the live drain after that sheet becomes available; a host whose asset lifecycle loads a sheet after its terrain batches are built registers it at that later lifecycle seam. A foreign sprite render or a custom pass is treated as GL-state pollution until the engine has re-established the state required by the next batch.

The standalone host normally renders every layer. A host can request a subset through the same pipeline, but it must supply the camera and context each selected producer needs; omitting a layer does not manufacture unavailable state. This is how the combat bridge presents ground content in vanilla combat without forking the ground renderer. The Mech Lab uses the seam as `GROUND + DOODADS`, then `UNITS`; Barracks adds `SHOTS` to its actor pass so retained overlays can sit between physical room content and live range activity. The live host drains those commands through Starsector/OpenGL; headless UI evidence collects the same systems and replays their sprite, sheet-quad, fill, and line commands through Java2D. The headless camera receives the canvas's actual content-box extent, and its resolved commands paint one-for-one in that box; the authored canvas surface must not apply a second non-uniform stretch that turns square battle cells into rectangles. Its embedded frame profile suppresses HP bars and surface-relief FBO work; fog is absent because these hosts do not request the fog layer.

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

The current world order is `GROUND → DECALS → VEHICLES → DOODADS → HIGHLIGHTS → FOG → UNITS → HAZARDS → SMOKE → ROOFS → DRONES → OBJECTIVES → COMPOUND → CONVOY → SHUTTLES → SHOTS → IMPACT_FX → FLYBY`. The enum is the authority for this order; the sequence here makes the standing occlusion contract legible without replacing it. A cell that current clear-air observation would reveal but smoke actually conceals receives half the ordinary fog shadow, then smoke paints above it with a bounded opacity boost. This exposes a muted terrain silhouette as context while leaving the actual cell unrevealed and its actors hidden; naturally unseen smoke remains under ordinary fog.

Ground is a dense, cell-backed surface. Current camera culling range-loops the visible cell rectangle for dense passes and AABB-rejects eligible sparse scenery. This preserves cell truth while avoiding off-camera collection. If terrain or decal work becomes the measured ceiling again, future dense render tiles may cache a view-resident projection of cell blocks. A tile is a derived, view-admitted presentation block, never a new simulation grid or coordinate system. Ground and decals may keep separate backing while sharing tile addressing, invalidation, and eviction policy. Evicted ground rebuilds from cells and evicted decals replay retained sources; unavailable tile backing falls back locally to the present cell path without changing paint order.

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
    already were; an additive draw is left on the sprite path, because its blend
    state is its own. And the set is **bounded by what an image is for** rather
    than by what will fit: an atlas pays where many of its images are on screen
    at once, which a composed body is and a vanilla aircraft hull is not, so an
    image past a stated size keeps drawing as a whole sprite.
25. Residency is not only for the ground. A **roof** is a function of the
    topology and of which building owns the cell, and it paints *above* units —
    so the mesh is a resident layer in its own painter slot rather than another
    sub-layer of GROUND, and law 20 carries over whole. What is new is the
    invalidation's two shapes. A cave-in is geometry and comes from the same
    `CellTopology` change log, and it re-resolves **exactly the cell that
    caved**: a roof tile is picked by hashing its own coordinates, so unlike a
    ground autotile it has no neighbour to disturb. A building fading as the
    player walks inside it is not geometry at all — it is that building's alpha
    — so its cells are baked as one contiguous span and a fade is a patch of
    that span's vertex colours. The stream's own rule that a roof under a
    threshold is not drawn survives as an alpha of zero, or a resident quad
    would put a roof over an interior the streamed path leaves clear.
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
