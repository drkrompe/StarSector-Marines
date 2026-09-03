# Surface Relief

Status: ACTIVE — semantic terrain relief, a directional sun, and presentation-only event lighting share one material-aware ground composite.

Written: 2026-08-23

Updated: 2026-09-02 — the height and normal fields are resident and patched from the cell change log instead of rebuilt every frame.

## Purpose

Surface relief gives the flat battlefield a readable sense of height without
changing navigation, collision, targeting, or simulation. It combines a small
screen-space displacement of ground color, shadows cast by a directional sun,
and normal-aware illumination from short-lived battle events. All three are
presentation interpretations of authored terrain and event data, never a second
physical model of the map.

## Vocabulary and ownership

- **Macro relief** is the semantic, cell-scale height of a surface: tall wall,
  raised interior, ordinary ground, low rubble, or water. Map/tile mapping
  data owns that meaning because pixels cannot say whether a bright region is
  a wall or a pale floor.
- Macro relief is measured in **metres above a ground datum**, on the world's
  existing anchor of one cell to one metre. It was an invented 0..1 scale, and
  the change is not cosmetic: it is what makes the sun's geometry
  self-calibrating, since a wall's shadow is `height / tan(elevation)`
  metres and a metre is a cell. It also makes each number answerable — a
  building floor is a slab's step above the yard, not "0.65".
- The **sun** is a single directional light with a bearing and an elevation.
  It casts and nothing else: it does not tint the scene, does not light
  surfaces by their normals, and has no time-of-day authority. Its elevation
  is the only thing that sets how far a shadow reaches.
- A **cast shadow** is the sun's occlusion of ground, found by walking the
  macro-relief field toward the sun and comparing what stands there against a
  ray climbing at the sun's elevation. It is an interpretation of macro
  relief, so anything with an authored height casts — walls because they are
  tall, not because they are walls.
- A **roof** is what a building casts with. Its interior floor is a floor, but
  the thing standing between the sun and the ground is the roof, so a roofed
  interior stands at roof height and the building casts as a solid block rather
  than as a hollow outline of its own walls. A cell whose roof has caved in
  drops back to its floor, which is what puts daylight into a breached building
  and lays the intact rim's own shadow across the hole. Roof *visibility* is a
  separate thing and changes nothing: a roof faded out so the player can see
  inside is still standing, and reading that fade would make a building's
  shadow pulse with what the player happens to know.
- A **window** is an aperture, and in a height field the way to let light
  through one is to lower it. A window cut into a thick structural wall stands
  at its **sill**: light passes over the sill and lands behind the opening
  while the full-height wall either side keeps its shadow. A window on a shared
  edge needs nothing — the aperture there is the floor gap between two wall
  cells, already at the datum, so the light is already through it. Giving that
  pane a height would mean claiming a walkable cell it does not occupy.
- The **shadow margin** is the band of off-view cells the macro-relief target
  carries beyond the viewport. An occluder has to be in that target to cast
  out of it, so the margin is what stops a wall just past the sun-ward edge
  from having no shadow until the camera reaches it.
- **Micro relief** is the derived, per-texel texture variation from a source
  albedo. The build-time derivation pipeline owns the height and normal assets;
  it is deterministic source processing, not runtime inference.
- A **material signal** carries macro relief, micro relief, water identity,
  and shore proximity separately. Keeping the signals distinct leaves
  structural depth, surface texture, and water motion independently tunable.
- A **relief field** is one of the two per-cell inputs the composite samples:
  the material/height target and the normal target. Both are a quad per cell —
  the cell's own material signal, over the cell's own rectangle of its derived
  atlas — and both are **resident**, baked once per battle into vertex buffers
  in cell coordinates and drawn from them, so the camera is a modelview
  transform rather than a pass over the map. A field is not the composite: the
  composite is a fullscreen pass that reads them.
- A **normal map** expresses local surface direction for light response. It is
  a presentation asset; unsupported or absent art has a flat normal rather
  than an invented shape.
- **Parallax** is a ground-plane displacement in the fullscreen composite. It
  creates apparent perspective from a virtual eye around the screen center;
  it is not movement of map cells or sprites.
- A **ground light** is a short-lived render-side response to a visible battle
  event. Its owner bounds lifetime, merges nearby matching flashes, culls to
  the camera, and selects a fixed nearest-visible shader budget.
- **Unit relief** is a separate future concern. Units sit above the parallaxed
  ground and may receive lighting later, but are never displaced by this
  feature.

## Flow

During asset production, terrain albedo is transformed into height and normal
atlases. Atlas cells are derived independently with clamped edges while their
normalization range remains shared across the sheet, preserving both cell
boundaries and relative material scale.

During a battle render, the ordinary ground image, material signals, and
matching normal samples are assembled into parallel targets. Colour and normal
are the size of the viewport; the material target alone carries the shadow
margin, because it is the only one the sun's march reads and widening the
others would redraw the whole ground layer over an area several times the view
to feed sampling that never touches them. The composite therefore addresses
material by world position rather than by its own screen coordinate.

