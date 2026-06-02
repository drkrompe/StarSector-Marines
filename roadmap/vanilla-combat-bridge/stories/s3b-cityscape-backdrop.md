# S3b — Cityscape backdrop (stub)

> Render the real ground scene *behind* the vanilla combat layer, so the fleet fight
> visibly happens over a battlefield. Scoped, not built. Follows S3a.

## Goal

Draw the existing `battle/` ground scene (terrain, structures, roads, scarring) into the
combat **world transform** on `CombatEngineLayers.BELOW_SHIPS_LAYER` / `BELOW_PLANETS`,
at `WORLD_UNITS_PER_CELL` (=50), via a `CombatLayeredRenderingPlugin`. Replaces the
S0b/S2 placeholder grid plate with the actual map.

## Why this exists

S2 proved we can render below ships (the canvas backdrop). S3b makes the canvas *real*:
the legible "fleet above a city" picture, and the spatial frame the air-to-ground and
shuttle work hang off of.

## Scope

**In:**
- A backdrop renderer that draws ground tile layers + structures + decals through the
  mod's `render2d` batches under `GlStateBracket` (same pattern as `CanvasBackdropRenderer`),
  transformed to combat world coords.
- Camera already handled (`SpectatorCanvasPlugin` `viewport.set()` free cam).

**Out:**
- Infantry sprites (not targetable, not the point at this scale — keep sim-internal or a
  coarse density hint at most).
- The render-target refactor beyond what's needed: the ground renderer currently targets
  the mod's FBO dialog ([[architecture_decisions]]); S3b points it at the combat world
  transform. Scope that as a renderer-target seam, not a rewrite.
- Lighting / fog parity with the standalone ground view (later polish).

## Open questions

- How much of the ground renderer assumes its FBO / screen-space setup vs. accepts an
  arbitrary world transform? That gap sizes S3b.
- LOD: at fleet-scale zoom the city is tiny; do we draw a simplified far representation
  and the detailed one only when zoomed in (ties into S3d scale-down)?

## Acceptance

The spectator battle shows the actual mission map under the ships, correctly scaled and
camera-panned; structures line up with their S3a proxies. Verdict: does the ground scene
read clearly behind a live fleet fight?
