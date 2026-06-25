# Moddable tilesets — Next Session

Read [`overview.md`](overview.md) first (concept, the three tile systems,
the data/algorithm seam, both schemas). Active story:
[`stories/phase-1-tile-registry.md`](stories/phase-1-tile-registry.md).

## State of play

- **Design captured; no code yet.** This is a fresh design-stage track.
  Approach agreed with the user: dual-JSON (tileset def ↔ gen mapping) fed
  into an id-addressed `TileRegistry`.
- The headline constraint to keep in front of mind: **tiles are pure data
  (fully JSON-able); generation is data + algorithm** (pools/chances are
  data, the wetland carve is not). The mapping JSON names a *code* filler
  and supplies its tunables — it is not "the generator in JSON."
- We are **not starting from zero**: `tools/tilesets/TilesetCatalog.java`
  already loads a per-sheet `data/tilesets/<basename>.catalog.json` at
  runtime (sandbox-safe `SettingsAPI` path, saves/common round-trip).
  Phase 1 promotes that file from labels to authoritative tile defs.

## Next up (priority order)

1. **Phase 1, Slice 1a** — `TileDef` + `TileRegistry` + the load hook in
   `onApplicationLoad`; author `nature-tiles.tileset.json` +
   `urban-tileset-3.tileset.json` as a superset of their `.catalog.json`;
   registry self-check (frame count == entry count). **No consumer changes
   yet** — add the parity test (registry semantics == enum fields) as the
   oracle. See the story for the full slice breakdown.
2. **Slice 1b** — sliced consumers (`NatureTile`/`UrbanTile3`) read
   semantics by id; `canOverlay` → `validOn`.
3. **Slice 1c** — grid sheets (`TileManifest` origins → named-layout
   blocks). Heavier; may split into its own story — ship 1a+1b first.

Then **Phase 2** (gen mapping as data) and **Phase 3** (mod-merge,
deferred until a real submod exists).

## Decisions locked (don't relitigate)

- Dual JSON, not one (tileset def vs. mapping have different lifecycles).
- Id-addressed registry is the linchpin, landed first; valuable before any
  submod (kills enum-order-= -PNG-order + hardcoded `(col,row)`).
- Mapping JSON names code fillers; carve algorithms stay in Java. **No
  scripting layer.**
- Promote `.catalog.json` in place — one file per sheet, not a fork.
- Nests under `GenRecipe` (already shipped, `7016b8e`), doesn't collide:
  recipe = stage order; mapping = tile/param content.

## Sanity check before resuming

- `gradlew.bat compileJava` clean.
- `gradlew.bat :test --tests "*BspMapPreviewTest*"` — the behavior-
  preservation oracle (same seed → same PNG). Re-run after any consumer
  migration; a diff is a regression.
- In-game `TilesetDebugScreen` — the visual A/B for slicing/semantics.
