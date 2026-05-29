# Battle render reorg — next session

## State of play

Concept doc written ([`overview.md`](overview.md)). **Stories A–D shipped.**
- A — `BattleSprites` asset registry ([`complete/story-a-extract-assets.md`](complete/story-a-extract-assets.md)).
- B — `BattleRenderer` + `RenderContext`; pipeline severed from the loop
  ([`complete/story-b-battlerenderer.md`](complete/story-b-battlerenderer.md)).
- C — `RenderLayer` + `DrawList` + `DrawCommand` + `drainLayer`; the `SHOTS` pass
  converted ([`complete/story-c-drawlist-model.md`](complete/story-c-drawlist-model.md)).
- D — first sheet-batched pass + `RenderSystem`: `DOODADS` migrated to
  `DoodadRenderSystem` emitting `SheetQuad`s; drain gained the per-sheet
  `QuadBatch` grouping path ([`complete/story-d-doodads-system.md`](complete/story-d-doodads-system.md)).

Command vocabulary now: `SheetQuad` (batched sheet sub-rect),
`Sprite` (whole-texture `renderAtCenter`), `Custom` (own-GL escape hatch). Two
passes (`SHOTS`, `DOODADS`) route through the draw list; ~15 still draw inline.
`renderWorld` hand-wires `doodadSystem.collect` + `drainLayer`.

## Next-up

**Story E — migrate another pass into a `RenderSystem`.** Good candidates, in
rough increasing order of size/risk:
- **VEHICLES / CONVOY / SHUTTLES / DRONES** — single rotated sprites, so they map
  to the existing `Sprite` command (no new command type). `renderVehicles` is a
  clean, medium pass; converting it is mostly mechanical and proves a second
  `Sprite`-emitting system.
- **GROUND** (`renderTiledFloorsAndWalls`) — biggest payoff (it's the heaviest
  `QuadBatch` user and the pass `drainLayer` most directly generalizes), but
  large and multi-sub-case (autotiles, crosswalks→`SolidRect`, walls, nature
  overlays). Will likely force the first **`SolidRect`** command + the rotated
  `SheetQuad` form.
- **UNITS** — sprites + HP bars (`SolidRect`) + sub-passes (turrets/drones/dead).

When the third or fourth system lands, introduce the **`List<RenderSystem>`
registry** + start collapsing `renderWorld` toward the systems-loop + drain
endgame (the overview's "Final").

Carry-over follow-ups (opportunistic): dedupe `MARINE_TRACER`/`DEFENDER_TRACER`/
`bearingDeg()`; restore fuller inter-pass comments; drop pre-existing unused
imports (`ShuttleType`, `LightKernel`).

## Slice chain

A → B → ~~C (prove model on SHOTS)~~ ✅ → ~~D (first sheet pass + RenderSystem,
DOODADS)~~ ✅ → E…N (one pass per slice into `RenderSystem`s) →
Final (collapse `render()` to systems-loop + drain).

## Watch-outs

- Pass order in `renderWorld` is semantic — `RenderLayer` is the verbatim list.
  Emit a migrated pass into its existing layer; don't re-derive order.
- Keep every batched flush inside a `GlStateBracket`. The drain owns this for
  `SheetQuad`/`Sprite` runs; `Custom` callbacks own their own.
- **`drainLayer` flushes touched batches in first-touched order**, not a fixed
  per-layer sheet order — fine for DOODADS (no cross-sheet overlap). A pass that
  needs deterministic inter-sheet layering will need explicit ordering.
- FBO accumulators (decal/lightmap) are still inline — they'll need `Custom`
  (or a dedicated command) when their layers migrate.
- **In-game-pending validation** for both SHOTS (C) and DOODADS (D) — render
  changes; confirm in a real battle.
