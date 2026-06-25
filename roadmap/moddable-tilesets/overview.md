# Moddable tilesets

> Move tile definitions and their generation mappings out of hardcoded
> Java (`NatureTile`, `UrbanTile3`, `TileManifest`, the per-`BlockKind`
> filler presets) and into a **dual-JSON, id-addressed registry** so the
> tile catalog and the gen→tile mapping become *data a submod can extend*
> rather than enums a submod would have to recompile.

## What this is

Today a tileset is described in three different hardcoded shapes, and the
"which tile does the generator place here" mapping is scattered across
filler classes. This track replaces the hardcoded definitions with two
JSON layers fed into a runtime **`TileRegistry`**, addressed by stable
string id:

1. **Tileset JSON** — *what tiles exist in a sheet.* Slicing/grid info +
   per-tile semantics (layer, cover, passable, valid-on). Replaces the
   enums and the hardcoded `(col,row)` origins.
2. **Mapping JSON** — *how tiles map to generated things.* `GroundKind`→
   render-tile dispatch, and per-`BlockKind` filler **parameters** (pools,
   weights, chances). Names a *code* filler; supplies its tunables.

This is the standard data-driven content split — a **registry of what
exists** vs. **recipes for how it's used** — and it's the same
state-owner / stateless-consumer shape the rest of the codebase runs under
([[battle_services_systems]], [[user_artemis_ecs_framing]]): the registry
is the component/asset store, fillers and the renderer are the systems
that consume it by id.

## Why now

"May be moddable in the future" is the stated driver, but the linchpin —
**string-id addressing through a registry** — pays off *before any submod
exists*. It kills two standing fragilities:

