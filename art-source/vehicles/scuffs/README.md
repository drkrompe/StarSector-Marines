# Vehicle scuff sheet

Worn-plate marks drawn over live ground hulls and turrets (`body-scuffs.md`), made with the geometry-first workflow
(`geometry-first-imagegen.md`).

- `make_guide.py` → `guide.png`: a 4 x 3 grid of 384 px cells, each mark's footprint in flat grey inside the middle
  70% of its cell. Rows are stages: scrapes and bullet pocks, scorch, gouges and dents. Columns are variants.
- `prompt.txt`: the brief. `master.png` (+ `.provenance.json`) is the accepted generation, from
  `python tools/codex_imagegen.py --prompt prompt.txt --out master.png --ref guide.png=<layout> --ref
  ../tank/wreck-master.png=<material only>`.
- `export.py` → `mod/graphics/battle/vehicles/scuffs.png`: the grid cut mechanically, each cell resampled to 128 px.

The renderer turns a mark about its cell centre and keeps it on the hull by its reach, half the cell's side: a mark
must stay inside the middle 70% of its cell. Re-run `export.py` after replacing the master.
