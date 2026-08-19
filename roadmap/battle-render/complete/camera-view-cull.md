# Camera view culling — range-loop the dense passes

> **Status: ✅ SHIPPED.** Headless tests pin the camera AABB and the GROUND
> fallback collect. In-game smoke: zoom in, pan, confirm tiles/roofs/doodads/fog
> still meet the viewport edge with no pop.

## What landed

Battle rendering no longer walks the whole grid for the expensive static
passes. `BattleCamera.visibleCells(margin, gridW, gridH)` maps the current
viewport to an inclusive cell AABB (`VisibleCellRect`). Dense emitters iterate
that rect; sparse AABB-bearing lists reject against it.

| Pass | How it culls |
|------|----------------|
| `GroundRenderSystem` floors/walls | range-loop `GEOMETRY_MARGIN_CELLS` (2) |
| `GroundHeightPass` / `GroundNormalPass` | same range-loop (parallax edge samples) |
| Fog overlay | same helper, original `FOG_MARGIN_CELLS` (8) halo |
| Roofs | building bbox vs view, then per-cell `contains` |
| Doodads | footprint AABB vs view |
| Zone-overlay debug | range-loop |

Units, shots, vehicles, shuttles stay linear — N is hundreds, not tens of
thousands. Ground bump lights already had their own camera-rect filter.

The full-grid backing fill is unchanged (one quad; the scissor bracket clips
it). Autotile neighbor *reads* still sample cells just outside the emit rect.

## Why not spatial buckets

The ground grid already *is* a dense spatial index. A second bucket layer
would still walk every cell inside hit buckets and over-emit a ring at bucket
edges. The camera is 2D ortho, so the frustum *is* this AABB. Fog already had
the math inlined; this slice made it the shared query.

## What this is not

- **Not a no-op at play zoom.** Default attach is still `zoom = 1.0` (whole
  map fits), but typical fight camera is zoomed in. Area on screen falls with
  `1/zoom²`.
- **Not the combat-bridge spectator cull.** `GroundSceneBackdrop` builds a
  `BattleCamera` whose viewport *is the entire grid* in world units, so this
  helper is a no-op there. Bridge culling would read the vanilla spectator
  viewport — a separate consumer of the same `VisibleCellRect` idea.
- **Not the ground-FBO cache.** That spike
  ([`../stories/perf-ground-fbo-cache.md`](../stories/perf-ground-fbo-cache.md))
  still helps the zoomed-out overview (1 quad instead of the visible slice).
  The two stack.

## Tests

- `BattleCameraTest` — whole-map at zoom 1, slice around pan at `MAX_ZOOM`,
  margin expand+clamp, empty viewport.
- `VisibleCellRectTest` — inclusive contains, building AABB, doodad footprint.
- `GroundRenderSystemViewCullTest` — no-sheet fallback collect count equals
  `1 + view.width * view.height` when zoomed in, full grid at zoom 1.
