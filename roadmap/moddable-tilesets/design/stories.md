# Moddable Tilesets — Open Stories

Status: ACTIVE — 2 proposed stories

Written: 2026-08-23

Updated: 2026-08-29 — `Water_tiles` is exported from its authoring document through the new variant-pool block shape; `Floors_Tiles` is the last sheet on the normalize script and has a story.

Read `moddable-tilesets-nouns.md` before changing a moddable-tilesets story.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `nature-variant-pool-authority-cleanup.md` | Proposed | Runtime grass/dirt primary variant membership remains hardcoded in `TileManifest`; preserve coordinate-hash parity while moving that membership to declared content. |
| `floors-tiles-grid-frame-treatment.md` | Proposed | The grid export path lacks the material, sprite-border and sharpen steps the strip path has, which is the only thing keeping `Floors_Tiles` on `normalize_tilesets.py`. Must not change `urban-tileset` as a side effect. |

External discovery and additive merge use the shared catalog-provider contract
defined in `submod-catalog-contract.md`; tilesets
deliberately do not support overrides.
The optional filler-dispatch field, more filler tunables, resolver/marker data,
and richer overlay tags remain direction, not contracted work.
