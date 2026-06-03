# S3j — FX / FBO layers retargeted to the bridge sink

> The hard bucket: `DECALS`, `LIGHTING`, `IMPACT_FX`. These are **not** drop-in EnumSet additions —
> they blit through accumulator FBOs / own GL in *screen* space, bypassing the world camera. Lowest
> priority (presentation polish). See `../render-layers.md`.

## Goal

Get bullet-hole decals, the time-of-day lightmap multiply, and shot-impact sparks to render correctly
into the combat layer under the ships — i.e. through the **world** projection, not the standalone
screen projection they assume today.

## Why this is different from the drop-in stories

The drop-in layers emit camera-relative draw commands and the drain brackets its own GL, so a
world-unit camera is the only change needed. These three don't:

- **DECALS** — `DecalAccumulator.render` (`BattleRenderer.java:378`) blits a persistent FBO; the blit
  is in screen space. Pointed at the combat layer it would land in the wrong projection.
- **LIGHTING** — `LightAccumulator.render` (`:252`) multiplies a full-screen lightmap quad; same
  screen-space assumption, and a fleet-commander view may not want a ground time-of-day multiply over
  the whole combat scene at all (**design call**).
- **IMPACT_FX** — `impactFx.render(ctx.camera, ...)` (`:248`) is own-GL and *does* take the camera, so
  it's the closest to free — but it has no source under `AirProvider.EXTERNAL` until shots exist in
  the bridge sim, and it shares the FBO-era GL-state assumptions worth re-checking.

## Approach (sketch — flesh out when prioritized)

- Retarget the FBO blits to draw their accumulator quad through the world camera (or composite the
  FBO into the combat layer at the right world rect) instead of a screen-space full-viewport quad.
- Re-validate GL state under the combat engine's polluted GL (the `GlStateBracket` discipline) for
  each own-GL pass.
- Make LIGHTING opt-in for the bridge (it may stay standalone-only).

## Explicitly NOT here

- **`SHUTTLES`** and sim-sourced **`FLYBY`** — under `AirProvider.EXTERNAL` the host owns the air, so
  the sim ticks no shuttles/flyby and there is nothing to draw. Those belong to **S3d** (the
  host-side shuttle handoff), not this thread.

## Acceptance

Whichever of decals / lighting / impact-FX are kept render in the correct world projection under the
ships with correct GL state; LIGHTING's keep/drop decision for the bridge is recorded.

## Decision (2026-06) — deferred; no source yet + real projection work

Held until there's content to render and the retarget pays off. Two reasons stack:

- **No source in the bridge today.** All three are driven by *sim-side* combat events
  (`DECALS` = shot-impact craters, `IMPACT_FX` = shot-endpoint sparks) or a sim lightmap
  (`LIGHTING`). The map-only `SIM_COUPLED` probe runs no sim-side shooting and no day/night, so
  there is nothing to draw. They light up only once the bridge sim actually fights a battle
  (post-`deliverSquad`, S3d) — wiring them now would be untestable speculation
  ([[feedback_ship_then_optimize]]).
- **It's the genuinely hard bucket.** `DECALS`/`LIGHTING` blit accumulator FBOs in *screen* space,
  so unlike S3f–S3h they can't just join the EnumSet — they need real projection-retarget work (or
  world-rect FBO compositing). `LIGHTING` also carries a design call: a fleet-commander view likely
  does **not** want a ground time-of-day multiply over the whole combat scene (it would tint the
  vanilla ships too). `IMPACT_FX` is the closest to free (it takes the camera) but still shares the
  FBO-era GL-state assumptions worth re-checking.

So S3j stays open as the **last** render-layer item, picked up when the bridge has live sim combat
and the projection-retarget is worth the cost. `SHUTTLES`/`FLYBY` remain S3d's, not this thread.
