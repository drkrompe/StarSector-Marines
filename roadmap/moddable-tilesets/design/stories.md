# Moddable Tilesets — Open Stories

Status: ACTIVE — 2 proposed stories

Written: 2026-08-23

Updated: 2026-08-28 — `nature-tiles` is keyed and fully annotated, and its document reproduces the shipped tileset field for field, but its export is refused: five of its twenty frames take their picture from a material library rather than from its plate. See `nature-tiles-material-provenance.md`.

Read `moddable-tilesets-nouns.md` before changing a moddable-tilesets story.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `nature-variant-pool-authority-cleanup.md` | Proposed | Runtime grass/dirt primary variant membership remains hardcoded in `TileManifest`; preserve coordinate-hash parity while moving that membership to declared content. |
| `nature-tiles-material-piece.md` | Proposed | Let an authoring entry say its picture is a tileable material rather than a crop of the plate, sized from the material plus the renderer's ground inset; retires `texture-atlases.json`'s `nature` entry and unblocks the `nature-tiles` export. Rationale and alternatives in `nature-tiles-material-provenance.md`. |

External discovery and additive merge use the shared catalog-provider contract
defined in `submod-catalog-contract.md`; tilesets
deliberately do not support overrides.
The optional filler-dispatch field, more filler tunables, resolver/marker data,
and richer overlay tags remain direction, not contracted work.
