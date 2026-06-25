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

#### 1a critique follow-ups

Background critique of `99de776` (fix-before-1b verdict). Resolved in `2859234`:

- **[parity hole, fixed]** The oracle looked tiles up *by* `frame`, so a frame
  swap between two same-semantics tiles passed green — the silent-shift this
  track exists to kill, relocated into the JSON. Now pinned via an explicit
  enum→id table with `def.frame == frameIndex()` asserted.
- **[fixed]** Missing `frame` now fails loud (was silently `-1`); `layer:`
  selector tokens validated against `TileLayer` at load.

Deferred (not blockers — carry into the noted slice):

- **`validOn` exclusion is id-only, no `!layer:` form.** "Non-water ground" is
  only expressible by enumerating `!water` ids, so adding a third water tile
  forces hand-editing every rock's `validOn`. Revisit in **Phase 2** with a
  tile `tag`/kind so `!tag:water` works; until then the redundancy is tolerable
  (two water tiles).
- **No strict unknown-key validation.** A typo'd field key (`"layr"`) defaults
  silently rather than failing. A strict-schema pass is **Phase 3** hardening
  (mod-facing diagnostics), disproportionate for the built-in sheets now.
- **Enum `label` not carried into `name`/`description`.** The JSON uses its own
  short names; the enums' human labels ("sand-ish", "medium rock (light
  cover)") aren't round-tripped. Debug-only; fold into the **1c** viewer cutover
  when `.catalog.json` is promoted/deleted.

### 1b — sliced consumers read by id (FULL CUTOVER — delete the enums)

Decision (user, full cutover over thin-delegation): call sites resolve
`TileDef` by id; `NatureTile` / `UrbanTile3` are **deleted this slice**, not
kept as a thin façade.

**Storage decision (the load-bearing one).** With the ordinal gone, a cell
can't store `NatureTile.ordinal()+1`. `CellTopology` stores the **dense
registry index** as an opaque tile handle (`short`, `index+1`, 0 = none —
future-proof past a `byte` as 1c adds grid tiles). `getNatureOverlayIndex` /
`setNatureOverlayIndex` (int handle, <0 = none); CellTopology stays
**registry-free** — callers resolve `reg.byIndex(handle)`. (Step 1 —
`TileDef.index` + `byIndex`/`indexOf` — shipped `1b308ba`.)

