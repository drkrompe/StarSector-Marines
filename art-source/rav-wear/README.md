# Rav wear sheet

Worn-chitin marks drawn over the living Matriarch and Brute (`colony-breach-nouns.md`, Matriarch; `RavWear`), made
with the geometry-first workflow (`geometry-first-imagegen.md`). The organic counterpart of the vehicle scuff sheet.

- `make_guide.py` → `guide.png`: a 4 x 3 grid of 384 px cells, each mark's footprint in flat grey inside the middle
  70% of its cell. Rows are stages: scored and chipped chitin, bruising and cracked plate, weeping wounds. Columns are
  variants.
- `prompt.txt`: the brief. `master.png` (+ `.provenance.json`) is the accepted generation, from
  `python tools/codex_imagegen.py --prompt art-source/rav-wear/prompt.txt --out art-source/rav-wear/master.png
  --ref art-source/rav-wear/guide.png=<layout> --ref mod/graphics/battle/rav-matriarch/body.png=<material only>`.
- `export.py` → `mod/graphics/battle/swarm/rav-wear.png`: the grid cut mechanically, each cell resampled to 128 px.

The renderer turns a mark about its cell centre and keeps it on the body by its reach, half the cell's side: a mark
must stay inside the middle 70% of its cell. Re-run `export.py` after replacing the master.