- `NatureTile` / `UrbanTile3` encode "enum declaration order **is** the
  frame index on the PNG" (`NatureTile.java:64`, the `frameIndex() =
  ordinal()` contract). Re-export the sheet in a different order and every
  tile silently shifts. The `*Tileset` loaders only catch a *count*
  mismatch, not a reorder.
- `TileManifest` hardcodes `(col,row)` origins as private constants; a
  sheet redraw means hunting magic numbers (the file already carries
  several "moved here after the sheet was redrawn" / "known catalog gap"
  comments).

A `data/tilesets/*.tileset.json` keyed by stable id makes both
art-authoring concerns data edits, not code edits — useful to *us* now,
and the foundation a submod needs later. That keeps this honest against
[[feedback_ship_then_optimize]] (Phase 1 has standalone value; the actual
mod-merge machinery is deferred to Phase 3, when a second consumer exists)
while respecting [[feedback_no_stopgap_dev]] (the id-registry is the real
destination, not a throwaway — we build it once).

## The three tile systems (the thing the schema must unify)

A tileset JSON that only handles `NatureTile` solves a third of the
problem. There are three structurally different systems today:

| System | Source of truth | Shape |
| --- | --- | --- |
| **Sliced sheets** — `NatureTile`, `UrbanTile3` | enum order = slicer frame index | ordered tile list + per-tile semantics (`Kind`, `Cover`, `passable`, `canOverlay`) |
| **Fixed-grid autotiles** — `TileManifest` | hardcoded `(col,row)` origins + `pickXxxTile` resolvers | autotile **blocks** (3×3 / 5×5) with a layout convention, plus singleton doodads/decals |
| **Generation mapping** — `NatureZoneFiller` & siblings, `BlockKind`, the `GroundKind`→picker render switch | hardcoded pools / chances / dispatch | pools, weights, chances **plus carving algorithms** |

The data flow that ties them together:

```
BlockKind  ──BlockFiller──▶  GroundKind (per cell)  + optional NatureTile overlay
GroundKind ──renderer switch──▶  pickXxxTile()  ──▶  TileFrame(col,row)   [grid sheets]
                              └▶  NatureTile / UrbanTile3  ──▶  SlicedTileDrawer  [sliced sheets]
```

## The load-bearing seam: data vs. algorithm

The single most important design constraint, and the one a naive
"generator-in-JSON" schema gets wrong:

> **Tiles are pure data. Generation is data + algorithm.**

- A **tileset** is fully data-able. ✅ Phase 1.
- A **filler** has two tiers. `NatureZoneFiller` doesn't just pick from
  pools — it carves wetland puddles, BFS-rescues connectivity, lays dirt
  banks (`NatureZoneFiller.java:142-272`). The *parameters* (`PLANT_CHANCE`,
  `ROCK_CHANCE`, the ground/overlay pools) are data; the *carve* is an
  algorithm. JSON supplies the former; the latter stays in Java.

The same seam appears inside the tileset JSON: the autotile **layout
conventions** (`facing-outward-5x5`, `hollow-perimeter-3x3`,
`standard-3x3`) are the geometry algorithms in `TileManifest`'s
`pickFloorsAutotile` / `pickStandard3x3` / `pickWallTile` families. The
JSON **selects a layout by name and supplies its origin**; the resolver
stays in code.

Consequence for the mapping JSON contract: it's **"recipe parameters for a
code-named filler,"** not "the generator in JSON." A submod re-skins
(swap art + pools + chances) for free. A genuinely new carve registers a
code filler — until/unless we invest in a scripting layer, which this
track **explicitly punts** (see Non-goals).

## The existing seed: `.catalog.json`

We are not starting from zero. `tools/tilesets/TilesetCatalog.java`
already loads a per-sheet `data/tilesets/<basename>.catalog.json`
(`{col,row,name,description}` entries) at runtime — read by the in-game
`TilesetDebugScreen`, written to `saves/common` and pulled back by a
Gradle task. Today these are *human labels*, not consumed by gen/render.
Phase 1 is largely **promoting the catalog from notes to authoritative
tile defs**: add slicing/layout/semantic fields, keep the same load path
(which already respects the [[starsector_script_sandbox]] no-`java.io`
constraint via `SettingsAPI`), and make gen/render read it by id.

## Schema sketch

### JSON 1 — tileset definition (`data/tilesets/<sheet>.tileset.json`)

```jsonc
{
  "sheet": "graphics/tilesets/nature-tiles.png",
  "slice": { "mode": "auto-strip", "alphaThreshold": 16, "minGap": 4 },
  // grid sheets instead use: "slice": { "mode": "grid", "cellPx": 16 }
  "tiles": [
    { "id": "nature.grass",   "frame": 0,  "layer": "ground", "cover": "none",  "passable": true },
    { "id": "nature.shrub",   "frame": 7,  "layer": "plant",  "validOn": ["nature.grass", "nature.grass2"] },
    { "id": "nature.rock_lg", "frame": 17, "layer": "rock",   "passable": false, "validOn": ["!water"] }
  ],
  "blocks": [   // autotile blocks (grid sheets) — origin + a *named* layout; the resolver stays in code
    { "id": "floors.grass", "origin": [0, 5], "layout": "facing-outward-5x5" },
    { "id": "urban.wall",   "origin": [3, 0], "layout": "hollow-perimeter-3x3", "fillRgb": null }
  ]
}
```

- `id` is the linchpin — a stable namespaced string that replaces both
  "enum ordinal = PNG order" and the hardcoded `(col,row)`. It absorbs
  everything `NatureTile`'s fields + `canOverlay` encode.
- `frame` = ordinal into the slicer output (sliced sheets); `origin` =
  block top-left (grid sheets).
- `layer` / `cover` / `passable` / `validOn` carry the per-tile semantics.
  `validOn` generalizes `canOverlay` — explicit id list or a negated kind
  token (`!water`).

### JSON 2 — generation mapping (`data/tilesets/<recipe>.mapping.json`)

```jsonc
{
  "groundRender": {                         // GroundKind -> tile/block id (render dispatch)
    "GRASS":  "floors.grass",
    "WATER":  "water.autotile",
    "STREET": "urban3.street_square"
  },
  "fillers": {                              // BlockKind -> code filler + its tunables
    "NATURE_GRASSLAND": {
      "filler": "nature-zone",              // names a REGISTERED CODE class; the carve stays in Java
      "groundPool": [
        { "id": "nature.grass", "w": 80 }, { "id": "nature.dirt", "w": 15 }, { "id": "nature.sand", "w": 5 }
      ],
      "plantChance": 0.25, "rockChance": 0.12,
      "plantPool": ["nature.shrub", "nature.tuft1"],
      "rockPool":  ["nature.rock_sm1", "nature.rock_md1"]
    },
    "NATURE_BEACH": { "filler": "nature-zone", "rockChance": 0.18 }
  }
}
```

## The registry seam

```java
// loaded once in onApplicationLoad (merges every data/tilesets/*.tileset.json)
TileRegistry      // id -> TileDef (sheet handle, frame/origin, layer, cover, passable, validOn, layout)
GenMappingRegistry // GroundKind -> tile id; BlockKind -> filler id + param block (pools by id)
```

Code migrates from enum constants to id lookups: `NatureTile.GRASS_1`
becomes `registry.tile("nature.grass")`. The enum can survive the
migration as a **typed convenience over the built-in set** (its constants
resolve to ids), then retire once gen/render read ids directly. The
renderer's `GroundKind` switch becomes a registry lookup; a filler reads
its param block and resolves pools against the `TileRegistry`.

## Non-goals (explicit punts)

- **No scripting layer.** Carving algorithms stay in Java. A submod that
  wants a *new* carve registers a code filler; it does not author the
  algorithm in JSON. Reconsider only if re-skinning proves insufficient in
  practice.
- **No mod-merge machinery in Phase 1–2.** Override/extend semantics,
  load-order, cross-mod id collisions, and validation diagnostics are
  Phase 3 — built when an actual second consumer (a submod) exists, per
  [[feedback_ship_then_optimize]].
- **Not a rewrite of the autotile resolvers.** The `pickXxxTile` geometry
  is ported behind named layouts, not redesigned.

## Slice progression

| Phase | Story | What | Value without a submod |
| --- | --- | --- | --- |
| **1** | [`stories/phase-1-tile-registry.md`](stories/phase-1-tile-registry.md) | Promote `.catalog.json` → `.tileset.json`; build `TileRegistry`; render + `NatureTile`/`UrbanTile3` read by id. Behavior-preserving. | Kills "enum order = PNG order" + hardcoded `(col,row)` fragility. **This is the "tiles within a tileset" JSON.** |
| **2** | _(to author)_ | Extract filler pools/chances + `GroundKind` render dispatch into the mapping JSON + `GenMappingRegistry`; fillers read params. | Tile recipes become data; re-tuning densities is a JSON edit. **This is the "mapping to generated things" JSON.** |
| **3** | _(to author, deferred)_ | Mod-merge load order, id-override semantics, validation/diagnostics. | The actual submod story. Deferred until a second consumer exists. |

## Relationship to `GenRecipe` (don't design it twice)

`mapgen/` already shipped `GenRecipe` (`7016b8e`) — a named, ordered
`List<GenStage>` selecting map *types* (ConquestCity / LegacyUrban /
station layouts). That is a **coarser grain** than this track's mapping
JSON. They must **nest, not collide**:

- `GenRecipe` = which *stages* run for a map type (an orchestration list).
- Mapping JSON = which *tiles/params* a stage's fillers use (content).

A map-type recipe references mapping-JSON ids; the mapping JSON never
re-encodes stage order. Keep `FillDispatchStage` the seam where a recipe's
chosen mapping is bound into the per-`BlockKind` filler dispatch.

## Verification posture

- Phase 1 is **behavior-preserving**: the JSON must reproduce the current
  enum/`TileManifest` output exactly. Lock against the existing
  `BspMapPreviewTest` seed renders (same seed → same grid) and the
  `TilesetDebugScreen` visual A/B. A diff in any preview PNG is a
  regression.
- A registry self-check at load: every built-in `tileset.json` must
  resolve, and slice/grid frame counts must match the sheet (the count
  guard the `*Tileset` loaders do today, moved into the registry).
- Phase 2 keeps the invariant — JSON-driven pools must reproduce the
  hardcoded pools until a mapping deliberately diverges.

## Decisions

- **Dual JSON, not one.** Tileset (what exists) and mapping (how used) are
  different lifecycles and different authors (art vs. gen-tuning). One file
  would couple a sheet redraw to a gen-tuning edit.
- **Id-addressed registry as the linchpin, landed first.** Everything else
  hangs off stable string ids; do that before any mod-facing work.
- **Mapping JSON names code fillers.** The data/algorithm seam is explicit
  in the schema (`"filler": "nature-zone"`), so the schema can't promise
  expressiveness it can't deliver (the wetland carve).
- **Promote the catalog, don't fork it.** Reuse `TilesetCatalog`'s
  sandbox-safe load path and the debug viewer; extend the file, keep one
  source of truth per sheet.

## Cross-references

- `battle/world/tiles/` — `NatureTile`, `UrbanTile3`, the `*Tileset`
  loaders, `SpriteSheetSlicer`, `SlicedTileDrawer`, `FixedGridTileDrawer`.
  The sliced-sheet system Phase 1 migrates first.
- `battle/world/model/TileManifest.java` — the fixed-grid autotile origins
  + `pickXxxTile` resolvers that become named layouts.
- `battle/world/model/CellTopology.java` — `GroundKind` / nature-overlay
  per-cell storage; the render dispatch reads it.
- `battle/world/gen/BlockKind.java`, `bsp/fill/NatureZoneFiller.java` —
  the gen mapping Phase 2 extracts; the data/algorithm seam lives here.
- `tools/tilesets/TilesetCatalog.java` + `mod/data/tilesets/*.catalog.json`
  — the existing JSON seed Phase 1 promotes.
- [`../mapgen/composable-pipeline.md`](../mapgen/composable-pipeline.md) —
  `GenRecipe` / `GenStage` / `GenContext`; the coarser orchestration layer
  this nests under.
- [[battle_services_systems]], [[user_artemis_ecs_framing]] — registry =
  store, fillers/renderer = systems.
- [[starsector_script_sandbox]], [[sounds_json_schema]] — the
  `SettingsAPI`-based load path and JSON-resource conventions Phase 1
  reuses.

## How this directory is laid out

- **`overview.md`** (this file) — concept, the three systems, the
  data/algorithm seam, schemas, phasing. The stable view; edit rarely.
- **`stories/`** — active/queued story docs, one per phase.
- **`complete/`** — sealed shipped work (commit hash + what landed).
- **`next-session.md`** — handoff state for picking up cold.
