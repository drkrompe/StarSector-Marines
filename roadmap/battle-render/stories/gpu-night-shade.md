Status: IN PROGRESS — the field landed behind `battle.render.gpuNightShade`; retiring the control is open
Written: 2026-10-24

# GPU night shade

## Why

With every quad layer down to a handful of draws (`programmable-quad-batch.md`),
`NIGHT_LIGHTING` was the frame's ceiling on the fixed-world stress scene: half
of our own CPU time at the close and mid framings, every run. The shade is a
function of light pools sampled bilinearly at half-cell resolution, and the CPU
path evaluated it at a grid of points in every lit cell (up to eight by eight)
and appended a tinted quad per point. A burning lane is about a hundred
thousand quads a frame, built and then drawn.

## Shape

The pools become textures and the function a fragment shader
(`NightShadeField`): the lamps' field over the whole map, uploaded once per
bake; fire's pool over the visible window, uploaded each frame; and a cell mask
over the window for fog and empty ground. One quad over the visible cells draws
it, directly over the scene or premultiplied into the coverage composite's
targets, in the place the meshes went. The fixtures and halos stay ordinary
draw-list commands.

**One function.** `NightLightingRenderSystem.shade` is the shade, the CPU path
calls it, and `NightShadeFieldGlEvidence` holds the shader to it within three
levels per channel at every pixel, with fire, a fog-hidden cell and an empty
cell in the frame.

**Hosts.** Only a host that can run a custom pass owning its own GL
(`BattleRenderHostProfile.residentGroundAllowed`, the resident ground's
election). The Java2D review drain keeps the quads. A field that fails logs
once and the next frame builds quads.

## Readings

`renderEvidence -PrenderMaps=stress at night` (the stress scene on a dark night
lit only by fire): `NIGHT_LIGHTING` collect and drain 2.79 + 2.69 ms at mid
against 0.33 + 0.51 under the field, directionally, on a shared machine. The
frames differ by at most 9 levels at mid (mean 0.19); zoomed out the field is
smooth where the quads stepped once per cell.

## Open

- Retire `battle.render.gpuNightShade` and the quad path for GL hosts once the
  owner has read the stills.
