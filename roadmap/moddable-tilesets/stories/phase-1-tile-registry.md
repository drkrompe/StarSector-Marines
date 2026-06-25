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

### 1a — schema + registry + load (registry built, not yet consumed) — ✅ shipped (`99de776`)

- ✅ `TileDef` + `TileLayer` + `TileCover` + `TileRegistry`
  (`Map<String,TileDef>`, `tile(id)` / `has(id)` / `all()`). `TileDef.frame`
  is the sliced-sheet index; `(origin, layout)` block fields land in 1c.
- ✅ `nature-tiles.tileset.json` + `urban-tileset-3.tileset.json` (the two
  sliced sheets). `canOverlay` ported to `validOn` selectors (`"<id>"`,
  `"layer:ground"`, `"!<id>"`); `TileDef.canOverlayOn` evaluates them.
- ✅ Loaded in `onApplicationLoad` via `TileRegistry.loadBuiltins()`
  (defensive — never throws out of startup). Internal self-checks:
  duplicate-id fail-loud (`ingestSheet`), cross-tile `validOn` resolution
  (`validateReferences`).
- ✅ **No consumer changes.** `TileRegistryParityTest` is the oracle:
  registry semantics == `NatureTile`/`UrbanTile3` fields for every constant,
  plus the full `canOverlay` cross product, plus per-sheet count parity.

> **Deferred from 1a — frame-count vs. slicer self-check.** The "slicer
> output count == JSON tile count" guard needs the loaded texture, which
> isn't available at `onApplicationLoad`. Left as a runtime cross-check for
> when the `*Tileset` loaders consume the registry (1b). 1a validates
> JSON-internal invariants only.
>
> **Transitional dual-file.** `.tileset.json` (registry, authoritative) and
> `.catalog.json` (debug viewer) coexist for now — `urban-tileset-3` has
> both; their content is kept identical. The viewer migration + `.catalog`
> deletion is part of 1c (when grid sheets also get `.tileset.json`).
> `nature-tiles` never had a catalog, so no duplication there.

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
