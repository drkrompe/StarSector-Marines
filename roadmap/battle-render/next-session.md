# Battle render reorg — next session

## State of play

Concept doc written ([`overview.md`](overview.md)). **Story A shipped** —
`BattleSprites` registry extracted; `BattleScreen` 4364 → 3441 lines. See
[`complete/story-a-extract-assets.md`](complete/story-a-extract-assets.md).
This is the render-side sibling of `battle-reorg/`.

Decision recorded: **no ECS dependency** (artemis-odb rejected — reflection vs
the Starsector sandbox is the hard blocker; render is also the wrong first ECS
user). Build the pipeline framework-free; borrow ECS *concepts* via hand-rolled
views. See "Considered alternatives" in `overview.md`.

## Next-up

**Story B — Extract `BattleRenderer` + `RenderContext`.** Move the existing
`render*`/`draw*` methods *verbatim* into a renderer class holding
camera/layout/batches/sheet-refs (pulls sheets from `BattleSprites`, owns the
six `QuadBatch`es currently still on the screen + `buildTileBatches()`). Still a
call sequence, but severed from screen/loop/input — `BattleScreen` shrinks to
the loop. Verify: build + in-game battle renders identically.

## Slice chain

A (assets) → B (`BattleRenderer`/`RenderContext` extraction, verbatim) →
C (prove `RenderLayer`/`DrawList`/drain on one layer) → D…N (one pass per slice
into `RenderSystem`s) → Final (collapse `render()`).

## Watch-outs

- Pass order in `BattleScreen.render()` is semantic — lift it verbatim into
  `RenderLayer`, don't re-derive it.
- Keep every batched flush inside a `GlStateBracket` (polluted GL state).
- `Custom` command escape hatch must cover the FBO/lightmap/contrail passes —
  pressure-test it in Story C before fanning out D…N.
