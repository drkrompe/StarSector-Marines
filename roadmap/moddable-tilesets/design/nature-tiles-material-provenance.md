# nature-tiles: two provenances on one strip

Status: SHIPPED — the sheet exports whole; its five field frames declare the
material their picture comes from

Written: 2026-08-28

Updated: 2026-08-28 — option 1 was taken: an authoring entry may declare a
material, the export places it, and `texture-atlases.json` no longer names this
sheet.

Read `moddable-tilesets-nouns.md` first; this doc records why one sheet stopped
short of the path `urban-tileset` and `urban-tileset-3` completed, and what the
alternatives were.

## The situation this doc was written about

`nature-tiles.raw.png` carries its own keyed alpha, per cell, and
`nature-tiles.tileset-authoring.json` describes all twenty pieces — id, layer,
label, description, cover, passability, overlay validity, and the sprite border
each field was drawn with. The document reproduced the shipped tileset field for
field, and `nature-tiles` was withdrawn from `normalize_tilesets.py`, which took
the whole auto-strip half of that script with it.

What could not happen was the export, because **five of the twenty frames did
not take their picture from the raw plate**. `grass-1`, `grass-2`, `dirt-1`,
`dirt-2` and `sand` are tileable materials from
`art-source/tilesets/atlas-material-source/game-buffs-*`, and they were pasted
into the atlas by `texture-atlases.json` after the export had written it. The
ImageGen art underneath them is still on the plate, so exporting from the
document alone would have put it back.

## Why the material is the art, and the plate is not

That is not a bug in the manifest; it is a deliberate art decision, and the
pictures say why. Rendered side by side and tiled three by three, the material
is a quiet, seamless surface and the ImageGen field is a shaded slab: bright
along its top, dark under its foot, with a darker column down each side. Repeat
one and it rules a lattice over the ground — measured at 41–63% brightness drift
between a drawn edge and the middle of the same tile.

The sprite-border treatment does remove that. With the border authored per piece
the drift falls to 1–9%, and the lattice is gone from the tiling. But what
replaces it is a mirror: the treatment reflects the interior out through the
border, and on a frame 54 pixels wide with a six-pixel border the reflection
reads as an ornamental symmetry at every join. It is a better failure than a
lattice and it is still worse than the material. **A field that ships as a
surface has to be drawn as one; a slab with a lit rim is a prop of ground, not
ground.**

The other thirteen frames — six plants and seven rock groups — export *better*
than what shipped: crisper silhouettes, correct alpha, every stone of every
scatter intact, against a shipped sheet whose alpha had been dilated off a
brightness mask. But **a strip exports whole or not at all.** Order is the
address, so there was no partial export: thirteen good frames could not be
shipped without the seven that would regress. The two water fields are plate art
and keep their authored sprite border, so they take the mirror; that is the
trade the treatment is, and it is visible in the review comparison.

## What was decided

Option 1 below was taken. An authoring entry may now name the tileable material
its picture comes from; a material-backed frame is sized from the material plus
the renderer's ground inset and placed with that much of itself wrapped round
it. See the noun doc's law "a frame's picture may be a material, and then it is
sized by the material" for the standing model. The `nature-materials` atlas is
gone from `texture-atlases.json`, its pixel rectangles with it, and the sheet has
one producer again.

The alternatives, kept because the reasoning is what makes the choice legible:

1. **Move the material into the authoring pass.** *(taken)* The only option that
   leaves the sheet genuinely re-derivable, and the only one under which "the
   authoring document must be able to say everything the tileset says" is true
   of this sheet.
2. **Keep the material pack as a second step after the export**, with `frame`
   selectors instead of rectangles and the materials refitted to whatever the
   export produces. Cheaper, but it leaves the atlas with two producers that must
   run in order, and the fit is a resample of a seamless texture — which has to
   wrap-pad or it stops being seamless.
3. **Redraw the seven fields as surfaces** and export the sheet whole. The
   cleanest result and the one that needs new art rather than new code. Still
   open for the two water fields, which are the only fields that now take the
   mirror.

## What is also wrong here, and is not the export's fault

- **`nature.rock-small-1` draws a plant.** The sheet carries six plants and
  seven rock groups; the tileset was written for five and eight, so frame 12 —
  a tall grass tuft — is named as a small rock and sits in every `rockPool` in
  `urban.mapping.json`. It carries no cover, so nothing about the fight changes;
  a bush simply appears where a rock was asked for. Renaming it is a content
  change with its own review, not part of a re-export, so it is recorded on the
  piece's own annotation and left alone.
- **`floor-materials` is still a second producer, of a different sheet.**
  `Floors_Tiles.png` is a fixed-grid sheet still produced by
  `normalize_tilesets.py` and its materials are still pasted in by the manifest,
  addressed by `cell` rather than by pixel rectangle. That is tolerable exactly
  while its plate stays opaque and the exporter does not own it; keying that
  plate is what would force the same move, and
  `KeyedSheetsAreExportedNotNormalizedTest` is what would say so.
