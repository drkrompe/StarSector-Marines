# surface-relief — next-session handoff

## State of play (2026-08-13)

- ~~S1 — derivation pipeline~~ **shipped** `cf2e4db1` (see `complete/`).
- **S2 — screen-space parallax: code-complete at `80aac9e2`, NOT yet
  verified in-game.** Full build green (jar produced, tests pass). The
  effect is gated behind `DevConfig.SURFACE_RELIEF_PARALLAX`
  (**default false** — off path is pixel-identical to pre-S2 rendering).
- Commit chain (worktree `worktree-surface-relief`, branched off main at
  `98462906` which added these docs): `cf2e4db1` S1 → `80aac9e2` S2.

## What S2 landed

- `render2d.ShaderProgram` — GLSL 1.20 compile/link helper (GL20 core,
  same path as the planet drawables), fail-soft broken-flag idiom.
- `ops.battleview.GroundParallaxPipeline` — color+height FBO pair
  (GL30 core, mirrors `DecalAccumulator.withFboBound` incl. the
  sample-once FBO-binding rule), ortho set to the SAME UI-space viewport
  rect the GROUND quads already emit into (zero coordinate translation),
  fullscreen offset-limited composite. Every failure path falls back to
  draining GROUND straight to the backbuffer.
- `GroundHeightPass` (macro per-cell quads) + `HeightSource` seam +
  `GroundMicroHeightSampler`/`HeightSheetTexture` (per-cell micro height
  from S1 sheets, cached per battle).
- Macro heights: `GenMappingRegistry.macroHeight` code defaults +
  `"macroHeight"` override block in `urban.mapping.json`
  (WALL 0.90 / INDOOR 0.65 / RUBBLE 0.30 / WATER 0.15, else 0.50).
- `MAX_FBO_DIM` guard: the vanilla-combat-bridge backdrop renders GROUND
  through a WORLD-UNIT camera; absurd FBO sizes degrade to fallback
  instead of allocating.

## Next up (in order)

1. **In-game smoke test** — flip `DevConfig.SURFACE_RELIEF_PARALLAX`,
   load a battle: does the shader compile live, does parallax read, is
   toggle-off pixel-identical? GL runtime behavior is structurally
   mirrored from shipped precedents but UNVERIFIED live.
2. **Tune** `STRENGTH` / `EYE_HEIGHT` / `HEIGHT_SCALE` / `HEIGHT_BIAS`
   in `GroundParallaxPipeline` — reasoned guesses, not calibrated.
   Water is the first target per overview.
3. Micro-height for the SLICED sheets (nature-tiles, urban-tileset-3 —
   baked by S1 but not wired; see `GroundMicroHeightSampler` javadoc).
   Also GRASS/DIRT/STREET/SIDEWALK special-cases fall back to macro-only.
4. Then S3 (bump lighting), S4 (unit relief) per their story docs.

## Known edges

- Letterboxed-viewport band now clears to opaque black behind the
  composite (rare zoom-out-past-map case) — pre-existing edge, noted not
  fixed.
- The S2 implementation agent died mid-workflow (connection loss); the
  integration agent wrote most of `GroundParallaxPipeline` — reviewed
  main-thread, but weight the critique pass accordingly.
