Status: IN PROGRESS — stages A and B landed (law 27); C and D open
Written: 2026-10-24
Updated: 2026-10-24 — paged runs and fills in the run landed, pixel-equal on `PagedQuadRunGlEvidence`

# Programmable quad batch

## Why

Every textured quad the battle draws goes through `QuadBatch`, which is the
fixed-function pipeline: one bound texture, client arrays, one
`glDrawArrays`. So a run can only ever see one texture and one blend, and the
drain (`DrawListRenderer`) flushes on every change of sheet, of blend, and on
every solid fill, line or ribbon between two quads (law 6). The atlases
(`battle-textures.md`) fight that by moving art into fewer textures. A program
stops that fight from needing to be won: a run can address several textures,
and later several blends and effects.

`renderEvidence` (2026-10-23) shows where it bites today. `UNITS` is one to nine
draws. The most expensive layer on The Burn is `HAZARDS`: 5,735 commands, 1,323
draws, 678 binds and 4.5 ms of drain at the mid framing. That cost comes from
fire flames interleaved with solid ember rects, each hot cell breaking the run
twice, and not from texture count. The same shape, a quad and a fill alternating,
shows up wherever a layer mixes the two.

## Shape

**GL 2.1 and GLSL 1.20, no extensions.** Every shader in the mod is
`#version 120`, the floor the Mac's legacy context gives Starsector. A texture
array would need GL 3.0 or `EXT_texture_array`, which nobody here has verified
on that context. **Pages** need neither: a run binds up to eight ordinary 2D
textures to eight units, each vertex carries its page number, and the fragment
shader picks the sampler with a branch. A branch on a value that is constant
across a quad is coherent, and no texture here is mipmapped, so sampling under
the branch is well defined. Pages may differ in size, so any sheet, atlas or
whole image can be one, with no repacking.

**Painter order is untouched.** A paged run is the same quads in the same
order; it only stops flushing when the texture changes.

**Same pixels.** The fragment shader is `GL_MODULATE`: texel times vertex
colour, which is what the fixed-function path computes. Acceptance is pixel
parity against that path.

**It fails soft.** No program (compile or link refused, fewer than two units)
leaves the drain on today's fixed-function path, unchanged. A control switch
`battle.render.pagedBatch` holds it off for the A/B.

## Stages

- **A. Paged runs. Landed.** Consecutive normal-blend sheet quads and atlas meshes share
  one draw across up to eight sheets. Accept on `UnitAtlasGlEvidence` (194
  differing pixels, must not rise), a new GL evidence that draws an interleaved
  multi-sheet frame both ways and compares, and `renderEvidence` draws and binds
  per layer against the switch-off control.
- **B. Fills in the run. Landed for rects and polys.** A solid rect becomes a quad sampling one opaque white
  texel on a reserved page, so the flame and ember interleave is one run. Lines,
  polys and ribbons follow if their geometry fits a quad stream.
- **C. Blends in the run.** Premultiplied output with `ONE, ONE_MINUS_SRC_ALPHA`
  makes an additive quad a quad whose alpha is zero, so normal and additive
  quads share a draw. The destination alpha this writes differs from the
  `SRC_ALPHA` path's, so it needs an answer on who reads destination alpha (the
  evidence compares all four channels) before it lands.
- **D. Effects.** Per-quad parameters as vertex attributes: hit flash, dissolve,
  outline, faction mask recolour, lit units. Each is its own story once A to C
  have settled the stream.

## Measuring

Read counts (draws, binds) and pixel parity; timings on this shared machine
are only directional, and a paged `renderEvidence` run once read several times
slower than its control in *collect*, which touches no GL, because other
sessions were busy. Measure render levers on the fixed-world stress scene
rather than on played battles. Nsight Graphics' shader profiler is the tool for
the program's own register count and occupancy, which matter once stage D puts
effects into it.
