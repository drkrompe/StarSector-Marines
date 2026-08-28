# Moddable Tilesets — Open Stories

Status: ACTIVE — 2 proposed stories

Written: 2026-08-23

Updated: 2026-08-28 — added the tileset ingest/annotation story.

Read `moddable-tilesets-nouns.md` before changing a moddable-tilesets story.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `nature-variant-pool-authority-cleanup.md` | Proposed | Runtime grass/dirt primary variant membership remains hardcoded in `TileManifest`; preserve coordinate-hash parity while moving that membership to declared content. |
| `tileset-ingest-annotation-pass.md` | Proposed | Resumable annotation of a raw sheet into walls, corners and doodads; raw inputs leave `mod/`. Independent of the variant-pool cleanup. |

External discovery and additive merge use the shared catalog-provider contract
defined in `submod-catalog-contract.md`; tilesets
deliberately do not support overrides.
The optional filler-dispatch field, more filler tunables, resolver/marker data,
and richer overlay tags remain direction, not contracted work.
