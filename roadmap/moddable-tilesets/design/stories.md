# Moddable Tilesets — Open Stories

Status: ACTIVE — 1 proposed story, 1 in progress

Written: 2026-08-23

Updated: 2026-08-28 — added the authoring MCP server story.

Read `moddable-tilesets-nouns.md` before changing a moddable-tilesets story.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `nature-variant-pool-authority-cleanup.md` | Proposed | Runtime grass/dirt primary variant membership remains hardcoded in `TileManifest`; preserve coordinate-hash parity while moving that membership to declared content. |
| `authoring-mcp-server.md` | In progress | Headless stdio server over the same domain code the Tilesets page uses; first cut of the tools is landed and tested. Awaiting review of whether the tool set is the right one before folding. |

External discovery and additive merge use the shared catalog-provider contract
defined in `submod-catalog-contract.md`; tilesets
deliberately do not support overrides.
The optional filler-dispatch field, more filler tunables, resolver/marker data,
and richer overlay tags remain direction, not contracted work.
