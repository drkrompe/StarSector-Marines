# S3 — Dynamic bump lighting

> **IMPLEMENTED** `c92d5b9a` (2026-08-19); pending in-game shader/effect
> acceptance before moving to `complete/`.

Light the composed ground from battle events — muzzle flashes, explosions,
fires — using the S1 normal sheets. Per the Welsh paper, composes with S2:
compute the parallax-offset coordinate first, then sample the normal map at
the offset coordinate, then N·L against each light.

## Landed scope

- A third viewport-sized **normal target** composes the exact S1 atlas texels
  selected for the accepted height path. Unsupported/missing sheets use a flat
  encoded normal.
- `GroundLightService` owns short-lived cell-space lights, merges repeated
  co-located events, culls outside the camera, and uploads at most the eight
  nearest visible lights.
- Sources are existing presentation events: weapon-colored muzzle flashes,
  rifle/kinetic/HE impacts, heavy support impacts, and burning-wreck fire.
- The S2 composite samples the normal at the final parallax/water coordinate,
  decodes it into world +Y-up space, and applies squared radial falloff plus
  Lambert `N·L`. Lighting is additive over the accepted S2 image.
- No live lights is pixel-identical to S2. A `Bump lighting` DEBUG dial exposes
  `0–2` strength, default `1`.
- `GroundBumpLightingPixelComparisonTest` mirrors the shader against real
  albedo/normal assets and writes `build/surface-relief/bump-lighting-comparison.png`.
  Current metrics: 66.27% changed, 6.596 mean RGB delta, and 39.87% of pixels
  materially differ from a flat-normal render.

## Acceptance remaining

- In battle, confirm the expanded GLSL shader compiles and normal orientation
  reads correctly under moving muzzle/impact lights.
- Confirm `Bump lighting = 0` matches the accepted S2 presentation and that
  pause plus 1×/2×/4× timing remains readable.
- Decide whether default strength, radii, and lifetimes need tuning before
  moving this story to `complete/`.

## Deliberate limits

- Ground only; units remain unlit until S4.
- No shadow map or wall occlusion, so lights are radial rather than visibility-aware.
- S1 deliberately skipped structure sheets; those cells receive a flat normal
  but still respond to top-facing point light.
