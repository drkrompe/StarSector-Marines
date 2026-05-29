# Battle render reorg — next session

## State of play

Concept doc written ([`overview.md`](overview.md)). **Stories A + B shipped.**
`BattleScreen` 4364 → **1251 lines** across the two slices:
- A — `BattleSprites` asset registry ([`complete/story-a-extract-assets.md`](complete/story-a-extract-assets.md)).
- B — `BattleRenderer` + `RenderContext`; world-render pipeline severed from the
  loop ([`complete/story-b-battlerenderer.md`](complete/story-b-battlerenderer.md)).

`BattleScreen` is now ~the loop/input/audio + scissor bracket + chrome.
`render()` = scissor → `renderer.renderWorld(rc)` → chrome.

Decision recorded: **no ECS dependency** (artemis-odb rejected — reflection vs
the Starsector sandbox is the hard blocker; render is also the wrong first ECS
user). Build the pipeline framework-free; borrow ECS *concepts* via hand-rolled
views. See "Considered alternatives" in `overview.md`.

## Next-up

**Story C — Prove the DrawList model on one layer.** Introduce `RenderLayer`
(the ordered enum that *is* today's pass list) + `DrawList` + the drain in
`BattleRenderer`. Convert ONE pass (`SHOTS` or `UNITS`) to emit `DrawCommand`s
instead of drawing inline, and validate the command/batch/flush path end-to-end
on that single layer — **including the `Custom` escape hatch** on at least one
accumulator pass (FBO/lightmap/contrail). See `overview.md` §Stories C.

Carry-over follow-ups from Story B (do opportunistically, not blockers): dedupe
`MARINE_TRACER`/`DEFENDER_TRACER`/`bearingDeg()` duplication; restore the fuller
inter-pass comments in `renderWorld`; drop unused imports in `BattleScreen`.

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
