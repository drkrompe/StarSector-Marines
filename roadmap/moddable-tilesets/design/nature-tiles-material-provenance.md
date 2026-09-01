# nature-tiles: two provenances on one strip

Status: SHIPPED — the sheet exports whole; its seventeen field frames declare
the material their picture comes from

Written: 2026-08-28

Updated: 2026-09-01 — grass and dirt widened from a pair each to eight each;
the pairs turned out to be one picture apiece.

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

## The pairs were one picture, and that is what the material field was hiding

Found 2026-09-01. `grass-1` and `grass-2` named the *same* material file, and so
did `dirt-1` and `dirt-2`. They packed byte-identical, so
`TileManifest.pickNatureGrassTileId` hashed per cell between two copies of one
image and every grass cell in the game drew the same picture. Grass is the
primary outdoor surface; dirt is fifteen percent of every temperate fill. This
was the most-repeated image in the project, and it was invisible in every
artifact that could have shown it — the ids differ, the frames differ, the
tileset lists two entries, and the atlas holds two frames.

It is the same defect `floors.stone` and `floors.sand` each carried, and it has
the same shape as those: a pool whose slots were filled by copying. Worth stating
as a standing hazard rather than as three incidents. **A variant pool is not
variety until its members differ, and nothing in this pipeline checks that.**
Declaring a material made it cheaper to write a pool that only looks like one,
because two slots naming one path is a shorter edit than two slots naming two.

Both pools now run eight frames. Slot 1 of each keeps the material that shipped,
so an eighth of the cells are pixel-for-pixel unchanged and the ground did not
move colour; slots 2 through 8 are generated siblings shifted onto that slot's
exact mean by `normalize_ground_variants.py --mean`. The frame renumbering that
followed — twelve inserted frames moving seventeen later ones — is safe because
nothing addresses this sheet by number: `urban.mapping.json` and `TileManifest`
both name ids and the export assigns the indices. That was proved rather than
assumed, by checking every pre-existing id's picture is byte-identical at its new
frame index.

The wrap needed a second method to get here. The four-corner blend that made the
`floors.*` families is periodic by construction but flattens whatever it wraps,
which is fine for grain and wrong for structure: the first dirt batch came back
with its mottle averaged away and a chequerboard of darker tile centres, scoring
a seam ratio of 5.59 against a threshold of 3.5. `wrap_overlap` cross-fades a
band at two edges instead and leaves the middle of the tile exactly as drawn —
1.19 on the same batch, with the mottle intact. Blend for noise, overlap for
anything with features.

## What is also wrong here, and is not the export's fault

- **`nature.rock-small-1` drew a plant.** Fixed 2026-08-29. The sheet carries
  six plants and seven rock groups and the ids were written for five and eight,
  so frame 12 — a tall grass tuft — was named as a small rock and sat in every
  `rockPool` in `urban.mapping.json`: one entry in five on grassland and
  wetland, one in eight on beaches. It carried no cover, so nothing about the
  fight changed; a bush simply appeared where a rock was asked for. It is now
  `nature.tuft-3` in the plant pools, and the small rocks are renumbered from
  frame 13 so the names run contiguously again. The re-export is byte-identical
  in the atlas — only the names moved — so the derived height and normal
  companions stayed valid. Worth keeping in view: the cover and passability on
  frames 15 through 19 were checked against the pictures and were already
  right, so this was one misnamed frame rather than a shift running through the
  range.
- **`urban-tileset-2` had no master, so the shipped atlas became one.**
  Adopted 2026-08-29. Only a 3x3 patch of it had raw art:
  `normalize_spaceport_apron.py` composited nine downsampled panels into columns
  6 through 8 and never touched the other 42 cells, whose only copy was the file
  in `mod/graphics`. Copying that file to `art-source/` and cutting it 1:1 is
  what made the sheet exportable at all — there was nothing else to export from.
  Adopting a shipped file as its own source is worth doing only under that
  condition and only at 1:1: nothing is resampled and nothing re-keyed, so the
  adoption is provable rather than merely plausible. It was proved — every
  visible pixel is unchanged, alpha included; the only differences are 1187
  fully transparent pixels whose leftover colour is now zero.

  Packing it revealed what a script-produced sheet can hide. Ten cells the game
  draws were in no tileset at all: `DefensePostStamper` reached the turret
  embankment and the vent grate through raw `(col, row)` constants in
  `TileManifest`, and `road.courtyard`'s nine cells were reached a second time,
  by coordinate, as the "bow out" embankment. Exporting only the declared blocks
  dropped them, which is how they were found. They are `road.embankment` and
  `road.vent` now, and all of them resolve by id.
- **`floor-materials` is gone, and so is every other second producer.**
  Closed 2026-08-29. `Floors_Tiles` exports from its authoring document: its
  seventeen addressed cells of 650, with `floors.stone` and `floors.sand` taking
  their picture from the same materials the manifest used to paste, byte for byte.
  `normalize_tilesets.py`, `pack_texture_atlas.py` and `texture-atlases.json` are
  deleted. What replaced the manifest is the same `material` field this document
  argued for on `nature-tiles`, so the two sheets now say the same thing the same
  way.