**DI decision (PIVOTED — singleton, not GenContext threading).** Sizing the
gen-orchestrator DI showed `BspCityGenerator.generate()` is called from ~8 test
files and `new GenContext(...)` from several more — threading a registry param
through all of them is a 10+ file sweep. Pivoted to the **`TileRegistry.installed()`
process-wide singleton** (the registry is a read-only asset catalog, exactly the
shape of the codebase's `Global.*` services): `NatureZoneFiller` /
`GroundRenderSystem` read `installed()`; `onApplicationLoad` sets it in-game.
Tests install a disk-loaded registry via an **auto-registered JUnit extension**
(`TileRegistryTestInstaller` + `META-INF/services` + `junit-platform.properties`
autodetection), so every test runs the production registry-backed path —
guarded by `TileRegistryBootstrapTest`. `NatureZoneFiller` keeps an
`installed() == null` fallback (paint ground, skip overlays) as defense.

**Cutover sequence — ✅ ALL SHIPPED:**
- **(i) ✅ `1b308ba`** — dense `TileDef.index` + `byIndex`/`indexOf` (additive).
- **(ii–iv) ✅ `fa36eb3a`** — every consumer reads `TileDef` via the registry,
  in one coherent commit: `CellTopology` overlay storage `byte[ordinal+1]` →
  `short[index+1]` (`get/setNatureOverlayIndex`); `NatureZoneFiller` pools →
  id-resolved `TileDef` (RNG draw order preserved exactly), `canOverlayOn`,
  stamp by index; `GroundRenderSystem`/`SlicedTileDrawer` take `TileDef`
  (`.frame` + `.isGround()`); `TileManifest.pickNature*`/`pickStreet3Sidewalk`
  return ids; loader frame-count guards → registry count; `TilesetDebugScreen`
  reads the registry; the test bootstrap. (Delegated to a Sonnet sweep; the
  behavior-critical `NatureZoneFiller`/`CellTopology` diffs reviewed on main.)
- **(v) ✅ `51841174`** — deleted `NatureTile` + `UrbanTile3`; converted
  `TileRegistryParityTest` to a frozen golden table (id/frame/layer/cover/
  passable + the canOverlay placement contract as explicit rules); reseated the
  dangling `{@link}`s.

**Parity:** behavior-preserving by construction — `TileDef.frame == old
frameIndex()` (golden-pinned), `isGround()` matches, `NatureZoneFiller` RNG draw
order untouched. Tile/gen/map suite green (`TileRegistry*`, `NatureZone`/
`FixedGrid`/`Street`/`BspMapSprite` previews, `BspMapPreview`, `MapValidationScan`,
`TacticalRegion`).

**1b is DONE.** The sliced sheets are fully data-driven; no enum remains.

### 1c — grid sheets as named-layout blocks (SCOPED — live blocks only)

Decision (user): **scoped to the live grid blocks + dead-code cleanup**, not a
full `TileManifest` port.

**Gut-check correction (don't re-derive).** The "lots of dead code" premise was
wrong: `pickWallTile` is **live** via `WallMasks.pickTileFromMask`; the Floors/
Water *edge* resolvers (`pickGrass/Stone/Dirt/Sand/Snow/WaterTile`) are
production-center-only BUT exercised by the dev-tool preview tests
(`FixedGridZonePreviewTest` renders the full autotile). So genuine dead code is
minimal — 1c is a **faithful port of working (legacy) resolvers**, not a purge.
The doodad pools + turret-embankment helpers (woven through ~15 fillers/stampers)
are *gen mapping*, not tile defs → **Phase 2**, not 1c.

**Model (`battle/world/tiles/`):** `GridBlockDef` (sheet, `cellPx`, origin,
`GridLayout`, optional `fillRgb`) is the grid counterpart to the sliced
`TileDef`; `GridLayout` is the named resolver enum (`SINGLE`, `FLOOR_3X3`,
`WALL_3X3`, …) — faithful ports of the `pickXxx` geometry, parameterized by
origin. JSON `blocks` array + `cellPx`; ids share the tile namespace.

**Foundation — ✅ shipped `6d30f529`** (additive, no consumer reads it yet,
mirrors 1b step i): `GridLayout`/`GridBlockDef` + registry `blocks` ingest +
`urban-tileset.tileset.json` (wall/floor/rubble + door-open) + `GridBlockParityTest`
(each resolver pinned to its `TileManifest` picker across all 16 masks, incl. the
wall enclosed/null + `0x060A10` fill). Verified green.

**Remaining (consumer migration, per sheet):**
- `GroundRenderSystem` floor (INDOOR) / rubble / door dispatch → resolve block id;
  `WallMasks.pickTileFromMask` → `block("urban.wall").resolve(...)`.
- Next sheets: `urban-tileset-2` (road / courtyard / striped), then the Floors/
  Water center grounds + brick/tile single tiles. Each adds its `GridLayout`(s) +
  `.tileset.json` blocks, parity-pinned to the picker, then flips the consumer.
- Migrate `TilesetDebugScreen` to `.tileset.json`; delete the `.catalog.json`
  duplication; carry the catalog labels.
- The dev-tool preview tests (`FixedGridZonePreviewTest`) move to block ids too.

> The `nature` vs. `floors` grass fork at `GroundRenderSystem` GRASS/DIRT is
> already on registry ids (Phase 1b); the `floors.*-center` blocks only need to
> cover STONE/SAND/SNOW/WATER + the legacy fallback.

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
