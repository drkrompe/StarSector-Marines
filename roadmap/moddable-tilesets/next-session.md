# Moddable tilesets — Next Session

Read [`overview.md`](overview.md) first (concept, the three tile systems,
the data/algorithm seam, both schemas). Active story:
[`stories/phase-1-tile-registry.md`](stories/phase-1-tile-registry.md).

## Commit chain so far

```
91ee3f2  docs(moddable-tilesets): new track — dual-JSON, id-addressed TileRegistry
99de776  moddable-tilesets: Phase 1a — id-addressed TileRegistry (sliced sheets)
2859234  moddable-tilesets: Phase 1a critique fixes — pin id→frame, fail-loud guards
```

Critique-deferred design items (don't lose) are in the story's "1a critique
follow-ups": `!layer:` exclusion form → Phase 2; strict unknown-key schema →
Phase 3; enum `label` round-trip → 1c viewer cutover.

## State of play

- **Phase 1a shipped (`99de776`).** `TileRegistry` (id → `TileDef`) loaded
  in `onApplicationLoad`, fed from `data/tilesets/{nature-tiles,urban-tileset-3}.tileset.json`.
  `TileDef`/`TileLayer`/`TileCover` carry the per-tile semantics the enums
  hardcode; `canOverlay` → `validOn` selectors. **Not yet consumed** by
  render/gen — that's 1b/1c. `TileRegistryParityTest` (green) pins registry
  semantics to the `NatureTile`/`UrbanTile3` fields so the migration is
  provably behavior-preserving.
- **The headline constraint** (keep in front of mind for Phase 2): tiles
  are pure data; generation is data + algorithm (pools/chances are data,
  the wetland carve is not). The mapping JSON names a *code* filler and
  supplies its tunables — not "the generator in JSON."
- **Transitional dual-file:** `urban-tileset-3` now has both `.tileset.json`
  (authoritative, registry) and `.catalog.json` (debug viewer), content kept
  identical. The viewer cutover + `.catalog` deletion is 1c work.

## Next up (priority order)

1. **Slice 1b** — sliced consumers read by id. Migrate `NatureTile` /
   `UrbanTile3` semantic fields (`kind`/`cover`/`passable`, `canOverlay`) to
   resolve from `TileRegistry.installed()`; `NatureZoneFiller`'s overlay-
   legality check reads `TileDef.canOverlayOn` (pools/chances stay hardcoded
   until Phase 2). Move the `*Tileset` loaders' frame-count guard to cross-
   check the registry (the self-check deferred from 1a). Parity gate:
   `BspMapPreviewTest` + `TilesetDebugScreen` A/B unchanged.
2. **Slice 1c** — grid sheets (`TileManifest` origins → named-layout
   blocks); migrate `TilesetDebugScreen` to `.tileset.json` and delete the
   `.catalog.json` files. Heavier; may split into its own story.

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
