# Story D — First sheet-batched pass + RenderSystem (DOODADS) — ✅ SHIPPED

## What landed

The `DOODADS` pass migrated into the draw-list model as the first **sheet-batched**
pass and the first standalone **`RenderSystem`** — exercising the `QuadBatch`
sheet-grouping path that Story C (SHOTS) didn't.

New in `ops/battleview/`:
- **`RenderSystem`** — the pull-model producer contract: `collect(RenderContext,
  DrawList)`. Stateless, reads services/camera off the context, appends commands;
  never touches GL. Holds only immutable refs (e.g. `BattleSprites`).
- **`DoodadRenderSystem`** — implements `RenderSystem`; reads `sim.getDoodads()` +
  camera + sprites, emits one `SheetQuad` per doodad (urban or road sheet, full
  `TILE_SIZE` sub-rect, cell-center dst). Verbatim port of the old
  `renderDoodads` logic, incl. the `tileSheet()==null` early-out.
- **`DrawCommand.SheetQuad`** — new variant: a sub-rect of a named sheet, batched
  via that sheet's `QuadBatch`. The many-quads-one-sheet form (tiles, doodads,
  roofs). Fields = `QuadBatch.append` signature (sheet + src rect + dst
  center/size + rgba); axis-aligned, no rotation field yet.

Changed:
- **`DrawCommand.SpriteQuad` → `Sprite`** (the Story-C single-sprite/`renderAtCenter`
  command). Renamed so the vocabulary is unambiguous: `Sprite` = whole-texture
  `renderAtCenter`; `SheetQuad` = batched sheet sub-rect. (`drawSpriteQuad` →
  `drawSprite`; the SHOTS `collectShots` emit updated.)
- **`BattleRenderer.drainLayer`** gained a `SheetQuad` branch: groups consecutive
  `SheetQuad`s by sheet identity into their `QuadBatch`es and flushes the touched
  batches under one `GlStateBracket`. Resolved via a new `batchBySheet`
  (`IdentityHashMap<SpriteAPI, QuadBatch>`) populated in `buildTileBatches()` —
  reuses the same batch instances the inline tile passes use (batches reset after
  flush, so cross-pass reuse within a frame is the documented pattern).
- `renderWorld`: `renderDoodads(...)` → `doodadSystem.collect(rc, drawList)` +
  `drainLayer(DOODADS)`, at the same seam (between vehicles and highlights).
  `renderDoodads` deleted; unused `Doodad` import dropped.

## Verified

`mcp__intellij__build_project` clean. **In-game render check still required**
(render-behavior change): doodads (rocks/plants/debris) appear at the right cells,
right sheet, under units and above ground/decals — and projectile/tracer/contrail
SHOTS still render identically (touched by the rename).

## Deviations from spec / follow-ups

- **Inter-sheet flush order is first-touched, not the old fixed road→urban.** The
  old `renderDoodads` flushed roadBatch then urbanBatch (road-under-urban);
  `drainLayer` flushes touched batches in first-touched order. Visually identical
  because doodads are one-per-cell point overlays with no cross-sheet pixel
  overlap (documented in `drainLayer`). Within a single sheet, append order = draw
  order is preserved. If a future sheet-batched pass *does* need deterministic
  inter-sheet order, the drain will need an explicit per-layer sheet order.
- **`SheetQuad` has no rotation field.** Doodads are axis-aligned. The rotated
  form (for UNITS sprite frames via `QuadBatch.appendRotated`) is added when that
  pass migrates — YAGNI for now.
- **`RenderSystem` registry not yet introduced.** Story D wires the single
  `doodadSystem` by hand in `renderWorld`. A `List<RenderSystem>` + the final
  systems-loop comes as more passes migrate (the "collapse `render()`" endgame).
- Story B carry-overs still open: `MARINE_TRACER`/`DEFENDER_TRACER`/`bearingDeg()`
  duplication; fuller inter-pass comments; pre-existing unused imports
  (`ShuttleType`, `LightKernel`).

---

# Original design / spec

D…N of the battle-render reorg: "one pass per slice into `RenderSystem`s." Story D
picks the first **sheet-based** pass so the slice finally exercises the
`QuadBatch` sheet-grouping path SHOTS didn't, and introduces the `RenderSystem`
interface + the sheet+sub-rect command form.

Chosen pass: **DOODADS** — the smallest sheet-batched pass (already drove
`urbanBatch`/`roadBatch` via `QuadBatch` with explicit flush), so it proves the
batched path + the command form at lowest risk before GROUND/UNITS.
