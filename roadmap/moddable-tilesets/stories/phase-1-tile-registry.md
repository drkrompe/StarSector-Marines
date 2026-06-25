# Phase 1 — id-addressed `TileRegistry` (tiles as data)

> Promote the per-sheet `.catalog.json` from human labels to authoritative
> tile definitions, build a runtime `TileRegistry` keyed by stable string
> id, and migrate the tile consumers to resolve **semantics** by id instead
> of from enum fields / hardcoded `(col,row)`. Behavior-preserving — the
> output PNGs must not move.

Read [`../overview.md`](../overview.md) first (the three systems + the
data/algorithm seam). This story is the **"tiles within a tileset" JSON**
half; gen-mapping is Phase 2.

## Scope

In:
- A `data/tilesets/<sheet>.tileset.json` schema (slice/grid + per-tile
  semantics + autotile blocks).
- `TileRegistry` (id → `TileDef`), loaded once in
  `StarsectorMarinesModPlugin.onApplicationLoad` via the `SettingsAPI`
  load path `TilesetCatalog` already uses (sandbox-safe —
  [[starsector_script_sandbox]]).
- Migrate the **sliced** sheets (`NatureTile`, `UrbanTile3`) to read
  semantics (layer / cover / passable / valid-on) by id.
- Migrate the **grid** sheets (`TileManifest` origins) to id-addressed
  autotile **blocks** with named layouts.

Out (later phases):
- `GroundKind`→tile dispatch as data, filler pools/chances as data → Phase 2.
- Mod-merge / override / load-order / cross-mod collisions → Phase 3.
- No scripting of carve algorithms (ever — see overview Non-goals).

## Slices

### 1a — schema + registry + load (registry built, not yet consumed)

- `TileDef` (sheet handle, `frame` *or* `(origin, layout)`, `layer`,
  `cover`, `passable`, `validOn`) and `TileRegistry` (`Map<String,TileDef>`
  + `tile(id)` / `has(id)`).
- Author `nature-tiles.tileset.json` and `urban-tileset-3.tileset.json` as
  a **superset** of the existing `.catalog.json` (keep `name`/`description`
  for the debug viewer; add `id`/`frame`/`layer`/semantics). The two
  sliced sheets first — they're the cleanest (ordered list = the enum).
- Load in `onApplicationLoad`; **self-check**: the slicer's detected frame
  count must equal the sheet's tile-entry count (the guard
  `NatureTileset` / `UrbanTile3Tileset` do today, moved into the registry
  and made fail-loud at load, not per-frame).
- **No consumer changes.** A test asserts the registry's per-id semantics
  equal the current enum fields for every `NatureTile` / `UrbanTile3`
  constant — the migration's parity oracle.

### 1b — sliced consumers read by id

- `NatureTile` / `UrbanTile3` keep `frameIndex()` (the slicer contract is
  unchanged) but their **semantic** fields (`kind`/`cover`/`passable`,
  `canOverlay`) resolve from the registry by a constant→id binding. Net:
  the enum becomes a typed convenience over the built-in set; the data
  lives in JSON.
- `canOverlay` → `validOn` resolution (explicit id list + `!water` kind
  token). `NatureZoneFiller`'s overlay-legality check reads the registry
  rule; its pools/chances stay hardcoded (Phase 2).
- Parity gate: `BspMapPreviewTest` + `TilesetDebugScreen` A/B unchanged.

### 1c — grid sheets as named-layout blocks *(may split into its own story)*

- `TileManifest`'s private `(col,row)` origins → `blocks` entries
  (`{id, origin, layout, fillRgb?}`); the `pickXxxTile` resolver families
  become **named layouts** (`facing-outward-5x5`, `standard-3x3`,
  `hollow-perimeter-3x3`, …) selected by the block's `layout`, **resolver
  geometry unchanged**.
- `GroundRenderSystem.sameKindAutotile` (and the wall/road/floor pickers)
  resolve a block id → origin + layout instead of calling the static
  `TileManifest.pickXxx` directly. The 9-case neighbor→cell math stays in
  the layout resolver.
- This slice is heavier (autotile layouts, multiple sheets, the
  `nature` vs. `floors` grass fork at `GroundRenderSystem.java:296-313`).
  If it grows, split into its own story and ship 1a+1b first — they
  already deliver the headline win for the sliced sheets.

## Verification

- **Behavior-preserving.** Same seed → byte-identical preview PNGs
  (`BspMapPreviewTest`); `TilesetDebugScreen` visual A/B holds.
- **Registry self-check at load** fails loud on frame-count drift or an
  unresolved built-in id (replaces the current per-frame `LOG.warn`).
- **Parity test** (1a): registry semantics == enum fields for every
  built-in constant. This is the contract that lets 1b delete the enum
  fields safely.

## Risks / watch-items

- **`frameIndex() = ordinal()` is the thing we're killing** — but the
  slicer still returns frames in PNG order, so the tileset JSON must pin
  `frame` explicitly (don't re-derive from list position, or we've just
  moved the fragility into the file).
- **Lazy-load timing.** The `*Tileset` loaders slice on first
  `ensureLoaded`; the registry needs the slice result for its count
  self-check. Either slice eagerly at load for the count check or defer the
  check to first use — decide in 1a (lean eager: it's a one-time cost and
  makes drift fail at startup, not mid-battle). Mind [[sprite_lazy_load]]
  (textures NPE on dimension queries before `loadTexture`).
- **Two source files per sheet** (`.catalog.json` + `.tileset.json`) would
  be a regression — promote in place: one `.tileset.json` per sheet, the
  debug viewer reads the superset.

## Cross-references

- [`../overview.md`](../overview.md) — concept, schemas, the seam.
- `tools/tilesets/TilesetCatalog.java` — the load path + saves/common
  round-trip to reuse; `TilesetDebugScreen` — the visual A/B harness.
- `battle/world/tiles/{NatureTile,UrbanTile3,SpriteSheetSlicer,SlicedTileDrawer,FixedGridTileDrawer}.java`.
- `battle/world/model/TileManifest.java` — origins + resolvers → blocks/layouts (1c).
- `ops/battleview/GroundRenderSystem.java:296-313` — the `GroundKind`
  autotile dispatch 1c routes through the registry.
