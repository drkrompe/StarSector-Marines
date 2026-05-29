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

**Story E is shipped, verified, and the fallback is deleted.** In-game check
passed (and fixed a latent beach-doodad bug); the old `@Deprecated`
`renderGrid`/`renderTiledFloorsAndWalls`/`draw*Tile`/predicate block + its
orphaned constants were removed from `BattleRenderer` (−426 lines). The live
`renderZoneOverlay`/`renderDecals` (interleaved in that span) were preserved.

Next pass migration is **VEHICLES / CONVOY / SHUTTLES / DRONES** (single rotated
sprites → existing `Sprite` command), then **UNITS** (see below).

**Story E shipped — what landed:**
- `DrawCommand.SolidRect` + the pooled, mutable tagged command buffer
  (`2ee5b89`): `DrawList` recycles per-layer `DrawCommand[]` slots so the dense
  GROUND pass (~38k tiles) allocates nothing steady-state.
- `DrawListRenderer.drain` is strict-painter: submission order = paint order,
  one `GlStateBracket` per layer, flush-on-sheet/kind-change. DOODADS converted
  (emits road-then-urban contiguous).
- `GroundRenderSystem` emits per-tile commands in cell order (base → nature
  overlay → doorway), then a wall pass; backing fill first.

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
~~pooled command buffer + SolidRect + strict-painter drain~~ ✅ →
~~E (GROUND → GroundRenderSystem; verified, fallback deleted)~~ ✅ →
F…N (VEHICLES/CONVOY/SHUTTLES/DRONES, then UNITS) →
Final (collapse `render()` to systems-loop + drain).

## Watch-outs

- Pass order in `renderWorld` is semantic — `RenderLayer` is the verbatim list.
  Emit a migrated pass into its existing layer; don't re-derive order.
- Keep every batched flush inside a `GlStateBracket`. The drain owns this (one
  bracket per layer, spanning all batch/sprite runs); `Custom` callbacks drop out
  of the bracket and own their own GL.
- **The drain is strict-painter now** (submission order = paint order), not
  first-touched. A migrated system must therefore emit in paint order, and emit
  each sheet's quads contiguously to batch them (one flush per contiguous run).
  GROUND relies on spatial coherence (street/grass regions) for long runs.
- FBO accumulators (decal/lightmap) are still inline — they'll need `Custom`
  (or a dedicated command) when their layers migrate.
- **In-game-pending validation**: SHOTS (C), DOODADS (D). GROUND (E) is verified
  (fallback deleted). New render-changing passes still need a live-battle check.
