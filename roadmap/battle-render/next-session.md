# Battle render reorg — next session

## State of play

Concept doc written ([`overview.md`](overview.md)). **Stories A + B + C shipped.**
- A — `BattleSprites` asset registry ([`complete/story-a-extract-assets.md`](complete/story-a-extract-assets.md)).
- B — `BattleRenderer` + `RenderContext`; world-render pipeline severed from the
  loop ([`complete/story-b-battlerenderer.md`](complete/story-b-battlerenderer.md)).
- C — `RenderLayer` + `DrawList` + `DrawCommand` + the `drainLayer` drain; the
  `SHOTS` pass converted to emit commands
  ([`complete/story-c-drawlist-model.md`](complete/story-c-drawlist-model.md)).

The draw-list model is now proven on one layer. `renderWorld` clears the
`DrawList`, runs the ~16 still-inline passes, and at the SHOTS seam does
`collectShots(...)` → `drainLayer(SHOTS)`. `DrawCommand` has `SpriteQuad`
(single rotated sprite) + `Custom` (own-GL escape hatch — contrails + tracers).

## Next-up

**Story D — migrate the next pass into the command model.** Pick a **sheet-based**
pass (tiles/`GROUND` or `UNITS`) so this slice finally exercises the `QuadBatch`
sheet-grouping path that SHOTS didn't (SHOTS only had single sprites + customs).
This is where:
- `SpriteQuad` likely gains (or gets a sibling for) the **sheet + sub-rect**
  form so the drain can group by sheet and flush via `QuadBatch`.
- `SolidRect` lands as a first-class command (HP bars, fills) if UNITS is chosen.
- The **`RenderSystem` interface** (`collect(ctx, drawList)`) + a small registry
  is introduced — D…N is "one pass per slice into `RenderSystem`s." Story C kept
  `collectShots` as a private method on `BattleRenderer`; D is the right time to
  lift the interface.

Then D…N convert the remaining passes one per slice; Final collapses `render()`
to the systems-loop + drain.

Carry-over follow-ups (do opportunistically, not blockers): dedupe
`MARINE_TRACER`/`DEFENDER_TRACER`/`bearingDeg()` duplication; restore the fuller
inter-pass comments in `renderWorld`.

## Slice chain

A (assets) → B (`BattleRenderer`/`RenderContext` extraction, verbatim) →
~~C (prove `RenderLayer`/`DrawList`/drain on one layer)~~ ✅ →
D…N (one pass per slice into `RenderSystem`s) → Final (collapse `render()`).

## Watch-outs

- Pass order in `renderWorld` is semantic — `RenderLayer` is the verbatim list.
  When migrating a pass, emit into its existing layer; don't re-derive order.
- Keep every batched flush inside a `GlStateBracket` (polluted GL state). The
  drain owns this for `SpriteQuad` runs; `Custom` callbacks own their own.
- `Custom` escape hatch is proven on the contrail accumulator (Story C). The
  FBO accumulators (decal/lightmap) are still inline — they'll need `Custom`
  (or a dedicated command) when their layers migrate.
- **SHOTS validation is in-game-pending** — render-behavior change; confirm
  tracers/projectiles/contrails render identically and contrails age on pause.
