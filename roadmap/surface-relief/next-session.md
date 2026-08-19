# surface-relief — next-session handoff

## State of play (2026-08-19)

- **S1 — derivation pipeline:** shipped in `cf2e4db1`.
- **S2 — screen-space parallax:** shipped and manually accepted through
  `35d1998d`. Keep the accepted defaults (`0.0060` structure, `0.0060`
  surface, `0.0800` water waves) and retain all three battle DEBUG dials.
- **S3 — dynamic bump lighting:** code-complete in `c92d5b9a`; automated
  checks pass, but the expanded shader and effect still need an in-game smoke
  test before the story moves to `complete/`.

S3 adds a normal FBO beside the color and material-height targets. Its pass
uses the same resolved atlas rectangles as the color/height passes, samples
the generated normal sheets, and writes a flat normal when art is unsupported
or missing. The composite applies parallax first, then samples color and
normal at the resulting coordinate.

Battle events now drive a bounded ground-light model:

- weapon-colored muzzle flashes plus rifle, kinetic, explosive, heavy-impact,
  and fire lights;
- co-located repeated events merge, off-camera lights are culled, and the
  nearest eight visible lights reach the fixed-size shader arrays;
- lifetimes use scaled battle time, so pause and battle-speed controls remain
  authoritative;
- lighting is additive, making an empty light list exactly preserve the
  accepted S2 image;
- the battle DEBUG panel keeps a live `Bump lighting` dial from `0.0–2.0`,
  default `1.0`. A value of zero disables the added light contribution.

## Verification completed

- Full Gradle build and focused surface-relief tests pass.
- The headless bump-lighting oracle uses the real floor color/normal atlases,
  asserts exact no-light identity, and reports 66.27% changed pixels with a
  mean RGB delta of 6.596 at its representative light setting. Real normals
  differ materially from flat normals on 39.87% of pixels.
- The generated GLSL 1.20 fragment shader passes `glslangValidator`.
- Oracle output is written to
  `build/surface-relief/bump-lighting-comparison.png`.

## Next up (in order)

1. Run an in-game S3 smoke test: confirm the expanded shader compiles, muzzle
   flashes/impacts/fire illuminate the ground, normal-map direction reads
   correctly, and `Bump lighting = 0` visually matches the accepted S2 image.
2. Check event timing while paused and at 1×, 2×, and 4× battle speed. Tune
   strength, radii, lifetimes, or source colors only if the live result needs
   it, then move S3 from `stories/` to `complete/`.
3. Begin S4 unit relief only after S3's live acceptance.

## Known edges

- Lighting currently affects the composed ground only; units remain an S4
  concern.
- Structure sheets skipped by S1 receive flat normals, so they do not yet show
  authored per-texel bump detail.
- Lights do not cast shadows and walls do not occlude them.
- The vanilla-combat bridge's world-unit camera still trips the pre-existing
  `MAX_FBO_DIM` guard, which disables the relief FBO path for that backdrop.
