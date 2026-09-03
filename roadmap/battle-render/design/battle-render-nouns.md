# Battle Rendering

Status: ACTIVE — the layered command pipeline is shipped; asset consolidation and view-resident dense rendering remain deliberately unqueued extensions.

Written: 2026-08-23

Updated: 2026-09-02 — a frame is measured through the shipping pipeline before
it is spent, detail is withheld at framings it cannot be read at, and the static
ground is resident on the GPU instead of resubmitted every frame.

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
- The **resident ground** is the battle's static base terrain, baked once into
  vertex buffers and drawn from them. One buffer per sheet; every cell that draws
  a base tile owns four vertices in it and keeps them, in cell coordinates, so the
  camera is a modelview transform rather than a pass over the data. A cell that
  changes is patched in place over its own slot.
- A **visible cell rectangle** is the camera-derived dense-world cull. It reduces work for cell-backed terrain passes; it does not replace the simulation's cell grid.
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
20. Static ground is resident, not resubmitted. Base terrain is one quad per
    cell and cells do not overlap, so it is baked into buffers rather than
    streamed through the command path every frame, and a change to it is a patch
    of the affected cell and its four neighbours rather than a re-mesh. Every
    cell keeps its own atlas sub-rectangle, which is what makes this possible
    where merging runs is not — a merged run wants a repeat wrap and an atlas has
    none to give. What is resident is only the cell's own base tile: fills,
    stripes, scatter, doorway decals, panes and shared-edge features are sparse,
    several of them straddle two cells, and they stay in the command stream. A
    collector stays GL-free by asking a pure predicate whether the mesh already
    holds this battle's ground (law 2); the bake and every patch happen inside
    the layer's own custom pass at drain time (law 3), so the frame that bakes a
    battle also draws it the ordinary way and the mesh serves from the next one.
    Any failure at all — no buffer objects, a failed allocation, a GL error —
    returns the layer to the per-cell stream with the same picture.

## Boundaries and extension paths

`combat-durability-nouns.md` owns armor and structure, what depletes them, and in which order; the bar only reports that model and must not invent a third capacity or a different drain order. Combat telemetry is the recorded-fact source a withheld bar consults. Allegiance is resolved render-side from simulation faction; the simulation never gains a presentation ownership field. The player's side is `MARINE` by standing convention across missions, so `MARINE` reads as player, `ALLY` as ally, `CIVILIAN` as neutral, and anything else as enemy. The ally reading gained its producer with the allied faction (`ai-nouns.md`, Sides); every colour site that used to key on `== MARINE` reads `Allegiance.friendly()` instead, so a fourth faction can never draw in the enemy hue by default.

`surface-relief-nouns.md` owns the ground-relief composite that may redirect the GROUND layer while preserving the render pipeline's order. `air-nouns.md` owns airborne behavior; this model only guarantees the layered presentation space it consumes. `vanilla-combat-bridge-nouns.md` owns the vanilla host and selects the bridge's subset of ground layers. `moddable-tilesets-nouns.md` owns tile catalog and generation mapping, while rendering resolves their authored visual identity.

The current renderer keeps a practical asset service behind `BattleSprites`. `unified-sprite-registry.md` is a possible render-only consolidation once its asset-path contract is ready. Static ground residency is settled by law 20 and is a mesh; `dense-render-tiles.md` remains parked for tiled **decal** residency only, and its baked-tile answer was measured against and rejected for ground — a tile costs fill and VRAM per view and needs residency, eviction and anti-thrash policy, where a mesh is one upload and no per-frame CPU at all. Merging identical cells into runs was rejected for the same measurement: a run re-splits on every edit and needs a wrap the atlas cannot give. Camera-Z or perspective is a separate projection decision, not an incidental optimization of the existing fitted 2D camera.

`surface-relief-nouns.md` owns the next ceiling. With the ground resident, a whole-map Conquest frame's remaining cost is almost entirely its relief composite — the height and normal fields are rebuilt per frame as a quad per visible cell, twice, which is the same shape of cost the ground has just stopped paying. `relief-field-budget.md` holds that work.
