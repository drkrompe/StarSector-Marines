# Battle render reorg — next session

## State of play

Concept doc written ([`overview.md`](overview.md)). No code yet. This is the
render-side sibling of `battle-reorg/`.

Decision recorded: **no ECS dependency** (artemis-odb rejected — reflection vs
the Starsector sandbox is the hard blocker; render is also the wrong first ECS
user). Build the pipeline framework-free; borrow ECS *concepts* via hand-rolled
views. See "Considered alternatives" in `overview.md`.

## Next-up

**Story A — Extract assets.** Move the `ensureXSheet()` family + sprite caches
out of `BattleScreen` into a `BattleSprites` registry. Mechanical, zero behavior
change, ~900 lines. Candidate for Sonnet delegation. Verify: build + in-game
battle renders identically.

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
