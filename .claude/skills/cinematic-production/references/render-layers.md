# Render layers and compositing

Written 2026-10-22, from the Jangala C1–C5 finals and the Asharu battle rounds 8–9.

A layered render costs about the same as a combined one the first time. It pays off
the second time: a changed backdrop, a retuned effect or a missing plume is a
recomposite in seconds, or a re-render of only the cheap layer, instead of another
1.5–2.5 h hero frame. Most shots here go through several review rounds, so plan the
layers at blocking.

## When to layer

Layer a shot when any of these hold:

- it will have more than one review round, or a final may need repair;
- it has a large backdrop (planet, landscape, starfield) that may be repainted,
  up-res'd or given settlements;
- it carries effects (volumes, shields, trails, bursts) that will be tuned separately
  from the hulls;
- parts of the frame need very different sample counts: a backdrop needs few, hulls
  need many, and volumes want their own denoise;
- the camera is locked or nearly so, and the backdrop can be rendered once for the
  whole shot.

A short, one-off look render that will not be repaired does not need layers.

## Standard split

| Layer | Holds | Samples | Notes |
|---|---|---|---|
| Backdrop | planet / ground, starfield, distant plates | low | Ships and effects as holdouts. A locked-camera shot renders it once. Can be replaced by a matte-painted plate. |
| Ships | hulls, turrets, fittings | full | Keeps **every light source**: sun, drives, muzzle flashes, explosion lights, planet bounce. Effect emitters stay invisible to the camera here, so hulls are still lit. |
| Effects | volumes, shields, trails, bursts, tracers | own, with own denoise | Ships and backdrop as holdouts. |

Write depth and Cryptomatte (object and material) on every layer, so the composite can
mask per ship and order volumes in front of or behind hulls.

## Lighting: what survives a split

- **Light that effects throw on hulls** survives if the lights stay in the ships
  layer. This is the rule that makes layering safe. Never move a light into the
  effects layer just because its visible glow lives there.
- **Occlusion** (a hull in front of the planet, smoke in front of or behind a hull)
  is handled by holdouts plus depth and Cryptomatte.
- **Lost:** inter-layer bounce and reflection, such as an effect reflecting in a hull
  and back. This is negligible at orbital distance. Keep any hero reflection inside
  one layer.
- **Watch:** motion-blur and denoiser edges at layer boundaries. Prove a new split
  against one fully combined still (a pixel diff), then make it the default.

## Painted plates

Where the camera barely moves (straight down, a held horizon), finish the backdrop
as a matte-painted plate (ImageGen, guided by the 3D render so it registers) instead
of more texture resolution. Where the camera moves or parallax matters, the detail
must live on the geometry or on a camera projection. See `parallax.md`.

## Lessons from these renders

- **Check environment defaults against the setting.** The VFX kit's smoke defaulted
  to "in atmosphere" (`smk_atmosphere = 1`). In orbit that hung full-density haze
  24 engine radii long behind every drive, and dust boxes under every ship. It was
  wrong to look at and made volumes 72–116% of Asharu's frame. Set vacuum for space
  shots (`smk_atmosphere = 0`). Run the volume audit (`--volume-audit`, no render)
  before the first heavy round.
- **Fix scale once, at instancing.** Kit lights were up to 9× too bright in a
  metre-scale scene. `ShipFx.metre_scale` corrects each copy; never compensate
  per effect, or the fix is applied twice.
- **Copies need their own materials for keyed colour.** Objects drawn through a
  collection instance do not hand a keyed object colour to Cycles
  (`ShipFx.own_colours`).
- **Excluded collections are never evaluated.** Their stored matrices go stale, so
  read evaluated transforms.
- **Review frames: one look render per real change.** Prove fixes with the no-render
  checks (`--check`, `--fx-check`, `check_kit.py`) and save the GPU for what the
  owner has to see.
- **Missing drives are a composite repair; do not let them happen.** C4's first finals
  needed a plume pass. `thrust_shots.require_plumes` now refuses such a build.