The fullscreen composite first chooses a bounded parallax/water coordinate,
then samples ground color and normal at that same coordinate. It applies the
sun's cast shadow to that image, then adds the selected event lights — in that
order, so a muzzle flash still lights ground that stands in shade. Units,
shots, effects, and UI draw later on their ordinary paths.

Battle presentation translates muzzle flashes, traveling bolt bodies, impacts,
heavy impacts, and fire bursts into ground lights. Bolt lights follow the live
render-side shot pose until arrival; every light lifetime follows scaled battle
time, so pause and time-speed authority remains outside this feature. The debug
dials only tune presentation strengths for the current screen; they do not
alter map data or simulation.

## Laws

- Surface relief cannot alter gameplay state. It neither creates terrain
  height for navigation nor exposes visibility, collision, or combat data.
- Screen-space composition is required for sliced atlases: sampling after
  ground is assembled guarantees spatial-neighbor sampling and prevents an
  offset from bleeding into a neighboring atlas cell.
- Macro semantics and derived micro detail remain separate through the
  material pass. A missing height asset preserves macro/water behavior with
  neutral micro relief; an independently missing normal asset uses a flat
  normal.
- Water is semantic, not color-detected. Its relief is restrained, waves are
  world-anchored, shore motion is bounded, and a displaced water lookup may
  not borrow land color.
- Ground lights are additive and bounded. No active lights, or zero lighting
  strength, preserves the ordinary unlit ground composite; lights do not cast
  shadows or become a visibility system.
- Macro height is a physical claim, never a visibility one. What a cell stands
  at follows what is built there — walls, intact roofs, apertures — and never
  what any faction can currently see. A height that moved with the fog would be
  a shadow reporting knowledge.
- The sun casts on the ground plane only, and its shadows are not sight. They
  never darken units, never gate what a unit can see or be seen from, and are
  computed from presentation data no simulation reads. A marine standing in a
  wall's shadow is neither hidden nor harder to hit.
- The sun's reach is bounded and its cost is fixed. The march takes the same
  number of samples at every angle, its distance is read from the tallest
  height the installed mapping can place rather than from a constant, and the
  elevation dial is floored well above the horizon because the reach is a
  tangent. A shadow longer than the march is truncated rather than sampled
  coarsely: one reads as a shadow, the other as dashes.
- Zero shadow strength is not merely an unlit composite. It also collapses the
  material target's margin back to the ordinary geometry halo, so the whole
  feature costs neither fill nor memory when it is dialled off.
- A relief field is invalidated, never rebuilt. What a per-frame rebuild bought
  was the right answer after a roof caved in, and it bought it by redrawing the
  whole map twice a frame — which at whole-map framing on the canonical Conquest
  was the entire remaining cost of the frame once the ground itself had gone
  resident. The cells that can change are already recorded one by one, so a
  field is baked once per battle and patched over the changed cell and its four
  neighbours, the neighbours because a derived atlas rectangle is chosen from
  what stands beside the cell. It follows that **a field can only read what the
  change log carries**: a caved-in roof and a wall aperture move no ground tile
  and are recorded anyway, because the thing that stands a cell at its roof or
  its sill is resident too. A field that read some other authority would go
  stale silently, and a stale height field is a building shadowing ground it no
  longer covers.
- A field carries its material signal at the target's own precision. The
  targets are RGBA8, so the signal is eight bits per channel wherever it is
  held; storing it wider only moves where the rounding happens, and it costs
  four times the buffer to do so. The one visible consequence is that a
  mid-channel value quantises once rather than twice, which moved the shipped
  composite by at most 2 of 255 on a few per cent of its pixels.
- Shader, texture, or framebuffer failure must fail soft to the unmodified
  ground drain — and so must residency: any failure at all returns the field to
  the per-cell rasterisation with the same picture. A visual enhancement may
  disappear, but it may not suppress or double-draw ground.
- Parallax belongs only to the ground plane. Future unit lighting must rotate
  sprite-space normals with the unit and use its own render path.

## Presentation boundaries

Derived micro assets, macro semantics, water identity, and parallax remain
distinct material inputs. Ground lights sample the final parallax coordinate
with color and normals, use a fixed nearest-visible additive budget, and never
become simulation or visibility authority.

Structures and mixed indoor art remain macro-only surfaces with flat normals.
This is a deliberate quality boundary, not an asset-loading failure. Any unit
lighting uses a separate sprite-normal path and never displaces units with
ground parallax.

Every wall is one height, and so is every roof. A compound's perimeter and a
habitat's outer shell cast the same shadow, and a single-storey shed casts as
far as a warehouse. That is the current limit of the model rather than a
property of the world; per-surface and per-building heights are the next
authoring step, and the mapping's override table is already keyed to accept
them. Nor do units cast: the composite runs beneath them, so a marine and a
truck lay down nothing. All are quality boundaries a later story may move,
not defects in this one.

## Boundaries

`moddable-tilesets-nouns.md` owns tile and mapping authority; surface relief
only consumes the semantic surface classification and selected source art.
The battle renderer owns target construction, shader state, and graceful
fallback. Combat effects own event occurrence; this feature only turns their
presentation events into transient illumination. The battle-render feature
owns the broader draw-list and layer-order model.
