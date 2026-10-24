Status: IN PROGRESS — stages A to C and whole sprites landed (law 27); D open
Written: 2026-10-24
Updated: 2026-10-24 — normal and additive quads share a run, colour within three levels on `PagedQuadRunGlEvidence`

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
colour, which is what the fixed-function path computes, premultiplied since
stage C. Acceptance is colour within three levels of that path: the shader
rounds colour times alpha once where the blend unit multiplied, and stacked
translucent quads carry that rounding up.

**It fails soft.** No program (compile or link refused, fewer than two units)
leaves the drain on the fixed-function path, one run per sheet. The A/B switch
that held it off was retired once stages A to C were read against it.

## Stages

- **A. Paged runs. Landed.** Consecutive normal-blend sheet quads and atlas meshes share
  one draw across up to eight sheets. Accept on `UnitAtlasGlEvidence` (194
  differing pixels, must not rise), a new GL evidence that draws an interleaved
  multi-sheet frame both ways and compares, and `renderEvidence` draws and binds
  per layer against the switch-off control.
- **B. Fills in the run. Landed for rects and polys.** A solid rect becomes a quad sampling one opaque white
  texel on a reserved page, so the flame and ember interleave is one run. Lines,
  polys and ribbons follow if their geometry fits a quad stream.
- **C. Blends in the run. Landed.** Every paged run draws
  premultiplied with `ONE, ONE_MINUS_SRC_ALPHA` for colour and the normal
  blend's factors for alpha, so an additive quad writes alpha zero and normal
  quads write the destination alpha they always did. Who reads destination
  alpha: nothing blends by `DST_ALPHA`, but the ground composite blends its
  relief target by that target's alpha, which is why normal alpha stays exact.
  An additive quad leaves the alpha under it unchanged where the fixed-function
  path added its own squared; on an opaque target that is the same value. It
  premultiplies every run, not only one holding an additive quad: switching per
  run made a quad's rounding depend on what shared its run, and
  `renderEvidence`'s culled-against-unculled check caught a one-level
  difference where culling had changed a run's company.
- **Whole sprites in the run. Landed.** A `SPRITE`, and each image of a mesh
  with no atlas, is a quad of its own texture on a page, so a shot no longer
  closes the run. It matches `Sprite.renderAtCenter` as decompiled: the
  sprite's `texX..texX + texWidth` by `texY..texY + texHeight` upright, turned
  about its middle, colour sent as bytes with the alpha truncated from
  `255 * alphaMult`. A sprite's own centre or offset is not followed; no caller
  of the drain sets one. On the stress scene `SHOTS` went from 36 draws to 2 at
  every framing, frames within one level of the control.
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
