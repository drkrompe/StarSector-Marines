# A piece whose picture is a material

Status: PROPOSED

Written: 2026-08-28

Read `moddable-tilesets-nouns.md` first, and
`nature-tiles-material-provenance.md` for why this exists and what the
alternatives were.

## Scope

Let an authoring entry declare that its picture comes from a tileable material
file rather than from a crop of the raw plate, and export it that way. Then
export `nature-tiles` and retire `texture-atlases.json`'s `nature-materials`
entry.

Out of scope: the `floor-materials` entry, which pastes into `Floors_Tiles.png`
— a fixed-grid sheet still produced by `normalize_tilesets.py`, addressed by
`cell` rather than by pixel rectangle, and not blocked on anything. Also out of
scope: renaming `nature.rock-small-1`, which draws a plant. That is a content
change with its own review.

## Constraints

- **A material-backed frame takes its size from the material, not from the
  strip's scale.** A material is a surface, not a picture at a size, and
  resampling a seamless texture to fit a frame either loses its seams or has to
  wrap-pad to keep them. Sizing the frame from the material avoids the question.
- **The wrapped guard is the renderer's ground inset.** A material is placed
  with `FixedGridTileDrawer.GROUND_INSET_PX_LARGE` pixels of wrap around it, so
  the sampler never clamps and the inset crops exactly the material. That is
  what `texture-atlases.json` does today with `guardPx: 2`; it is the same
  number for the same reason and should be taken from the renderer rather than
  restated.
- The other fifteen frames keep coming from the plate at the authored scale,
  including the two water fields, which keep their authored sprite border.
- One producer afterwards: `nature-tiles.png` must be reproducible by the export
  alone. `KeyedSheetsAreExportedNotNormalizedTest` covers half of that; the
  material half needs its own guard, and the cheap one is that the shipped
  frames are byte-identical to the wrapped materials.

## Acceptance

- All twenty `nature.*` ids keep their frame order and every authored field;
  `NatureTilesStripPackingTest` already asserts that against the shipped
  tileset and must stay green through the export.
- `nature-tiles.png` grass, dirt and sand frames are the same pixels they are
  today.
- `texture-atlases.json` no longer names `nature-tiles.png` or any pixel
  rectangle into it.
- Companions re-derived (`:asset-pipeline:deriveTileMaps`);
  `NatureTilesAlphaTest` proves they line up with the new frames.
- The frame-by-frame and tiled comparisons under `build/tileset-authoring/` are
  reviewed by eye, not just by their numbers. The plants and rocks should
  improve visibly; the fields must not change.

## Plan

1. Add the material declaration to `TilesetDocument`/`TilesetExport.Entry` and
   round-trip it; refuse a material a strip's `packStrip` cannot size.
2. Place material-backed frames in `stripAtlas` with the wrapped guard.
3. Point the five field entries at their material files, export, re-derive.
4. Delete the `nature-materials` atlas from `texture-atlases.json` and add the
   byte-identity guard.
