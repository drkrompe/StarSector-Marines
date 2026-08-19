# Surface Relief — parallax + per-texel lighting for the 2D battlefield

## Concept

Give the flat top-down battlefield apparent depth using two composable
per-pixel effects, both driven by the same auto-generated height/normal data:

1. **Parallax mapping with offset limiting** (Welsh 2004) — offset each
   ground pixel's sample coordinate by its height so tall features (walls,
   buildings, rubble) lean away from the screen center and slide against the
   ground as the camera pans/zooms, while low features (water, craters) sink.
2. **Per-texel dynamic lighting** (bump mapping) — light the ground and,
   later, units from muzzle flashes and explosions using derived normal maps.

Reference paper: *Parallax Mapping with Offset Limiting: A Per-Pixel
Approximation of Uneven Surfaces*, Terry Welsh, 2004.

## Key adaptations for our renderer

### Fake-perspective eye vector

A straight-down orthographic camera has eye vector `(0,0,1)` everywhere —
zero parallax. We fake a perspective eye: a virtual camera point at finite
height above the screen center, so each pixel's tangent-space eye vector is
`normalize(screenCenter - pixelPos, eyeHeight)`. Tangent space is the
identity (the ground plane IS screen space) — no TBN machinery. The
offset-limited form is essential (shallow eye vectors at screen edges would
otherwise explode into shimmer):

```
offset = heightScaledBiased * (screenCenter - pixelPos) * strength
```

Total offset stays small — ~2–4 screen pixels at max height. Zoom changes
the virtual eye height (buildings rear up as you zoom in); pan produces the
parallax slide.

### Screen-space composite (not per-quad texcoords)

Per-quad texcoord offsets would sample across atlas cell boundaries in our
sliced tile sheets — neighbor texels in the sheet are NOT spatial neighbors.
Instead:

1. Ground/terrain passes render to an FBO **color** target exactly as today
   (QuadBatch under GlStateBracket, painter pass list unchanged).
2. A parallel RGBA **material-height** target renders the same quads. Its
   channels preserve macro height, derived micro height, water identity, and
   shoreline proximity instead of prematurely collapsing them to grayscale.
3. One fullscreen quad + small GLSL fragment shader applies the
   offset-limited parallax against the composed color image.
4. Units, shots, FX, UI draw on top, un-warped.

In the composed screen image, offset samples DO hit spatial neighbors —
atlas bleed is structurally impossible.

### Material-aware height channels

- **Macro** (per tile id): wall = high, building = raised, ground = mid,
  crater = low, water = lowest. Data lives with `TileRegistry` /
  gen-mapping JSON — the derivation kernel cannot know a wall is
  structurally tall (luminance ≠ elevation at that scale).
- **Micro** (per texel): derived from albedo by the vendored MoonLight
  kernel — individual bricks, cracks, rock lumps.
- The height pass stores macro and micro separately. The composite has live,
  independent structure and surface-strength controls, so tall authored cells
  can move strongly while floor texture remains restrained.
- Water identity is semantic (`GroundKind.WATER`), not inferred from color.
  Its macro/micro relief is damped, while a world-anchored wave field refracts
  it by a tunable fraction of one cell. A three-cell shore-distance channel
  boosts motion and moving crest highlights near land.
- Water lookups backtrack when their displaced UV would sample a non-water
  texel. This keeps coast cells from disappearing during camera or wave motion.

## Auto-generated height/normal data

Vendored from MoonLightEngine's
`asset-pipeline/.../tools/assets/terrain/TerrainMaterialDerivationKernel.java`
(deterministic pure-CPU, only `java.awt`/`java.util`): albedo → linear
luminance → box blur → percentile-windowed height; central-difference
tangent normals; cavity AO. Lands in OUR `:asset-pipeline` **tool** source
set (build-time only, never ships); outputs are plain PNGs the mod loads
like any other sheet — no script-sandbox concerns.

Two adaptations needed (see S1):

- **Atlas awareness** — kernel filters wrap at image edges (right for
  tileable terrain textures, wrong for sliced sheets). Run per-cell
  (slice → derive → repack) with **clamp** addressing; `TileRegistry` knows
  every sheet's grid.
- **Percentile window scope** — normalize per-sheet (or per pool), NOT
  per-cell, so a flat grass tile doesn't get stretched to full 0–1 range.

## Stories

- **S1 — Derivation pipeline** (`stories/s1-derivation-pipeline.md`):
  vendor the kernel, add a `deriveTileMaps` Gradle task, bake
  `<sheet>_height.png` / `<sheet>_normal.png` for the terrain sheets.
- **S2 — Screen-space ground parallax** (`stories/s2-screenspace-parallax.md`):
  shader infra (first GLSL in the mod), ground FBO pair, height pass,
  fullscreen offset-limited parallax pass. Water-first tuning.
- **S3 — Dynamic bump lighting** (`stories/s3-dynamic-bump-lighting.md`):
  light the composed ground from muzzle flashes / explosions using the
  derived normal sheets. Composes with S2 (offset first, then sample
  normal at offset coordinate — per the paper).
- **S4 — Unit relief** (`stories/s4-unit-relief.md`, stretch): derive
  normal maps for soldier/vehicle sprites; per-texel lighting on units.
  Parallax itself is a ground effect; units get the lighting half.

## Risks / open questions

- **Pixel-art aesthetic** — subpixel offsets on chunky NEAREST-sampled art
  soften the parallaxed layer (screen-space resample is effectively
  bilinear). Might read as depth, might fight the style. Only a prototype
  answers this; keep a hard off-switch.
- **Steep height steps** — binary ground→wall edges are the paper's own
  worst case. Mitigate with blurred height target or ramped perimeter
  heights; offset limiting caps the damage.
- **Material boundaries** — water has a hard semantic boundary even when the
  metadata texture is bilinear filtered. The composite validates the proposed
  sample against the water channel and falls back to a half or zero offset.
- **First shader in the mod** — GLSL 1.20 / GL 2.1 via LWJGL 2 `GL20`.
  Proven feasible in this environment (GraphicsLib precedent), but
  compile/link infra + GL-state bracket discipline is new ground.
  Degrade gracefully: shader compile failure ⇒ effect off, plain blit.

## Cross-refs

- `roadmap/battle-render/` — draw-list pipeline this hooks into
  (collect-all → drain-all, painter pass list, `GlStateBracket`).
- `roadmap/moddable-tilesets/` — `TileRegistry` + mapping JSON carry the
  macro height metadata and sheet grids.
- MoonLightEngine repo (`C:\Users\Dillon\IdeaProjects\MoonLightEngine`) —
  kernel source of truth for re-vendoring.
