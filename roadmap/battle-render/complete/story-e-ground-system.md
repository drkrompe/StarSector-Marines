# Story E — GROUND tile/wall pass → GroundRenderSystem — ✅ SHIPPED (in-game verify pending)

The heaviest render pass (`renderGrid` + `renderTiledFloorsAndWalls`, the top
render-CPU root per the backlog JFR capture) migrated into the command model.
Commits `2ee5b89` (engine: pooled buffer + SolidRect + strict-painter drain) and
`dadcf8b` (game: `GroundRenderSystem` + wiring).

## The allocation problem this solved

GROUND walks the whole grid every frame (MEDIUM 144×80 ≈ 11.5k cells; LARGE
240×160 ≈ 38k). Naive per-tile `DrawCommand` emission with immutable records
would allocate ~N records/frame on the render hotspot. So the command model was
reworked **pooled**:

- `DrawCommand` is now one **mutable tagged** type (`Kind` + union fields), not a
  sealed record hierarchy. Adds `SOLID_RECT`.
- `DrawList` keeps a per-layer growable `DrawCommand[]` + count; `add*` methods
  recycle the next slot in place; `clear()` resets counts (keeps objects). Dense
  passes allocate nothing in steady state.

## Drain: strict painter-order

`DrawListRenderer.drain` replays a layer's buffer in submission order — submission
order *is* paint order, no per-layer config in the engine. It coalesces
consecutive same-sheet `SHEET_QUAD`s into one `QuadBatch` flush and `SOLID_RECT`s
into one `SolidQuadBatch` flush, flipping on sheet/kind change. One
`GlStateBracket` spans the whole layer (batches interleave under it by design);
`CUSTOM` drops out of the bracket and owns its GL. This replaced the prior
first-touched grouping (which couldn't guarantee cross-sheet overlay order).

## GroundRenderSystem

Faithful port of the kind-switch + autotiles + sidewalk/road-boundary predicates
+ nature overlays + doorways + crosswalk stripes + backing fill + no-tilesheet
fallback. Emits per-tile commands in **cell order**: backing `SolidRect` first,
then per non-wall cell its base tile → nature overlay → doorway, then a second
pass for wall tiles. Per-cell base-before-overlay ordering + strict-painter give
correct layering; spatial coherence (contiguous street/grass regions) keeps each
sheet's run long, so batching matches the old per-sheet-batch flush count.

DOODADS converted to the same model (emits road-then-urban contiguous).

## Verified

`mcp__intellij__build_project` clean; `gradlew test` green. **In-game render
verification still required** (correctness of every tile kind, overlays, walls,
fallbacks). The old `renderGrid`/`renderTiledFloorsAndWalls`/`draw*`/predicate
methods are retained `@Deprecated` + **uncalled** in `BattleRenderer` as a
one-line-rewire rollback; **delete them once the live battle confirms parity**
(immediate follow-up).

## Notes / follow-ups

- Per-tile command allocation is now zero steady-state, but per-frame command
  *count* is high (one per cell) — feeds the deferred `QuadBatch.flush` perf
  spike as a measurable input.
- Bracket count per GROUND frame is now 1 (was 2), vs the per-run risk avoided by
  the one-bracket drain rework.
