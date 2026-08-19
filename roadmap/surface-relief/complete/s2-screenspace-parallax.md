# S2 — Screen-space ground parallax

> **SHIPPED and playtest-accepted** through `35d1998d` (2026-08-19).
> The final form goes beyond the original scalar-height sketch: RGBA preserves
> macro height, micro height, water identity, and shore proximity; structure,
> surface, and water strengths remain independently tunable in the battle DEBUG
> panel. The accepted defaults remain `0.006`, `0.006`, and `0.08` cells.

## What landed

- Viewport-sized color + material-height FBOs and a GLSL 1.20 fullscreen
  offset-limited parallax composite.
- Exact color-pass atlas rectangles reused against S1 height sheets, including
  sliced nature/urban-3 art and fixed-grid fallbacks.
- Semantic macro heights plus independent per-texel micro relief.
- World-anchored water refraction, a three-cell shore ramp, animated crest
  highlights, and boundary backtracking that prevents water sampling land.
- Fail-soft shader/FBO handling and an unchanged direct-ground fallback.
- Live `Structure relief`, `Surface relief`, and `Water waves` debug dials.
- Asset-backed CPU shader oracle and generated comparison contact sheet.

## Acceptance

- Manual battle playtest accepted the current effect/defaults on 2026-08-19.
- Debug tuning remains intentionally available for future art and map changes.
- Headless zero-strength output is pixel-identical, animated water advances with
  time, and the boundary oracle records zero water-to-land lookups.
