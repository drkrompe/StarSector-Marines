# S1 — Height/normal derivation pipeline

> **SHIPPED** `cf2e4db1` (2026-08-13). As planned, plus: kernel's wrap-at-edges
> generalized to an `EdgeMode{WRAP,CLAMP}` parameter instead of a forked copy;
> `TileSheetSlicer` vendors the runtime auto-strip slicing algorithm (tool
> source set can't depend on root `main`); recipes live in
> `asset-pipeline/src/tool/resources/tilemaps/tilemaps.json`. Baked 5 sheets
> (water, floors, nature, urban-3, spaceport-apron); determinism verified
> byte-identical. Walls/structure sheets deliberately skipped.

## Goal

Build-time auto-generation of height and normal sheets from the existing
terrain tile-sheet albedos, so S2+ never needs hand-authored maps.

## Scope

1. **Vendor the kernel.** Copy MoonLightEngine's
   `TerrainMaterialDerivationKernel` into our `:asset-pipeline` **tool**
   source set as
   `com.dillon.starsectormarines.assets.tilemaps.TileMapDerivationKernel`
   (repackage; keep the algorithm faithful — luminance → blur →
   percentile-window → height; central-difference normals; drop the AO and
   roughness outputs for now, we don't consume them). Source of truth:
   `MoonLightEngine/asset-pipeline/src/main/java/com/dill/MoonLight/tools/assets/terrain/TerrainMaterialDerivationKernel.java`.
2. **Atlas-aware wrapper.** Sliced sheets are grids of independent cells:
   slice → derive per cell with **clamp** (not wrap) edge addressing →
   repack. Percentile windowing computed **per sheet** across all cells,
   then applied per cell, so relative height stays honest across tiles.
3. **Gradle task** `:asset-pipeline:deriveTileMaps` (pattern:
   `ProcessModelsTask`). Config: per-sheet recipe (cell size, blur radius,
   percentiles, polarity, normal strength). Outputs
   `<sheet>_height.png` and `<sheet>_normal.png` next to the source sheets
   under `mod/graphics/`.
4. **Bake the initial set** for the ground/terrain sheets (urban ground,
   nature tiles, water) and check the outputs in (they're small PNGs;
   deterministic kernel means diffs are meaningful).

## Non-goals

- No runtime code, no loading of the sheets (S2 consumes them).
- No AO/roughness outputs.
- No unit-sprite derivation (S4).

## Acceptance

- `gradlew :asset-pipeline:deriveTileMaps` is deterministic (re-run ⇒
  byte-identical PNGs).
- Height sheets visually plausible when inspected (bricks/rocks read as
  relief; flat tiles stay near mid-gray, not stretched to full range).
- Normal sheets are OpenGL-convention (+Y up), mid-blue flats.
- No cross-cell contamination: a cell's derived pixels depend only on that
  cell's albedo.
