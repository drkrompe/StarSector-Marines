# Impact decals

The marks rounds leave where they land, by calibre and weapon type (`battle-render-nouns.md`, impact marks), and the
shipped decal sheet `mod/graphics/decals/decals.png` they are drawn from. Made with the geometry-first workflow
(`geometry-first-imagegen.md`).

- `make_guide.py` -> `guide-ballistic.png`, `guide-explosive.png`, `guide-energy-fire.png`: three 4 x 4 grids of
  384 px cells, each mark's footprint in flat grey centred on its cell, directional marks (gouge, beam burn, flame
  sweep) along +x. Rows are `DecalKind` families, columns their variants.
- `prompt-<sheet>.txt`: the briefs. `master-<sheet>.png` (+ `.provenance.json`) are the accepted generations, from
  `python tools/codex_imagegen.py --prompt prompt-<sheet>.txt --out master-<sheet>.png
  --ref guide-<sheet>.png=<layout> --ref ../tilesets/terrain-blast.raw.png=<material only>`.
- `master-<sheet>-v1.png` and `guide-<sheet>-v1.png` are the first pass, kept for comparison: its sine-lobed
  footprints came back as regular stars (wall pocks, the frag core, fire scorch), so the guide's outlines were made
  irregular and the briefs asked for ragged edges. The v1 provenance names the guide by its current path; the v1
  guide is the file beside it.
- `singles/`: the frames other producers draw, one per file: the shell casings (cut from the old strip), the muzzle
  scorch (`art-source/fx/build_muzzle_scorch.py`) and the Spitter's acid marks
  (`art-source/rav-redesign/spitter/export_spitter_rig.py`).
- `export.py` -> the shipped sheet: a grid of 160 px cells, 8 wide. Cells 0-5 are the singles, each centred in its
  cell; from cell 8 the three masters, cut mechanically, each cell resampled to 160 px. The game cuts each cell to its
  content symmetric about the cell's centre (`SpriteSheetSlicer.sliceGrid`), so a mark's drawn size is its content's
  width and it turns about the centre it was drawn round.

Re-run `export.py` after replacing a master or a single. `DecalKind` names the cells; keep the two in step.
