# Moddable tilesets — Next Session

Read [`overview.md`](overview.md) first (concept, the three tile systems,
the data/algorithm seam, both schemas). Active story:
[`stories/phase-1-tile-registry.md`](stories/phase-1-tile-registry.md).

## Commit chain so far

```
91ee3f2  docs(moddable-tilesets): new track — dual-JSON, id-addressed TileRegistry
99de776  moddable-tilesets: Phase 1a — id-addressed TileRegistry (sliced sheets)
2859234  moddable-tilesets: Phase 1a critique fixes — pin id→frame, fail-loud guards
1b308ba  moddable-tilesets: Phase 1b step 1 — dense tile index on the registry
fa36eb3a moddable-tilesets: Phase 1b step ii — consumers read TileDef via registry
51841174 moddable-tilesets: Phase 1b step v — delete NatureTile/UrbanTile3 enums
```

Critique-deferred design items (don't lose) are in the story's "1a critique
follow-ups": `!layer:` exclusion form → Phase 2; strict unknown-key schema →
Phase 3; enum `label` round-trip → 1c viewer cutover.

## State of play — Phase 1b DONE ✅

**The sliced sheets are fully data-driven; `NatureTile`/`UrbanTile3` are
deleted.** Every consumer reads `TileDef` from `TileRegistry.installed()`.
Key decisions as shipped:
- **Singleton, not GenContext DI.** Threading a registry through the gen
  orchestrator was a 10+ file sweep, so the registry is a process-wide
  `installed()` service (`Global.*`-shaped). Tests install a disk-loaded one
  via an auto-registered JUnit extension (`TileRegistryTestInstaller`), guarded
  by `TileRegistryBootstrapTest`. Without it, gen tests take `NatureZoneFiller`'s
  `reg==null` path (ground only, overlays skipped) → divergent RNG/previews.
- **Storage:** `CellTopology` overlay slot is now `short[index+1]` (opaque
  registry handle); `get/setNatureOverlayIndex`, registry-free.
- Behavior-preserving by construction (`frame == old frameIndex()`, RNG order
  untouched). Tile/gen/map suite green.

> **Concurrent-session note (2026-06-25):** the full `:test` shows 6 failures
> in `TurretDemolitionSystemTest`/`DroneCrashSystemTest`/`HubDemolitionSystemTest`
> — caused by another session's **uncommitted** `UnitRegistry`/`UnitType`/`World`/
> `NavigationService` changes, NOT this work (none of our files touch that code;
> our domain's tests all pass in isolation). Leave their files alone.

## Phase 1c — COMPLETE ✅ (SCOPED: live blocks)

User scoped 1c to the **live grid blocks + dead-code cleanup** (not a full
`TileManifest` port). Gut-check correction: genuine dead code is minimal —
`pickWallTile` is live via `WallMasks`, and the Floors/Water edge resolvers are
dev-tool-live (`FixedGridZonePreviewTest`). So 1c is a faithful port of legacy
resolvers. Doodad pools + turret embankment are *gen mapping* → Phase 2.

- **Foundation ✅ `6d30f529`** — `GridLayout` (SINGLE/FLOOR_3X3/WALL_3X3) +
  `GridBlockDef` + registry `blocks` ingest + `urban-tileset.tileset.json`
  (wall/floor/rubble/door) + `GridBlockParityTest` (resolvers pinned to the
  `TileManifest` pickers, all 16 masks). Additive — no consumer reads blocks
  yet. Verified green (via the init-script workaround — see below).
- **urban-tileset render flip ✅ `7fba02f4`** — floor/rubble/door + `WallMasks`
  wall resolve `urban.*` blocks; `emitWalls` fill from the block.
- **urban-tileset-2 (road sheet) ✅ `5a9402d2`** — `PERIMETER_3X3` + `STRIPED_3X3`
  layouts; STREET-fallback/COURTYARD/STRIPED/TILE/LZ_MARKER resolve `road.*`
  blocks; perimeter fill hoisted from `fillRgb`. 32px → road draw path.
- **Floors_Tiles + Water_tiles ✅ `0e1df6c9`** (parity **confirmed green
  2026-06-27** — `GridBlockParityTest` 7/7, incl.
  `floorsAndWaterVariantPoolsMatchCenterPickers`; was IntelliJ-only at commit
  time because a sibling refactor had the tree red). Insight: production is
  center-only → **variant pools** (`GridBlockDef` cells, hashed), NOT autotiles;
  reuse the existing 16px `floorsTile`/`waterTile` path (no new emitter). `SNOW`
  dropped (dead). **All four grid sheets flipped.**
- **Catalog fold-in ✅ `ab6e98ac`** (gradle-verified green 2026-06-27). User chose
  **fold into tileset JSON** + **retire test-only pickers + oracle** (AskUserQuestion
  2026-06-27). What landed: each grid sheet's `<sheet>.catalog.json` per-cell
  labels folded into its `.tileset.json` as a read-only `"cells"` array (one file
  per sheet); sliced sheets keep labels on their tile defs. `TileRegistry.cellLabel`
  (grid cells + sliced fallback `frame==col`), new `CellLabel`, `TileDef` gains
  `name`/`description`. `TilesetDebugScreen` → read-only viewer; the in-game catalog
  editor (`TilesetCatalog`/`Normalizer`, saves/common round-trip, `pullCatalogs`
  task, the 2 catalog tests) deleted. `TileRegistryCellLabelTest` pins the lookup.
- **Thread A — picker retirement ✅ `bfe76d9e`** (gradle-verified green). Authored
  via an ultracode workflow (map → migrate → retire → verify). Migrated the dev
  preview tests (`FixedGrid`/`Building`/`Street`/`BspMapSprite` zone previews) +
  `BattleRenderer` roof brick off `TileManifest.pickXxx` onto `reg.block(id).resolve(...)`;
  dropped the dead SNOW case (no `floors.snow` block). Deleted **14** test-only
  pickers + their private helpers/origin constants + `FL_TILE`, and the now-redundant
  `GridBlockParityTest` oracle. **Kept** the 3 production-live pickers
  (`pickNatureGrassTileId`, `pickNatureDirtTileId`, `pickWallTile`) + the public
  `*_FILL_RGB` constants + `TileFrame` + `FLOORS_TILE_SIZE`. (The grep-gated retire
  caught `StreetZonePreviewTest` as an unlisted consumer — migrated it too.)

**Phase 1c is done.** The full live tile surface — sliced + all four grid sheets +
per-cell labels — is data-driven through `TileRegistry`; `TileManifest` is now just
the kept production pickers (nature pools, wall fallback) + doodad/turret tables.

## Phase 2 — in progress (gen mapping as data)

Story: [`stories/phase-2-doodad-pools.md`](stories/phase-2-doodad-pools.md). First
target = the doodad pools. User chose (AskUserQuestion 2026-06-27) the **full**
"pools + cover as data" scope. Scope refinement (in the story doc): **prop
doodads become data-with-cover; resolver/marker doodads (turret embankments via
`turretEmbankment`, LZ arrows/pad/vent) stay code** per the data/algorithm seam.

- **Sub-slice 1 — core + data ✅ `fda40a33`** (gradle-green). `DoodadCover`
  (4-level) + `DoodadDef` (id/sheet/col/row/cover); `TileRegistry` parses a
  sheet's `"doodads"` array (23 `urban-tileset` prop cells, cover == today's
  `Doodad.defaultCoverFor`); new `GenMappingRegistry` (the Phase 2 seam) loads
  `data/tilesets/*.mapping.json` → `doodadPool(DistrictTheme)`; `urban.mapping.json`
  carries the 4 theme pools (order-preserved → seeded scatter unchanged). Wired
  into `onApplicationLoad`. `DoodadMappingParityTest` pins cover + ordered pool
  parity. **Additive — no consumer flipped.**
- **Sub-slices 2 + 3 — flip + retire ✅ `259a9b8e`** (gradle-green). Every doodad
  consumer flipped (~14 fillers + `UrbanMapGenerator`): pool sites read
  `GenMappingRegistry.doodadPool(...)`; `BuildingConfig` carries a String pool-id
  resolved at scatter; hardcoded-prop sites resolve defs by id; marker sites pass
  explicit cover; `DefensePostStamper` embankments keep explicit `COVER_HEAVY`.
  `Doodad.defaultCoverFor` + the two cover-deriving `TileFrame` ctors + the 4
  `TileManifest` pools + `doodadPoolFor` all **deleted**. Tests updated;
  `DoodadMappingParityTest` is now a frozen golden. Behavior-preserving (pool
  order kept → seeded scatter identical; def cover == old `defaultCoverFor`).
  Authored via Sonnet subagent fan-out.
  - **Refinement:** pools keyed by **String pool-id** (4 themes + `COMMERCIAL`)
    because `BuildingCommercialFiller` has a bespoke pool; `doodadPool(String)`
    primary + `doodadPool(DistrictTheme)` convenience.

**The doodad-pools slice is done** — `TileManifest` no longer owns any doodad
data; props are registry defs with cover, pools are `urban.mapping.json` data.

Follow-ups (story doc): **cover-gap tuning** (chairs/shelves/desks score `NONE`
today — preserved for parity, now a one-line JSON edit); resolver doodads
(embankments/arrows) stay code unless a submod needs to reskin them; later Phase 2
slices = `GroundKind` render dispatch + per-`BlockKind` filler params (groundPool/
chances), extending the same `*.mapping.json` + `GenMappingRegistry`.

Then **Phase 3** (mod-merge, deferred).

> **Concurrent-session friction (recurring):** another session's drone/turret/
> sim refactor has repeatedly left `battle/` main OR its test files
> non-compiling. When `compileTestJava` is blocked by THEIR test files
> (`DroneCrashSystemTest`/`HubDemolitionSystemTest`/`TurretDemolitionSystemTest`),
> run with `--init-script` excluding those files (see
> [[concurrent-session-broken-test-workaround]]); don't touch their files. Verify
> our own files via IntelliJ `get_file_problems` when gradle is wedged.

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
