# Floors_Tiles: bring the frame treatment to the grid export path

Status: PROPOSED

Written: 2026-08-29

Read `moddable-tilesets-nouns.md` before changing this story.

## Why

`Floors_Tiles` is the last sheet produced by `normalize_tilesets.py` rather than
exported from its authoring document, and the reason is now precisely known.

The script contributes three things the exporter's **grid** path does not do:

| The script does | The exporter has it | Where |
|---|---|---|
| Removes ImageGen's dark isolated-sprite outline by mirroring an interior band through each tile edge | yes, as `spriteBorderPx` | strip path only |
| Pastes a tileable material into a cell (`floor-materials`, `stone.png`/`sand.png`) | yes, as `material` | strip path only |
| Sharpens after downsampling | yes, as `sharpen` | strip path only |

All three live in the auto-strip packer. The grid packer draws each cut cell
straight into its slot and does nothing else. So the work is to give both shapes
one frame-preparation step rather than two.

## What the sheet actually needs

The game addresses **17 of the sheet's 650 cells**. Exporting it packs those
seventeen and the atlas goes from 1400x1456 to roughly 272x56.

| Block | Cells | Source |
|---|---|---|
| `floors.grass` | 3 distinct | plate, needs the border mirror |
| `floors.dirt` | 3 distinct | plate, needs the border mirror |
| `floors.brick` | 5 distinct | plate, needs the border mirror |
| `floors.stone` | 3 **identical** | material `game-buffs-mountain/stone.png` |
| `floors.sand` | 3 **identical** | material `game-buffs-desert/sand.png` |

The material sizes line up exactly the way `nature-tiles` did: both files are
52x52, `cellPx` is 56, and `FixedGridTileDrawer.GROUND_INSET_PX_LARGE` is 2, so
52 + 2x2 = 56. Those six cells should reproduce byte-identically.

The variant-pool block shape the other eleven need already exists —
`Water_tiles` shipped through it on 2026-08-29.

## Constraints

- **Do not change `urban-tileset` as a side effect.** It is a grid sheet
  exported from its document today and re-exports byte-identical. Adding
  `sharpen` unconditionally to the grid path changes it: measured at 38% of
  pixels moved, 555 alpha pixels among them, and the wall panel's rivets and
  seam lines visibly softened. Either the treatment is authored per entry the
  way `spriteBorderPx` and `material` already are, or `urban-tileset` is
  re-reviewed and re-exported deliberately as part of this story. It is not a
  side effect either way.
- Regenerate `Floors_Tiles_height.png` / `_normal.png` with
  `:asset-pipeline:deriveTileMaps`; the atlas layout changes completely.
- `Floors_Tiles` leaves `GRID_SPECS` by being measured out, not written out:
  its document declaring blocks is what withdraws it, and
  `KeyedSheetsAreExportedNotNormalizedTest` checks that both ways round.
- When it leaves, `normalize_tilesets.py`, `pack_texture_atlas.py` and
  `texture-atlases.json` have nothing left to produce and should be deleted
  rather than left armed — the guard test already fails an empty `GRID_SPECS`
  for that reason.

## Worth deciding while here

`floors.stone` and `floors.sand` are three-cell pools whose three cells are
**pixel-identical**: the material paste writes one 52x52 tile into all three, so
the hash pick is a no-op and four cells of atlas are redundant. Preserving them
as three keeps the migration provably behaviour-preserving; reducing each to one
is also behaviour-preserving, since a hash over identical cells yields identical
pixels either way. Reducing is the honest shape, and expanding to three real
variants needs art rather than code. Not decided here.

## Acceptance

- `Floors_Tiles` exports from its authoring document; the atlas holds seventeen
  cells.
- The six material-sourced cells are byte-identical to what ships today.
- The eleven plate-sourced cells are reviewed tiled, not just measured: the
  failure this treatment prevents is a dark lattice at tile boundaries, which
  a per-cell difference number does not show.
- `urban-tileset` re-exports byte-identical, or its change is reviewed and
  accepted in this story.
- Nothing under `art-source/tilesets/` still produces a shipped atlas.
