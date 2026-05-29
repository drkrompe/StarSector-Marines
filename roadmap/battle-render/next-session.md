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

**Engine/game package split shipped** (structural, zero behavior change) — see
[`complete/engine-game-package-split.md`](complete/engine-game-package-split.md).
The render *mechanism* now lives in `render2d` (engine): `BattleCamera`,
`DrawCommand`, and the extracted `DrawListRenderer` drain joined the batch
primitives; `render2d` imports nothing from `ops.battleview` (verified). The
concrete passes, `RenderLayer`, `DrawList`, `RenderContext`, `RenderSystem`, and
`BattleRenderer` stay game-side in `ops.battleview`. `drainLayer` is a one-line
delegator to `DrawListRenderer.drain`. Both packages now carry charters. This is
the clean seam the remaining pass migrations land on.

## Next-up

**Story E — GROUND (`renderTiledFloorsAndWalls`) → `GroundRenderSystem`.** Now the
target (it's the heaviest `QuadBatch` user and the top render-CPU root per the
backlog JFR capture), landing on the fresh engine/game seam. It forces the
deferred command/drain work:
- **Add `DrawCommand.SolidRect`** (engine) for the FLOOR_COLOR backing fill,
  `fillCell` road/courtyard fallbacks, crosswalk stripes, and the wall-fill
  fallback. Drained via `SolidQuadBatch`.
- **Strict painter-order drain** — teach `DrawListRenderer.drain` to flush the
  active batch when the sheet/command-type changes, so submission order *is* paint
  order (no per-layer config in the engine). `GroundRenderSystem` emits in tiers
  (backing solids → base tiles bucketed per sheet → overlays: crosswalk/nature/
  doorway → walls) so each sheet stays contiguous = fully batched (matches today's
  flush count). Convert the DOODADS path to the same model. This is the upgrade
  past the current first-touched drain (see Watch-outs).

Other passes after GROUND, rough increasing size: **VEHICLES / CONVOY / SHUTTLES /
DRONES** (single rotated sprites → existing `Sprite` command), then **UNITS**
(sprites + HP bars via `SolidRect` + turret/drone/dead sub-passes).

When the third or fourth system lands, introduce the **`List<RenderSystem>`
registry** + start collapsing `renderWorld` toward the systems-loop + drain
endgame (the overview's "Final").

**Deferred story — QuadBatch.flush batching revisit (perf spike).** The backlog
JFR capture shows `QuadBatch.flush` is 78% of render CPU but the lever is
unconfirmed (CPU float-packing vs GL submit vs too-many-small-flushes). Profile
the flush body first, then pick: VBO, flush-coalescing, or runtime atlas. Drops
in *below* the command model (engine-only) — game `RenderSystem`s never see it.
The command stream is now the auditable unit. Cross-ref backlog § Performance.

Carry-over follow-ups (opportunistic): dedupe `MARINE_TRACER`/`DEFENDER_TRACER`/
`bearingDeg()`; restore fuller inter-pass comments; drop pre-existing unused
imports (`ShuttleType`, `LightKernel`).

## Slice chain

A → B → ~~C (prove model on SHOTS)~~ ✅ → ~~D (first sheet pass + RenderSystem,
DOODADS)~~ ✅ → ~~engine/game package split (structural foundation)~~ ✅ →
E…N (one pass per slice into `RenderSystem`s; E = GROUND) →
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
