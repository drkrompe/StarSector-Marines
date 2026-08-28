# Battle Rendering

Status: ACTIVE — the layered command pipeline is shipped; asset consolidation and view-resident dense rendering remain deliberately unqueued extensions.

Written: 2026-08-23

Updated: 2026-08-28 — generated-map evidence enters through the ordinary battle
scene, and the durability bar is emitted by every layer that draws something
shootable, convoy vehicles included.

## Vocabulary

- The **battle render pipeline** is the presentation path from current battle state to a painted world frame. It is a per-frame view of the simulation, not simulation state or a second gameplay authority.
- The **game render tier** decides what to show and in what order. It owns battle-specific layers, producers, presentation policy, and the frame context.
- The **render engine** is the reusable mechanism that projects coordinates, buffers commands, batches primitives, and brackets hostile GL state. It does not know what a unit, roof, faction, or objective means.
- A **render layer** is one named stratum in the world stack. Its ordinal is paint order; it is an occlusion contract, not a depth-sort hint.
- A **render system** is a GL-free, per-frame producer. It reads the frame context and appends commands for its one layer without putting render data on simulation entities.
- The **frame context** is the current simulation view plus camera, layout, alpha, selection/highlight state, and host-owned frame inputs. It is temporary and is not retained as gameplay state.
- The **draw list** is the pooled, per-frame command buffer. A **draw command** describes one presentational operation: a sheet quad, whole sprite, solid geometry, line, ribbon, polygon, or a bounded own-GL escape.
- The **drain** replays each layer's commands in strict submission order while coalescing only adjacent compatible work. It turns a deferred command stream into GL calls without changing the painter's meaning.
- A **custom command** is an explicit boundary for a pass that owns its own GL lifecycle or persistent target, such as an FBO-backed accumulator. It is not a shortcut for ordinary geometry.
- A **sheet quad** is a sub-rectangle batched from a shared sheet; a **sprite** is a whole texture rendered through the host API. They are presentation forms, not simulation identity.
- A **render appearance** is a type-shared render-side description of what an entity kind can draw. Dynamic pose, health, visibility, and interpolation remain current simulation inputs.
- An **allegiance** is the presentation reading of a unit's simulation faction from the player's chair: player, ally, neutral, or enemy. Faction is the side a unit fights for; allegiance is how the person watching should read it. The number of ownership buckets a player can distinguish at a glance stays four however many factions the simulation fields.
- A **durability bar** is the ownership-coded gauge above an entity reporting its remaining combat durability. It carries one **row** per pool — structure below, armor above it, since armor is spent first — and each row fills against its own maximum, so a pool at full reads as full whatever the other is doing. It is a per-frame read of current pools, never a second durability authority.
- A **notch** is one fixed quantity of a pool marked off along its row. It is deliberately not called a cell: cells are the simulation's grid, and a notch measures durability, not space. The quantity is per pool and identical for every entity in the battle, so notch count and density read magnitude directly: a marine is one notch, an emplacement a handful, a heavy mech a full comb. Notches measure the entity, not the bar — a longer bar shows the same notches further apart. Armor and structure take different quantities because the authored pools differ in size; forcing one scale on both leaves the larger pool illegible.
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

Within a layer, producer submission order is paint order. Across layers, enum order is paint order. The drain may batch adjacent commands with the same compatible primitive and state, but it flushes whenever batching would invert that order. A foreign sprite render or a custom pass is treated as GL-state pollution until the engine has re-established the state required by the next batch.

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

The current world order is `GROUND → DECALS → VEHICLES → DOODADS → HIGHLIGHTS → FOG → UNITS → ROOFS → DRONES → OBJECTIVES → COMPOUND → CONVOY → SHUTTLES → SHOTS → IMPACT_FX → FLYBY`. The enum is the authority for this order; the sequence here makes the standing occlusion contract legible without replacing it.

