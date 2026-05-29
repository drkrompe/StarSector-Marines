# Engine/game package split — ✅ SHIPPED

A pure structural refactor (zero behavior change) landing **before** the GROUND
pass migration, so that migration lands on a clean engine/game seam. Makes the
**engine (reusable mechanism) vs game (specific behavior)** boundary obvious in
the package structure — the MoonLight-style split the user reasons about.

## What landed

The render mechanism moved out of `ops/battleview` (game) into `render2d`
(engine), leaving the concrete battle passes + orchestration game-side.

**Moved to `render2d` (engine):**
- **`BattleCamera`** — pure cell↔screen projection. It was already imported by
  `render2d` primitives (`RibbonBatch`, both FBO accumulators), so its
  `ops/battleview` home was a live **engine→game back-edge**; the move fixes it.
  ~11 importers repointed (`render2d` siblings dropped the now-same-package
  import; 8 game callers switched to `render2d.BattleCamera`; `DoodadRenderSystem`
  + `RenderContext` gained it).
- **`DrawCommand`** — the deferred-render command vocabulary (`SheetQuad` /
  `Sprite` / `Custom`). Only platform coupling is `SpriteAPI`. Its javadoc no
  longer doc-links *up* into game `DrawList`/`RenderSystem`/`BattleRenderer`.
- **`DrawListRenderer`** (NEW) — the drain extracted from
  `BattleRenderer.drainLayer`: `static drain(List<DrawCommand>,
  Map<SpriteAPI,QuadBatch>)` + `drawSprite`, lifted verbatim. Pure mechanism —
  knows nothing about which layers exist or their order.

**Stayed game-side in `ops/battleview` (deliberate — avoids an engine→game back-edge):**
- `RenderLayer` (the concrete paint-order value enum), `DrawList`
  (`EnumMap<RenderLayer,…>`), `RenderSystem` (producer hook keyed on
  `RenderContext`), `RenderContext`, `BattleRenderer`, `BattleSprites` + caches,
  `DoodadRenderSystem`.

**`BattleRenderer.drainLayer`** collapsed to a one-line delegator:
`DrawListRenderer.drain(drawList.commands(layer), batchBySheet)`. The loop seam
(`BattleScreen.render()` → `renderer.renderWorld(rc)`) is unchanged.

**Charters:** added `package-info.java` to `render2d` (engine core) and
`ops/battleview` (game render), in the repo's Category/Charter/Boundary format.

## The boundary (the point of the commit)

- **Engine (`render2d`)** owns *how*: batch primitives, GL bracketing, the
  camera projection, the command vocabulary, the drain. No game-domain imports.
- **Game (`ops/battleview`)** owns *what + what order*: the layer set, the
  per-frame context, the concrete systems, the orchestrator.
- Invariant established: a system appends `DrawCommand`s tagged by `RenderLayer`
  and never touches GL; the engine drains layers in ordinal order — **occlusion
  is an enforced ordered drain, not a call-order convention.**

## Verified

`mcp__intellij__build_project` clean after every step. Post-move invariant grep:
**no `render2d/**` file imports `ops.battleview.*`.** Behavior-neutral by
construction (drain body lifted verbatim, same GL order) — the two layers that
exercise `DrawListRenderer` (SHOTS, DOODADS) still draw identically; in-game
smoke check folds into the GROUND verification.

## Known impurity (deferred)

`DecalAccumulator` / `LightAccumulator` live in `render2d` but still import
game-data types (`Decal`, `SpriteSheetFrames`, `TimeOfDay`). Documented in the
`render2d` charter; reclassified clean once the FBO mechanism is separated from
its game-data binding — part of the batching-revisit story, not this commit.
