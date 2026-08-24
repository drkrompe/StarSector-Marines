# Surface Relief

Status: ACTIVE — semantic terrain relief and presentation-only event lighting share one material-aware ground composite.

Written: 2026-08-23

Updated: 2026-08-24 — replaced acceptance chronology with durable presentation boundaries.

## Purpose

Surface relief gives the flat battlefield a readable sense of height without
changing navigation, collision, targeting, or simulation. It combines a small
screen-space displacement of ground color with normal-aware illumination from
short-lived battle events. Both are presentation interpretations of authored
terrain and event data, never a second physical model of the map.

## Vocabulary and ownership

- **Macro relief** is the semantic, cell-scale height of a surface: tall wall,
  raised interior, ordinary ground, low rubble, or water. Map/tile mapping
  data owns that meaning because pixels cannot say whether a bright region is
  a wall or a pale floor.
- **Micro relief** is the derived, per-texel texture variation from a source
  albedo. The build-time derivation pipeline owns the height and normal assets;
  it is deterministic source processing, not runtime inference.
- A **material signal** carries macro relief, micro relief, water identity,
  and shore proximity separately. Keeping the signals distinct leaves
  structural depth, surface texture, and water motion independently tunable.
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
matching normal samples are assembled into parallel viewport-sized targets.
The fullscreen composite first chooses a bounded parallax/water coordinate,
then samples ground color and normal at that same coordinate. It adds the
selected event lights after the accepted ground image; units, shots, effects,
and UI draw later on their ordinary paths.

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
- Shader, texture, or framebuffer failure must fail soft to the unmodified
  ground drain. A visual enhancement may disappear, but it may not suppress or
  double-draw ground.
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

## Boundaries

`moddable-tilesets-nouns.md` owns tile and mapping authority; surface relief
only consumes the semantic surface classification and selected source art.
The battle renderer owns target construction, shader state, and graceful
fallback. Combat effects own event occurrence; this feature only turns their
presentation events into transient illumination. The battle-render feature
owns the broader draw-list and layer-order model.