Ground is a dense, cell-backed surface. Current camera culling range-loops the visible cell rectangle for dense passes and AABB-rejects eligible sparse scenery. This preserves cell truth while avoiding off-camera collection. If terrain or decal work becomes the measured ceiling again, future dense render tiles may cache a view-resident projection of cell blocks. A tile is a derived, view-admitted presentation block, never a new simulation grid or coordinate system. Ground and decals may keep separate backing while sharing tile addressing, invalidation, and eviction policy. Evicted ground rebuilds from cells and evicted decals replay retained sources; unavailable tile backing falls back locally to the present cell path without changing paint order.

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
    camera, selected render systems, command order, and authored assets as live.
11. Ownership coding is redundant by construction. An allegiance is carried on hue *and* at least one non-color channel, so a busy field, a colorblind reader, and a pulled-back camera all still resolve whose unit it is. Decoration measured in screen pixels stays legible at any zoom; decoration measured in cells does not.
12. Each pool reports on its own row against its own maximum. A reader asking "is the hull hurt?" must not have to subtract the armor pool to find out, and a pool's row stays comparable with the same pool on every other unit on the field.
13. A quantised scale degrades by dropping a tier, never by smearing one, and it drops for the whole bar at once. A divider tier too fine to resolve at the current bar length is omitted entirely, so the bar falls back to coarser notches and then to none instead of turning into noise — and one row never ends up visibly finer than the row above it over a rounding error.
14. Decoration may be withheld until it carries news. An emplacement shows no bar until recorded fact says it has been fired on, so a quiet turret line reads as terrain rather than as a row of gauges. Withholding keys on something the simulation already records; the renderer never maintains its own idea of what has happened.
15. A battle-map preview is a camera and an annotation surface, never a renderer.
    It may add labels, guides, or diagnostic marks around the collected scene, but
    terrain, walls, apertures, doors, and props must come from the production
    render systems. Raw atlas and topology diagrams remain diagnostics and must
    not present themselves as battle-scene evidence.
16. A bar belongs to whatever can be shot, not to one layer's cast. Anything carrying the durability pools wears the same gauge with the same ownership coding wherever it is drawn — infantry and emplacements in `UNITS`, drones in `DRONES`, convoy vehicles in `CONVOY` — so the player reads one instrument rather than a per-layer dialect. Each layer emits its bars as a sweep after its bodies, so no body paints over a neighbour's gauge.

## Boundaries and extension paths

`combat-durability-nouns.md` owns the two pools, what depletes them, and in which order; the bar only reports that model and must not invent a third pool or a different drain order. Combat telemetry is the recorded-fact source a withheld bar consults. Allegiance is resolved render-side from simulation faction; the simulation never gains a presentation ownership field. The player's side is `MARINE` by standing convention across missions, so `MARINE` reads as player, `CIVILIAN` as neutral, and anything else as enemy. The ally reading has no producer until the simulation fields a friendly non-player faction — the same allied-contributor gap `fog-of-war-nouns.md` law 2 already names — and is styled ahead of that work so the presentation side needs no second design pass when it lands.

`surface-relief-nouns.md` owns the ground-relief composite that may redirect the GROUND layer while preserving the render pipeline's order. `air-nouns.md` owns airborne behavior; this model only guarantees the layered presentation space it consumes. `vanilla-combat-bridge-nouns.md` owns the vanilla host and selects the bridge's subset of ground layers. `moddable-tilesets-nouns.md` owns tile catalog and generation mapping, while rendering resolves their authored visual identity.

The current renderer keeps a practical asset service behind `BattleSprites`. `unified-sprite-registry.md` is a possible render-only consolidation once its asset-path contract is ready. `dense-render-tiles.md` is the only future vehicle for static-ground baking and tiled decal residency; it supersedes the single-world FBO idea. Camera-Z or perspective is a separate projection decision, not an incidental optimization of the existing fitted 2D camera.
