# A 560x336 frame has a budget, and it is measured before it is spent

Status: PLANNED — owner direction on 2026-09-02 after playing the 560x336
Conquest: "the massive maps have big problems with our renderer."

Written: 2026-09-02

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

**Fewer quads for the same ground.** Along a visible row, cells that draw the
same sheet tile at the same tint merge into one run drawn as one quad. A run
cut from an atlas cannot use the driver's repeat wrap, so a merged run either
draws through the ground composite shader with a per-run wrap of its own
sub-rectangle, or is limited to tiles that own a whole texture. The story
does not choose between that and the parked baked-tile layer: it measures the
per-cell path first and then spends on whichever the profile names. If the
ceiling is collection rather than drain, no amount of merging helps and the
answer is the resident tiles; if it is draw calls, runs are the cheap win.

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
- Whichever ground lever the profile names is implemented behind a `battle.*`
  toggle, measured on and off by the same task, and ships on only if the
  whole-map frame is faster with the close framing unchanged.
- `createSnapshots` suites unchanged at their authored zooms.

## Plan

1. `renderEvidence` on `HeadlessGl`: the frame harness, the per-layer counters
   at the drain, the three framings, the report.
2. Zoom gates in the shadow, effect and decal collectors, thresholds stated
   from the measurement.
3. The ground lever the profile names — merged runs, or reviving
   `dense-render-tiles.md` — behind a toggle, measured both ways.
