# S3b — Cityscape backdrop — ✅ SHIPPED

> **Verified in playtest — IT WORKS.** Commit: `347160a`.
>
> **Verdict:** the real ground scene (terrain + building structures) renders under the
> vanilla ships, panning/zooming with the free-cam, with the proxy markers locked to it.
> The render-target seam turned out to be the camera itself — a world-configured
> `BattleCamera` (world-unit viewport) makes the existing `BattleRenderer` draw in combat
> world coords, no `SceneCamera` interface / no codebase sweep. The concept is proven.
>
> **Open follow-up — ground/ship scale.** At `WORLD_UNITS_PER_CELL = 50` the ground cells
> read too large relative to the spacecraft; ships should tower over individual tiles. The
> fix is the single `S0BattleProbe.WORLD_UNITS_PER_CELL` knob (lower it — backdrop and
> proxies both derive from it, so they stay locked). The right value is a visual judgment
> to dial in-game; this is the architecture's deferred cross-scale convention surfacing
> early. Tracked as the scaling pass (with S3c, or standalone).

> _Story (as built):_ render the real ground scene *behind* the vanilla combat layer, so
> the fleet fight visibly happens over a battlefield. Follows S3a.

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

## Implementation (built — awaiting playtest)

Launch with **Ctrl+Shift+K** (the S3a `SIM_COUPLED` mode now also draws the scene). The
open question — *how coupled is the ground renderer to its screen/FBO setup?* — resolved
much better than feared.

**The render-target seam is the camera, and it was already there.** `BattleRenderer` is a
collect→drain pipeline: each pass turns sim cells into coordinates via a `BattleCamera`
and emits draw commands; `DrawListRenderer.drain` replays them into whatever GL
projection is active (and brackets its own `GlStateBracket`). The *only* thing binding
the scene to the standalone screen view is the camera transform — and `BattleCamera` is a
generic cell→affine map parameterized by viewport + cell size. Configured with a
**world-unit viewport** centered on the origin
(`setViewport(−gridW·u/2, −gridH·u/2, gridW·u, gridH·u, u)`), it emits
`(cell − grid/2)·u` — exactly the combat world coords the S3a proxies use. So retargeting
the whole renderer to the combat layer is *"configure the camera with a world viewport"*
— **no `SceneCamera` interface, no codebase-wide `BattleCamera→SceneCamera` sweep** (which
would have touched every render system's `cam` local + the FX internals). The abstraction
the spike imagined already exists as the camera's parameterization.

Pieces:
- **`GroundSceneBackdrop`** (`CombatLayeredRenderingPlugin`, `BELOW_SHIPS_LAYER`) — owns
  its own `BattleSprites` + `BattleRenderer` + a world-configured `BattleCamera`; lazily
  loads terrain/structure sheets on the GL thread (first `render()`), then draws the scene
  over the bridge's sim. Replaces the `CanvasBackdropRenderer` grid for `SIM_COUPLED`.
- **`BattleRenderer.renderWorld(rc, EnumSet<RenderLayer>)`** — additive subset overload. A
  system is collected only if its `layer()` is in the set (so passes whose sheets a host
  didn't load never run), and only those layers drain. The no-arg version delegates with
  `allOf`, so the screen path is byte-unchanged.
- The probe sim is now a **real generated cityscape** (`BspCityGenerator.generate`, fixed
  seed) instead of a bare arena, shared by the bridge and the backdrop.

Scope drawn here (terrain + structures): runs `GROUND` (floor + walls), `DOODADS` (props),
`ROOFS` (building tiles). Left out, as planned: the FBO accumulators (`DECALS`,
`LIGHTING`) and screen-coupled overlays (`FOG`, `HIGHLIGHTS`, `UNITS`, FX) — units are
shown by the proxy markers; those accumulators blit in screen space.

Pan/zoom come free: the combat free-cam moves the combat world projection, so the static
cell→world backdrop camera rides along.

Playtest checks pending: does the city read clearly under a live fleet fight; do the proxy
markers sit on the right structures; any scale/centering drift between backdrop and proxies.
