# Warship hull damage: interior and torn-edge art

Sources for `mod/graphics/warship-damage/` (story `warship-hull-damage.md`).

- `make_guide.py` writes the geometry guides to `art-source/templates/hull-*-guide.png`
  (periodic interior layouts, torn plate lips on a magenta key).
- `generate.sh <part>` sends `prompts/<part>.prompt.txt` and its guide to Codex ImageGen
  (`interior-frames`, `interior-core`, `torn-edges`), with the vanilla Onslaught sprite as a
  material reference, and keeps the untouched result in `raw/`.
- `export_assets.py` is the mechanical exporter: 512px, interiors made exactly seamless by a
  half-offset cross-fade, the edge sheet keyed to alpha.

The interiors are drawn under a breached hull cell, tinted to the hull's palette at runtime;
the torn edges are laid along the rim of a breach so it is ragged rather than square.
