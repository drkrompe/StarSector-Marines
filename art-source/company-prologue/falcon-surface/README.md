# Falcon (P) surfacing

Status: ACTIVE — pass 6 texture, waiting on the owner.
Written: 2026-10-19

The Falcon (P)'s inputs to the shared surfacing harness
(`../second-wind-surface`, the ship-model-pipeline skill's pass 6). Nothing
here forks the harness: every script runs from there with `--hull ../falcon-surface`.
`rebuild.sh` is the whole sequence; it replays everything except the Codex
generations, which are kept (`generated/`, `reference/finish-reference.png`).

- **Livery** is the pirates' repaint read off `falcon_p.png`: khaki prong armour
  with plates repainted pirate red or sand where the sprite paints its slashes.
  The geometry carries that per plate (`falcon-geometry/fc_detail.Livery`), so
  the grade keeps each plate its own class rather than averaging the patchwork.
- **Parts** (`groups/`): `prongs` — four deck-and-glacis tiles per prong (each
  prong its own generation, the liveries differ), four belt and four inner-wall
  tiles mirrored port to starboard; `body` — spine, dome, waist, drive block top
  and side, outrigger pod top and side, bow forks, port sources mirrored. Six
  sheets, one finish reference and one swatch sheet: eight Codex calls.
- **Wear** is the `pirate` row of `wear.json`: heavier abrasion, fade, grime and
  soot than any navy hull, some neglect. Its colours are owned in
  `palette.json` `wear` (the navy primer showing under the repaint).
- **Output**: `build/skinned.blend`, then `build/export/falcon-p.blend` — the
  packed, appendable `FALCON (P)` with the shared VFX kit's `SWFX` collection
  added by `../falcon-geometry/export_effects.py` and checked by
  `verify_asset.py`. Everything under `build/` is ignored.
