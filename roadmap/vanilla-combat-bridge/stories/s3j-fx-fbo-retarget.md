# S3j — FX / FBO layers retargeted to the bridge sink

> ~~The hard bucket: `DECALS`, `LIGHTING`, `IMPACT_FX`.~~ **LIGHTING removed 2026-06-29** (see
> backlog); **IMPACT_FX shipped** (camera-projected, added 2026-06-27 with the bridge audio fix).
> Bucket is now **`DECALS` only** — the FBO blit in *screen* space that bypasses the world camera.
> Lowest priority (presentation polish). See `../render-layers.md`.

## Goal

Get bullet-hole decals to render correctly into the combat layer under the ships — i.e. through the
**world** projection, not the standalone screen projection they assume today. (~~time-of-day lightmap
multiply~~: LIGHTING removed 2026-06-29; ~~shot-impact sparks~~: IMPACT_FX shipped 2026-06-27.)

## Why this is different from the drop-in stories

The drop-in layers emit camera-relative draw commands and the drain brackets its own GL, so a
world-unit camera is the only change needed. DECALS doesn't:

- **DECALS** — `DecalAccumulator.render` (`BattleRenderer.java:378`) blits a persistent FBO; the blit
  is in screen space. Pointed at the combat layer it would land in the wrong projection.
- ~~**LIGHTING**~~ — removed 2026-06-29 (`LightAccumulator`, `WeaponLights`, `TimeOfDay` deleted).
  The design call (bridge opt-in vs. standalone-only) is now moot.
- ~~**IMPACT_FX**~~ — shipped 2026-06-27 (camera-projected, added to `DEFAULT_SCENE_LAYERS` with the
  bridge audio fix; `impactFx.render(ctx.camera, ...)` takes the camera directly).

## Approach (sketch — flesh out when prioritized)

- Retarget the `DecalAccumulator` FBO blit to draw through the world camera (or composite the
  FBO into the combat layer at the right world rect) instead of a screen-space full-viewport quad.
- Re-validate GL state under the combat engine's polluted GL (the `GlStateBracket` discipline).

## Explicitly NOT here

- **`SHUTTLES`** and sim-sourced **`FLYBY`** — under `AirProvider.EXTERNAL` the host owns the air, so
  the sim ticks no shuttles/flyby and there is nothing to draw. Those belong to **S3d** (the
  host-side shuttle handoff), not this thread.

## Acceptance

Decals render in the correct world projection under the ships with correct GL state.

## Decision (2026-06) — deferred; no source yet + real projection work

Held until there's content to render and the retarget pays off. Two reasons stack:

- **No source in the bridge today.** DECALS is driven by *sim-side* combat events (shot-impact
  craters). The map-only `SIM_COUPLED` probe runs no sim-side shooting, so there is nothing to draw.
  Decals light up only once the bridge sim actually fights a battle (post-`deliverSquad`, S3d) —
  wiring now would be untestable speculation ([[feedback_ship_then_optimize]]).
- **It's the genuinely hard bucket.** `DECALS` blits an accumulator FBO in *screen* space, so unlike
  S3f–S3h it can't just join the EnumSet — it needs real projection-retarget work (or world-rect FBO
  compositing).

So S3j stays open as the **last** render-layer item, picked up when the bridge has live sim combat
and the projection-retarget is worth the cost. `SHUTTLES`/`FLYBY` remain S3d's, not this thread.
