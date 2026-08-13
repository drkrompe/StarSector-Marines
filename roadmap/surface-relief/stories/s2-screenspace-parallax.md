# S2 — Screen-space ground parallax

## Goal

The composed ground layer gets offset-limited parallax from a fake
perspective eye. Walls/buildings lean and slide on pan/zoom; water and
craters sink. Everything above the ground (units, shots, FX, HUD) is
untouched.

## Scope

1. **Shader infra** (new, minimal): a `render2d.ShaderProgram` helper —
   compile/link GLSL 1.20 via LWJGL 2 `GL20`, uniform setters, hard-fail
   surfaced as log + disabled effect (never crash the battle). Bracketed
   with existing `GlStateBracket` discipline.
2. **Ground FBO pair.** The ground/terrain painter passes render into an
   FBO **color** target instead of the backbuffer (viewport-sized,
   recreated on resize). A second **height** target (same size; plain
   RGB8 grayscale is fine) renders the same tile quads sampling the S1
   height sheets, tinted by per-tile **macro height**.
3. **Macro height metadata.** Per-tile-id scalar in the gen-mapping /
   `TileRegistry` layer (walls high, buildings raised, ground mid, craters
   low, water lowest), with a sane code default so unmapped tiles are
   mid-height. Follow the Phase-2 data-driven pattern
   (`urban.mapping.json`).
4. **Fullscreen parallax pass.** One quad, one fragment shader:
   - `hsb = height * scale + bias` (paper's scale/bias, uniforms)
   - `eye = normalize(vec3(screenCenter - fragPos, eyeHeight))`
   - `uv' = uv + hsb * eye.xy * strength` — **offset-limited form**
     (no divide by `eye.z`)
   - sample composed color at `uv'`.
   Then the rest of the frame draws on top as today.
5. **Tuning + toggle.** Uniform-driven strength/eyeHeight; a dev/settings
   toggle that bypasses the whole indirection (ground passes go straight
   to backbuffer as today). Off by default until playtested? — ship ON in
   dev, decide at playtest.

## Ordering constraints

- Ground passes only into the FBO. Decal accumulators and anything the
  render-target seam doc excludes stay out (`roadmap/battle-render/`,
  "Battle render-target seam").
- Fog-of-war, unit, shot, HUD passes remain post-parallax.

## Risks

- Steep wall edges: if artifacts are ugly, blur the height target a texel
  or two before the parallax pass (cheap separable blur or just sample
  bilinearly) rather than complicating the height authoring.
- Subpixel softening of the ground layer — acceptable as depth cue; the
  toggle is the escape hatch.

## Acceptance

- Visually: panning the camera slides walls against ground; zoom changes
  the lean; water reads as below-surface. No shimmer at screen edges
  (offset limiting), no atlas bleed anywhere.
- Toggle off ⇒ pixel-identical to pre-story rendering.
- Shader-compile failure path exercised (bad source in dev) ⇒ battle
  renders fine with effect off, one log line.
- No GL state leakage: UI and vanilla rendering unaffected after battle.
