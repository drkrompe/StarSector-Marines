# A 560x336 frame has a budget, and it is measured before it is spent

Status: IN PROGRESS — the profile and the zoom gates have shipped; the resident
ground mesh is the lever the profile named.

Written: 2026-09-02

Updated: 2026-09-02 — `renderEvidence` ran and the ground drain is the ceiling,
so the ground lever is a resident mesh behind `battle.render.groundMesh`;
greedy runs and baked ground tiles are recorded here as the rejected
alternatives.

## What the owner saw

The renderer was built for a 280x168 map and measured there. At 560x336 — four
times the cells — a whole-map frame stutters. Nothing here is a mystery in
principle: dense terrain still emits one command per visible cell, every
shadow, decal and effect draws at every zoom, and the sun-shadow and unit-shadow
layers are per body. Which of those is the ceiling is exactly the thing nobody
has measured, and `dense-render-tiles.md` was parked on the condition that
"profiling or map growth makes the current per-cell path a measured ceiling."
Map growth has arrived; the profile has not.

## The model

**Measure first, on the real pipeline.** A `renderEvidence` task, opt-in like
`shaderEvidence` and on the same `HeadlessGl` context: generate the canonical
560x336 Conquest map, play a few hundred ticks so units, decals and shadows
exist, then render frames through the real `BattleRenderer` — collect and
drain — at three framings: close (a compound), mid (a lane), and whole-map.
Report per layer and per framing: commands collected, quads drained, draw
calls and texture binds, collect milliseconds and drain milliseconds; the
same at 280x168 as the control. The report is the instrument every lever
below is judged by, and it goes under `build/reports/render/`. What it cannot
see is the game's own frame around ours, so it measures our share and says so.

**Detail follows zoom.** A framing wider than a stated threshold stops drawing
what cannot be read at that scale: unit and prop shadows, the sun-shadow
bodies, smoke and one-tick effects, and the decal blit at full resolution.
Thresholds are camera zoom, stated as constants with the measurement that set
them; the gates live in the collectors, so a layer simply collects nothing
rather than draining to nothing. Painter order and world ratios are untouched
(laws 6 and 9 in `battle-render-nouns.md`).

**The ground is a mesh, not a stream of quads.** The profile named the lever:
at whole-map on 560x336 the ground layer is 266 of the 276 ms our side of the
frame costs, and 259 of that is the *drain* — 199,345 sheet quads leaving as
49,514 draw calls and 49,461 texture binds, four quads a draw, because the
sheet changes from one cell to the next and the batcher flushes every time.
Collection is 7 ms. It is a submission ceiling.

So the static ground is baked into vertex buffers once per battle: one buffer
per ground sub-layer and sheet in painter order, every cell owning a fixed
four-vertex slot carrying position, atlas UV and whatever per-quad inputs the
composite reads today, each buffer drawn with one call per frame. A cell that
changes — a breach, rubble, a caved roof — is an in-place `glBufferSubData`
of that cell's slot and its autotile neighbours' slots, so there is no
re-meshing and no wrap problem: every cell keeps its own atlas UVs. The
composite shader sees the same UVs it does now. Chunk the buffers (32x32
cells, say) only if the profile shows partial upload or culling matters.
LWJGL 2 has two traps here: `gl*Pointer(FloatBuffer)` throws while a VBO is
bound, so the offset overloads are the ones to use, and a per-frame `glGet*`
stalls an async-renderer bridge.

Two alternatives were considered and rejected. **Greedy runs** along a visible
row re-split on every edit and need a repeat wrap an atlas cannot give, so a
merged run would have to go back through the composite with a per-run wrap of
its own sub-rectangle. **Baked ground tiles** — `dense-render-tiles.md`, the
parked story — cost fill and VRAM per view and need residency, eviction and
anti-thrash policy. A mesh is one upload and zero per-frame CPU for the
ground.

## What it does not do

- It does not change what a frame looks like at a readable zoom; every
  reduction is at a framing where the detail could not be seen.
- It does not touch the simulation's tick cost; the sim runs ~30 ticks/s on
  this map headless, and that budget is the replay harness's.

## Acceptance

- `renderEvidence` reports the table above for 280x168 and 560x336 at three
  framings, deterministically, and says which layer carries the most time at
  whole-map.
- With the zoom gates on, the whole-map frame's drain time falls by a stated
  share against the same run's control, and a close framing is byte-identical
  to the ungated render in the snapshot suites.
- The resident ground mesh is implemented behind `battle.render.groundMesh`,
  measured on and off by the same task, and ships on only if the whole-map
  frame is faster with the close framing unchanged.
- `createSnapshots` suites unchanged at their authored zooms.

## Plan

1. `renderEvidence` on `HeadlessGl`: the frame harness, the per-layer counters
   at the drain, the three framings, the report.
2. Zoom gates in the shadow, effect and decal collectors, thresholds stated
   from the measurement.
3. The resident ground mesh behind `battle.render.groundMesh`, measured both
   ways by the same task.
