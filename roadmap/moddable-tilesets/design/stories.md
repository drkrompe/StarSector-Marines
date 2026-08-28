# Moddable Tilesets — Open Stories

Status: ACTIVE — 1 proposed story

Written: 2026-08-23

Updated: 2026-08-28 — `urban-tileset` and `urban-tileset-3` are both generated from their keyed raw sheets; the exporter now writes the sliced-strip shape as well as the cell grid. `nature-tiles` is the remaining fused plate: it is a strip of 20 and could follow the same path, but its cut is still approximate and its pieces are unannotated.

Read `moddable-tilesets-nouns.md` before changing a moddable-tilesets story.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `nature-variant-pool-authority-cleanup.md` | Proposed | Runtime grass/dirt primary variant membership remains hardcoded in `TileManifest`; preserve coordinate-hash parity while moving that membership to declared content. |

External discovery and additive merge use the shared catalog-provider contract
defined in `submod-catalog-contract.md`; tilesets
deliberately do not support overrides.
The optional filler-dispatch field, more filler tunables, resolver/marker data,
and richer overlay tags remain direction, not contracted work.
