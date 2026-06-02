# Phase 3a — fog + roofs → command model (Bucket A)

> **✅ SHIPPED & VERIFIED** (commit `d874cf8`). Both passes migrated from inline
> `Custom`/bracket-flush to the strict-painter drain — `collectFogOverlay` emits
> `SOLID_RECT`, `collectRoofs` emits `SHEET_QUAD` (floors sheet already
> registered). Two bespoke `GlStateBracket` flushes deleted; collect is now
> GL-free for both layers. No engine additions. In-game verified: roofs render
> and fade with character FoV; fog edge-darkening gradient correct.
>
> **Follow-up surfaced during verify** (out of this track): roof reveal can
> *under*-reveal — a unit can shoot into a room without the roof fading, because
> `BuildingVisibilityPass` reveals via the **single closest** contributor + a
> **5-point** perimeter sample (corners + center), which diverges from per-shooter
> per-target-cell shooting LoS. Logged under fog-of-war backlog
> ("Merge `BuildingVisibilityPass` into the fog bitmap"). Sim concern, not render.
>
> ---
> *Original plan below (kept for context).*
>
> The two clean wins from the gameplay-geometry
> triage (see [`next-session.md`](../next-session.md) "pick from the shelf"). Pure
> production passes that are *already batch-shaped* — they build a `render2d`
> batch internally and flush it under their own `GlStateBracket`. Migrating folds
> them into the strict-painter drain and deletes two bespoke brackets. Lowest-risk
> migration on the shelf; no engine additions required.

## Scope

Two `Custom`-wrapped passes in `BattleRenderer`, both production (not debug, not
placeholder):

- **`renderFogOverlay`** (`BattleRenderer:381`, `RenderLayer.FOG`) — per-cell
  fog-of-war darkening. Builds `solidBatch.appendRect(...)` per visible cell, then
  `try (GlStateBracket … ) { solidBatch.flush(); }`.
- **`renderRoofs`** (`BattleRenderer:425`, `RenderLayer.ROOFS`) — building-roof
  tiles that fade with `Building.currentAlpha`. Builds `floorsBatch.append(...)`
  tinted small tiles via `appendSmallTileTinted`, then flushes under a bracket.

## Why these are unblocked (no new infra)

The drain already owns the batchers these passes use:

- `solidBatch` (the `SolidQuadBatch` field, `BattleRenderer:141`) is the **same
  instance** passed into `DrawListRenderer.drain` (`BattleRenderer:717`). Fog's
  `out.addSolidRect(FOG, …)` routes straight through it — `SOLID_RECT` is an
  existing command kind.
- The floors sheet is **already registered** in the drain's `batchBySheet` map via
  `registerBatch(sprites.floorsSheet(), floorsBatch)` (`buildTileBatches`,
  `BattleRenderer:285`). Roofs' `out.addSheetQuad(ROOFS, sprites.floorsSheet(), …)`
  groups into that registered `QuadBatch` automatically — `SHEET_QUAD` is existing.

So there is **no command-kind gap and no batch-registration gap** (unlike Bucket B,
which needs an arc primitive). The migration is mechanical.

## Plan

1. **Fog.** Change `renderFogOverlay(sim, alphaMult)` →
   `renderFogOverlay(sim, DrawList out, alphaMult)`; replace the per-cell
   `solidBatch.appendRect(sx, sy, sx+cellPx, sy+cellPx, 0,0,0, fogAlpha*alphaMult)`
   with `out.addSolidRect(RenderLayer.FOG, sx, sy, sx+cellPx, sy+cellPx, 0,0,0,
   fogAlpha*alphaMult)`. Delete the local `try (GlStateBracket …) { solidBatch.flush(); }`.
2. **Roofs.** Same shape: thread `DrawList out`; rewrite `appendSmallTileTinted` to
   emit `out.addSheetQuad(RenderLayer.ROOFS, sprites.floorsSheet(), srcPxX, srcTopPxY,
   srcPxW, srcPxH, cx, cy, cellPx, cellPx, r, g, b, alphaMult)` (the src-rect inset
   math is unchanged — it just feeds the command instead of the batch). Delete the
   local flush bracket.
3. **Producers.** The `RenderSystem.of(FOG, …)` / `…(ROOFS, …)` registrations
   currently emit an `addCustom` wrapping the inline method. Repoint them at the new
   signature: `(ctx, out) -> renderFogOverlay(ctx.sim, out, ctx.alphaMult)` and the
   roofs analogue. Collect is now genuinely GL-free for these layers.
4. **GL-free-collect check.** `renderRoofs` calls `sprites.ensureFloorsSheet()`
   defensively. Collect must stay GL-free (the established J/I pattern hoists lazy
   loads to `BattleScreen.attach`). Confirm the floors sheet is already ensured
   before collect (it must be — `buildTileBatches` guards on
   `sprites.floorsSheet() != null` and runs at attach); if so, drop the in-collect
   `ensureFloorsSheet()` and rely on the null-guard. Fog touches no textures.

## Watch-outs

- **Painter order inside the layer.** Both layers paint into a single
  `RenderLayer` slot; emission order = paint order under the strict-painter drain.
  Fog cells and roof tiles have no intra-layer ordering dependency (uniform alpha
  blend), so loop order is immaterial — but keep the emit loops in their current
  order to be safe.
- **Don't delete the `solidBatch` / `floorsBatch` fields.** They remain the
  registered batch instances the drain looks up by sheet; we stop *calling* them
  directly but the drain still flushes through them.
- **Keep the bracket out.** After migration these passes emit no GL — the drain's
  single per-layer `GlStateBracket` covers the flush. Removing their local
  brackets is the whole point.

## Verification

- `get_file_problems` / `compileJava` clean.
- In-game: fog edges still darken correctly at the seen/unseen boundary
  (the `darkNeighbors` gradient), and building roofs still fade in/out over
  unseen interiors as units approach. Both were spot-checked working in the Final
  collapse verify — this should be visually identical, just drained.

## Out of scope (tracked separately)

- **Bucket B** — `renderObjectiveMarkers` + `compoundMarkers`: blocked on the
  arc-primitive decision. See
  [`geometry-markers-command-model.md`](geometry-markers-command-model.md).
- **Bucket C** — `highlights` (`HighlightOverlay`): mixed debug/gameplay fate. See
  [`geometry-highlights-command-model.md`](geometry-highlights-command-model.md).
