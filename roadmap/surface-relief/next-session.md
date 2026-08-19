# surface-relief — next-session handoff

## State of play (2026-08-19)

- ~~S1 — derivation pipeline~~ **shipped** `cf2e4db1` (see `complete/`).
- **S2 — screen-space parallax: code-complete, NOT yet verified
  in-game.** Full build green (jar produced, tests pass). Gated behind
  `DevConfig.SURFACE_RELIEF_PARALLAX`, **default true** (flipped for
  playtesting; off path is pixel-identical to pre-S2 rendering).
- **Sliced-sheet micro height is now wired** on `codex/surface-relief`:
  nature grass/dirt and urban-3 street/sidewalk cells sample the exact frame
  selected by the color pass. Fixed-grid fallbacks remain aligned when a
  sliced color sheet fails to load. Asset-backed tests cover both baked sheets,
  implicit sidewalk/corner selection, fallback behavior, and terrain mutation.
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
- `GroundHeightPass` (macro per-cell quads) + `HeightSource` seam +
  `GroundMicroHeightSampler`/`HeightSheetTexture` (per-cell micro height
  from all S1 terrain sheets, cached per battle).
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
- Sliced micro-height uses the color loader's already-validated frame tables;
  missing/corrupt derived data stays macro-only instead of sampling unrelated
  fixed-grid art. Cache entries fingerprint nearby terrain so wall demolition
  re-resolves implicit sidewalk and autotile source rectangles.
