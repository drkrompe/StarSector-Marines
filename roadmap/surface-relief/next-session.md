# surface-relief — next-session handoff

## State of play (2026-08-19)

- ~~S1 — derivation pipeline~~ **shipped** `cf2e4db1` (see `complete/`).
- **S2 — screen-space parallax: code-complete, NOT yet verified
  in-game.** Full build green (jar produced, tests pass). Gated behind
  `DevConfig.SURFACE_RELIEF_PARALLAX`, **default true** (flipped for
  playtesting; off path is pixel-identical to pre-S2 rendering).
- **Per-texel micro height is now wired:** nature grass/dirt,
  urban-3 street/sidewalk, and fixed-grid terrain draw the exact source rect
  selected by the color pass from the corresponding derived height atlas.
  Brick, crack, and ripple detail therefore reaches the height FBO instead of
  being collapsed to one average per cell. Asset-backed tests cover sliced
  selection, implicit sidewalk/corner selection, fallback behavior, and
  terrain mutation.
- **Live strength tuning is in the battle DEBUG panel:** the `Parallax` dial
  applies immediately over a clamped `0.0000–5.0000` range; the default remains
  `0.0060`. Its cubic response and widened track preserve fine control near zero
  while still exposing the full user-requested experimental ceiling.
- **Headless pixel oracle added:** `GroundParallaxPixelComparisonTest` builds a
  1024×576 battle-like scene from the real color/derived-height sheets, mirrors
  both shader formulas plus bilinear sampling, and writes
  `build/surface-relief/parallax-pixel-comparison.png`. Current measurements:
  default `0.0060` = **0.840 px max displacement / 0.990 mean RGB-channel
  delta**; dial max `5.0000` = **700.171 px / 32.164** (an intentionally
  extreme ceiling, not a recommended target). Default remains difficult to
  perceive despite 45.81% of pixels changing numerically.
- Commit chain (developed on `worktree-surface-relief`, MERGED to main
  2026-08-13): `cf2e4db1` S1 → `80aac9e2` S2 → `9bd7491f` flag on →
  `6e36fe6d` critique fixes.

## What S2 landed

- `render2d.ShaderProgram` — GLSL 1.20 compile/link helper (GL20 core,
  same path as the planet drawables), fail-soft broken-flag idiom.
- `ops.battleview.GroundParallaxPipeline` — color+height FBO pair
  (GL30 core, mirrors `DecalAccumulator.withFboBound` incl. the
  sample-once FBO-binding rule), ortho set to the SAME UI-space viewport
  rect the GROUND quads already emit into (zero coordinate translation),
  fullscreen offset-limited composite. Every failure path falls back to
  draining GROUND straight to the backbuffer.
- `GroundHeightPass` composes macro height with per-texel derived height in a
  batched GLSL pass. `GroundMicroHeightSampler` resolves and caches the exact
  color-pass atlas rectangle; unsupported/missing derived sheets stay
  macro-only.
- Macro heights: `GenMappingRegistry.macroHeight` code defaults +
  `"macroHeight"` override block in `urban.mapping.json`
  (WALL 0.90 / INDOOR 0.65 / RUBBLE 0.30 / WATER 0.15, else 0.50).
- `MAX_FBO_DIM` guard: the vanilla-combat-bridge backdrop renders GROUND
  through a WORLD-UNIT camera; absurd FBO sizes degrade to fallback
  instead of allocating.

## Next up (in order)

1. **In-game smoke test** — load a battle: do both shaders compile live,
   does per-texel relief read, does dragging the DEBUG-panel `Parallax` dial
   update the next frame, and is the flag-off path pixel-identical? GL runtime
   behavior is structurally mirrored from shipped precedents but UNVERIFIED live.
   Run `gradlew :test --tests "*GroundParallaxPixelComparisonTest*"` for the
   headless reference image + pixel metrics when changing the shader.
2. **Tune** strength live with the dial, then promote the preferred value to
   `DEFAULT_STRENGTH`. Tune `EYE_HEIGHT` / `HEIGHT_SCALE` / `HEIGHT_BIAS` in
   `GroundParallaxPipeline` only if the projection itself needs adjustment.
   Water is the first target per overview.
3. Then S3 (bump lighting), S4 (unit relief) per their story docs.

## Known edges

- Letterboxed-viewport band now clears to opaque black behind the
  composite (rare zoom-out-past-map case) — pre-existing edge, noted not
  fixed.
- Critique pass ran post-commit; fixes landed for: stale micro-height
  cache across battles (invalidate on dispose), composite texture binds
  now inside the attrib bracket, fake-eye math aspect-corrected,
  micro sample rect matches the color pass's source inset. Verified
  non-issues: the vanilla-combat-bridge backdrop has its OWN
  `BattleRenderer`/pipeline instance (its `MAX_FBO_DIM` trip can't
  disable the battle screen's parallax); drain fallbacks can't lose or
  double-draw GROUND (draw list is read non-destructively). Accepted:
  the raw fallback `drainColor.run()` can rethrow a draw-list exception
  — same crash the flag-off path would produce, kept loud on purpose.
- Per-texel micro-height uses the color loader's already-validated frame tables;
  missing/corrupt derived data stays macro-only instead of sampling unrelated
  fixed-grid art. Cache entries fingerprint nearby terrain so wall demolition
  re-resolves implicit sidewalk and autotile source rectangles.
