# nature-tiles: two provenances on one strip

Status: ACTIVE — the sheet is keyed and annotated; the export is refused until
its ground frames have one source

Written: 2026-08-28

Read `moddable-tilesets-nouns.md` first; this doc records why one sheet stops
short of the path `urban-tileset` and `urban-tileset-3` completed, and what the
alternatives are.

## What was done

`nature-tiles.raw.png` now carries its own keyed alpha, per cell, and
`nature-tiles.tileset-authoring.json` describes all twenty pieces — id, layer,
label, description, cover, passability, overlay validity, and the sprite border
each field was drawn with. The document reproduces the shipped tileset field for
field, and `nature-tiles` is withdrawn from `normalize_tilesets.py`, which took
the whole auto-strip half of that script with it.

What is **not** done is the export. The atlas that ships is still the one that
was there before.

## Why the export is refused

**Five of the twenty frames do not take their picture from the raw plate.**
`grass-1`, `grass-2`, `dirt-1`, `dirt-2` and `sand` are tileable materials from
`art-source/tilesets/atlas-material-source/game-buffs-*`, pasted into the atlas
by `texture-atlases.json` after the fact. The ImageGen art underneath them is
still on the plate, and exporting from the document alone would put it back.

That is not a bug in the manifest; it is a deliberate art decision, and the
pictures say why. Rendered side by side and tiled three by three
(`build/tileset-authoring/nature-tiles-tiling.png`), the material is a quiet,
seamless surface and the ImageGen field is a shaded slab: bright along its top,
dark under its foot, with a darker column down each side. Repeat one and it
rules a lattice over the ground — measured at 41–63% brightness drift between a
drawn edge and the middle of the same tile.

The sprite-border treatment does remove that. With the border authored per piece
the drift falls to 1–9%, and the lattice is gone from the tiling. But what
replaces it is a mirror: the treatment reflects the interior out through the
border, and on a frame 54 pixels wide with a six-pixel border the reflection
reads as an ornamental symmetry at every join. It is a better failure than a
lattice and it is still worse than the material. **A field that ships as a
surface has to be drawn as one; a slab with a lit rim is a prop of ground, not
ground.**

The other thirteen frames — six plants and seven rock groups — export
*better* than what ships: crisper silhouettes, correct alpha, every stone of
every scatter intact, against a shipped sheet whose alpha was dilated off a
brightness mask. But **a strip exports whole or not at all.** Order is the
address, so there is no partial export: thirteen good frames cannot be shipped
without the seven that would regress.

## What is also wrong here, and is not the export's fault

- **`texture-atlases.json` holds pixel rectangles into an atlas the packer is
  free to lay out differently.** They are the same defect as the pinned frame
  boxes that `normalize_tilesets.py` carried: a reference that goes wrong
  without going missing. The packer already resolves `frame` selectors on an
  auto-strip atlas, so the rectangles can become frame indices — but the
  material *files* are also cut to the exact pixel size of the frame they fill
  (52x52 into a 56x56 frame, 45x47 into 49x51), which is the same dependency
  wearing a different hat.
- **`nature.rock-small-1` draws a plant.** The sheet carries six plants and
  seven rock groups; the tileset was written for five and eight, so frame 12 —
  a tall grass tuft — is named as a small rock and sits in every `rockPool` in
  `urban.mapping.json`. It carries no cover, so nothing about the fight changes;
  a bush simply appears where a rock was asked for. Renaming it is a content
  change with its own review, not part of a re-export, so it is recorded on the
  piece's own annotation and left alone.

## The ways out, in rough order of cost

1. **Move the material into the authoring pass.** Let an entry say that its
   picture is a tileable material rather than a crop of the plate, and let a
   material-backed frame take its size from the material plus the renderer's
   ground inset — which is exactly the guard width the packer adds today, and
   exactly what the inset crops. `texture-atlases.json` loses its `nature`
   entry, the rectangles disappear, and the sheet has one producer again. This
   is the only option that leaves the sheet genuinely re-derivable.
2. **Keep the material pack as a second step after the export**, with `frame`
   selectors instead of rectangles and the materials refitted to whatever the
   export produces. Cheaper, but it leaves the atlas with two producers that
   must run in order, and the fit is a resample of a seamless texture — which
   has to wrap-pad or it stops being seamless.
3. **Redraw the seven fields as surfaces** and export the sheet whole. The
   cleanest result and the one that needs new art rather than new code.

Option 1 is the recommendation. It is the only one under which the sentence "the
authoring document must be able to say everything the tileset says" is true of
this sheet.
