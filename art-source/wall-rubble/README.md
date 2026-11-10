# Wall rubble

The rubble a damaged building wall leaves on the ground at its foot (`WallRubble`, `voids.md` wall damage), and the
shipped sheet `mod/graphics/decals/wall-rubble.png` it is drawn from. Made with the geometry-first workflow
(`geometry-first-imagegen.md`).

- `make_guide.py` -> `guide.png`: a 4 x 4 grid of 384 px cells, each footprint flat grey. Rows: heaps (a pile along
  the cell's top edge, where its wall stood), slabs, clusters, scatter over a blurred dust patch.
- `prompt.txt` is the brief; `master.png` (+ `.provenance.json`) the accepted generation, from
  `python tools/codex_imagegen.py --prompt prompt.txt --out master.png --ref guide.png=<layout>
  --ref ../tilesets/colony-rubble.raw.png=<material only>`.
- `export.py` -> the shipped sheet: each master cell resampled whole to 128 px, so a heap keeps its place against its
  cell's top edge (it prints that edge for `WallRubble.HEAP_TOP`). The scatter row's dust is faded to a film
  (`thin_dust`). The game tints each neutral piece toward its wall's material.

Re-run `export.py` after replacing the master.
