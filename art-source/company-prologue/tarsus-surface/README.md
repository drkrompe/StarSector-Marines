# Tarsus surfacing (pass 6)

The independent merchant livery for the Tarsus (`tarsus-geometry`), made with the shared
harness in `../second-wind-surface` (`--hull` this folder). `rebuild.sh` replays it all
except the five Codex generations, which are kept with their prompts and provenance.

- **Livery**: from `tarsus_base.png` — weathered ochre-tan arms, aft body and castle;
  pale cream central deck; rust-brown drum castings and drive housings; mid-grey
  structure; white tanks; signal orange panels; copper drive cans; gunmetal machinery.
- **Finish reference** (`reference/finish-reference.png`): one Codex reskin of the clean
  build from the three-quarter camera, the sprite attached for colours only. Registered
  at IoU 0.996; `palette.json` is measured off it.
- **Owned palette** (`surface_hull.derive_palette`): spreads are owned on every class;
  copper is rust shifted lighter and warmer; the `dark` mean (recesses, seams) is owned at
  L 24, because the reference measures recesses only on registration edges (L 40, lighter
  than gunmetal).
- **Parts** (`groups/arms.py`, `groups/body.py`): 17 parts, 12 generated in three 2x2
  sheets (arms, body, aft); the starboard arm faces and aft wall reuse the port ones
  mirrored. Texel density 4-24 per unit (2-12 per metre at 0.50 m/unit); the belly
  and arm tops are the thinnest, and both are seen least.
- **Swatches**: one 3x2 Codex sheet (ochre, cream, rust, grey, orange, gunmetal); white,
  copper and dark derive from cream, rust and gunmetal.
- **Checks**: no colour-layout swap. Three grey plates (stern face, keel strip, drive top)
  flag as swaps because Codex painted them a paler grey that classifies nearer cream;
  grading pulls each declared region to the grey palette, so the flag is tonal.
- **Wear** (`wear.json` service): hard-worked and dusty, soot at the six drives. Wear
  colours are the Colossus set (`palette.json` `wear`), with chalk re-chosen as faded
  ochre rather than faded oxide red.

5 Codex calls in all: the finish reference, three part sheets and one swatch sheet.
