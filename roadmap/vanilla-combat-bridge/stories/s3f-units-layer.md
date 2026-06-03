# S3f — UNITS layer in the bridge sink

> Bring the `RenderLayer.UNITS` pass into the combat-bridge backdrop so ground units render
> under the vanilla ships: turret + drone-hub bodies, ground footprints, dead poses, live infantry
> sprites, and HP bars. **ACTIVE** — the first and highest-value layer of the render-layers thread
> (see `../render-layers.md`).

## Goal

`GroundSceneBackdrop` draws the `UNITS` layer with its world-unit camera, so every live ground unit
in the bridge sim appears under the ships at the right world scale. Marines are *never* proxied (the
architecture keeps infantry non-targetable), so this is the **only** way they get a visual in the
bridge — without it, a delivered squad would be invisible.

## Why this exists

The bridge today shows terrain + roofs but no actors. The fleet fight above is meaningless without
seeing the ground forces below. UNITS is the pass that turns the backdrop into a battlefield.

## Approach — grow the EnumSet + ensure the sheets

Two edits to `GroundSceneBackdrop`, no pass code touched (the seam is `renderWorld(rc, EnumSet)`):

1. **`SCENE_LAYERS`** — add `RenderLayer.UNITS` to the set (`GroundSceneBackdrop.java:55`).
2. **`initOnGlThread()`** — ensure the sheets `UnitRenderService` reads
   (`UnitRenderService.java:119-292`):
   - `sprites.ensureUnitSheets()` — live infantry + dead poses (dead loaded inside, `BattleSprites.java:531-543`).
   - `sprites.ensureMarineSecondarySprites()` — secondary-aim pose override (`:555`).
   - `sprites.ensureTurretSprites()` — turret base + recoil-barrel bodies (`:629`).
   - `sprites.ensureDroneHubSprite()` — drone-hub body (`:641`).

   The colored-quad fallbacks in `UnitRenderService` cover any sheet that fails to load, so a missing
   sheet degrades to a faction-tinted square rather than crashing — but ensure them so it reads right.

## Why it's safe in the bridge

- **`RenderContext` inputs.** `UnitRenderService.collect` reads only `ctx.sim`, `ctx.camera`,
  `ctx.alphaMult` — all provided by the bridge `rc`. It never touches `layout`/`highlights`/
  `selection`/`debugZonesVisible` (the null/zeroed fields). No NPE surface.
- **Vision uninitialized → fully visible.** The map-only bridge sim may never initialize vision;
  `getUnitVisibility` returns `VIS_VISIBLE` and `getFadeAlpha` returns `1f` when `!initialized`
  (`VisionService.java:121,127`). So the live-sprite + HP-bar sweeps draw everything, no fog gate.
- **No double-draw.** The structures mirrored by `SimProxyMirror` are **invisible** proxies; the
  UNITS sprites are their only visual.
- **World scale is automatic.** All six sweeps size off `cam.cellPxSize()` = `worldUnitsPerCell`
  (20), so turret `visualCells`/infantry `renderScale` land in world units with no extra math.

## What the probe shows today vs. latent

The current `SIM_COUPLED` probe (Ctrl+Shift+K) is map-only: the live units are the **defense-post
turrets + drone hubs** spawned by `buildMap`. So this story validates *immediately* on those —
turret bodies (with recoil-barrel + HP bar), hub bodies, footprints. Live infantry + dead poses stay
latent until a real battle scenario or `deliverSquad` (S3d) puts marines in the bridge sim; the code
path is exercised the moment they exist.

## Out of scope

- Marine delivery itself (`deliverSquad`) — that's S3d.
- The DRONES layer (separate from UNITS; drones self-bar in their own layer) — fold in with a later
  story if the bridge sim ever runs drones.
- Fog/highlights gating of units — `s3i`.

## Acceptance

Launch the bridge probe (Ctrl+Shift+K, `SIM_COUPLED`); confirm defense-post turrets + drone hubs
render under the ships at correct world scale, with recoil-barrels and HP bars, no NPE, and no frame
-time regression vs. the terrain-only backdrop. Verdict recorded: turrets visible + scaled; infantry
path confirmed latent-but-wired.

## Status

**Code-complete, build-clean — awaiting playtest verdict.** The two edits landed in
`GroundSceneBackdrop`: `RenderLayer.UNITS` added to `SCENE_LAYERS`, and the four unit sheets
(`ensureUnitSheets`, `ensureMarineSecondarySprites`, `ensureTurretSprites`, `ensureDroneHubSprite`)
ensured in `initOnGlThread()`. Class Javadoc updated (units no longer "left out"). No pass code
touched. Move to `complete/` once the Ctrl+Shift+K playtest confirms turret/hub render.
