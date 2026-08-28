# Moddable Tilesets — Open Stories

Status: ACTIVE — 2 proposed stories

Written: 2026-08-23

Updated: 2026-08-28 — `urban-tileset-raw-alpha.md` added: the sheet is fully adopted and blocked on alpha only.

Read `moddable-tilesets-nouns.md` before changing a moddable-tilesets story.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `nature-variant-pool-authority-cleanup.md` | Proposed | Runtime grass/dirt primary variant membership remains hardcoded in `TileManifest`; preserve coordinate-hash parity while moving that membership to declared content. |
| `urban-tileset-raw-alpha.md` | Proposed | `urban-tileset` is adopted into its authoring document and re-exportable in every respect but one: the raw sheet is opaque, so an export packs every piece on a black square. Blocks the corrected cut from shipping. |

External discovery and additive merge use the shared catalog-provider contract
defined in `submod-catalog-contract.md`; tilesets
deliberately do not support overrides.
The optional filler-dispatch field, more filler tunables, resolver/marker data,
and richer overlay tags remain direction, not contracted work.
