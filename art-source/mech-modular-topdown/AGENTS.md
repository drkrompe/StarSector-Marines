# Mech raster authoring instructions

Follow the repository-root `AGENTS.md` and `CLAUDE.md` first. These additional
rules apply to the modular mech raster pipeline in this directory.

Read `README.md` for the end-to-end weapon concept, perspective-prompting,
material-ID, deterministic build, and visual-verification flow.

## Faction livery workflow

- Treat ImageGen output as a semantic material-ID aid, never as shipping color,
  geometry, lighting, or alpha. Give ImageGen the exact registered source and
  ask it to mark only paintable casing with a high-chroma uniform key color.
- Retain the prompt and raw material-ID pass under `sources/`. Derive and review
  a normalized soft mask against the original sprite; resample semantic output
  to the original canvas when ImageGen changes its dimensions.
- Build every shipped weapon pixel from the accepted base weapon. Preserve its
  canvas, registration, alpha, silhouette, local luminance, noise, wear,
  highlights, shadows, and protected hardware. Exposed barrels, mechanisms,
  vents, fasteners, ordnance, warning marks, and functional indicators do not
  receive faction paint.
- Transfer the aligned faction chassis palette through the reviewed mask in
  OKLab (or a demonstrably equivalent perceptual space). Reapply the source
  weapon's fine luminance/detail so a color replacement never flattens the
  module into a clean fill.
- Keep chassis and equipment generation deterministic. `build_assets.py` is
  the entry point; faction weapon derivation remains in
  `derive_faction_weapons.py`. Regenerate both faction contact sheets after
  changing a source, mask, transfer rule, or palette.
- Validate exact alpha equality against every base layer and inspect the
  contact sheets at gameplay scale. A visually attractive result that changes
  geometry, registration, or protected hardware is not an acceptable livery.

## Top-down direct-fire concepts

- For a barrel that fires north along the battlefield plane, make the camera see
  the barrel's top surfaces and only an edge-on muzzle line at the northmost tip.
  A visible circular aperture points toward the camera, not toward the target.
- Hiding the bore is insufficient when a broad emissive channel remains on the
  weapon's top face; that reads as a vertical emitter or reactor. Use long
  parallel focusing rails and a narrow recessed optical path to carry the
  horizontal firing axis through the silhouette.
- Label each ImageGen input by role. A shipped cannon may be a perspective
  reference and a missile pod a footprint reference without either donating its
  weapon geometry to the new concept.
