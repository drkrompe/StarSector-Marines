# Bridge render layers — bringing the standalone scene into the EXTERNAL sink

> The standalone `BattleScreen` sink draws all 17 `RenderLayer`s; the combat-bridge sink
> (`GroundSceneBackdrop`, `AirProvider.EXTERNAL`) currently draws only `{GROUND, DOODADS, ROOFS}`.
> This thread brings the rest over, **one layer-bucket per story**. Decomposition doc — the
> per-story files live in `stories/` (`s3f`–`s3j`).

## The seam already exists — these stories are not new render code

The render-target seam shipped in **S3b**: `BattleRenderer.renderWorld(rc, EnumSet<RenderLayer>)`
(`BattleRenderer.java:724`) + a world-unit `BattleCamera` (`GroundSceneBackdrop.worldCamera()`).
Every pass is command-driven collect→drain and **projection-agnostic** — it emits coords through
the camera and the drain brackets its own GL. The standalone sink calls it with `allOf(...)` + a
screen-pixel camera; the bridge sink calls the **same pass code** with a subset enum + a world
camera. No pass forks.

So each story here is mechanically small:

1. Add the layer(s) to `GroundSceneBackdrop.SCENE_LAYERS`.
2. `ensureX()` that layer's sprite sheet(s) in `GroundSceneBackdrop.initOnGlThread()`.
3. Handle that layer's `RenderContext` inputs (most read only `sim`/`camera`/`alphaMult`, which
   the bridge already provides — but a few read `highlights`/`selection`, which the bridge passes
   `null`, see gotchas).

The work is the *verification* (does it render at the right world scale, no NPE, sane perf) and the
**design calls per layer** (do we even want fog in a top-down fleet-commander view?), not new
rendering.

## Three buckets

| Bucket | Layers | Work |
|---|---|---|
| **Drop-in** (projection-agnostic command passes) | `UNITS`, `OBJECTIVES`, `COMPOUND`, `VEHICLES`, `CONVOY`, `FOG`, `HIGHLIGHTS` | Grow `SCENE_LAYERS` + ensure sheets + handle `RenderContext` inputs. |
| **No-op under EXTERNAL air** | `SHUTTLES`, sim-sourced `FLYBY` | The host owns the air (`airSystem` doesn't tick), so there's nothing to draw. **Belongs to S3d**, not this thread. |
| **FBO / screen-space** | `DECALS`, `LIGHTING`, `IMPACT_FX` | Accumulator FBOs blit in *screen* space — they bypass the world camera, so they can't just join the EnumSet. Real retarget work (`s3j`). |

## Shared gotchas (apply to every story below)

- **`RenderContext` nulls.** The bridge builds `rc` with `layout=null, highlights=null,
  selection=null, debugZonesVisible=false`. A layer that reads only `sim`/`camera`/`alphaMult` is
  safe (UNITS, OBJECTIVES, COMPOUND, VEHICLES, FOG). Two are **not**: the `HIGHLIGHTS` collect reads
  `ctx.highlights`, and the `CONVOY` DebugOnly overlays read `ctx.selection.getSelectedVehicleIdx()`
  → **NPE** with the bridge's null selection. Those stories must thread a real source or no-op.
- **Vision is safe uninitialized.** The bridge sim is map-only; vision may never initialize.
  `VisionService.getUnitVisibility` returns `VIS_VISIBLE` and `getFadeAlpha` returns `1f` when
  `!initialized` (`VisionService.java:121,127`) — so unit passes draw everything fully, no fog gate,
  no NPE. The `FOG` pass itself early-returns on `!isInitialized()`.
- **No double-draw with proxies.** `GroundSimBridge` mirrors targetable structures as **invisible**
  vanilla proxies (targeting avatars, `ProxyTargetPlugin` pins invisible). They have no sprite, so
  adding our UNITS/structure sprites is the *only* visual — no overlap.
- **World scale.** Under the world camera, `cam.cellPxSize()` is `worldUnitsPerCell` (20), so every
  pass sizes in world units automatically. If a layer reads too large/small vs the ships, the knob
  is `WORLD_UNITS_PER_CELL`, not the pass.
- **Latent vs. immediate.** The current `SIM_COUPLED` probe is map-only: the only *live* units are
  defense-post turrets + drone hubs, and there are no vehicles/objectives/marines yet. So UNITS
  validates immediately (turrets render); VEHICLES/OBJECTIVES/marines stay latent until a real battle
  scenario (or `deliverSquad`, S3d) populates them. Each story notes what its probe shows today.

## Child stories

- **`s3f-units-layer.md`** — `UNITS` (turret + hub bodies, footprints, dead poses, live infantry,
  HP bars). **ACTIVE** — the highest-value layer (marines have no visual at all today). Detailed.
- **`s3g-objectives-compound.md`** — `OBJECTIVES` (charge sites, equipment drops) + `COMPOUND`
  (capture rings/arc/glyph). Drop-in; latent until objectives/compounds exist in the bridge sim.
- **`s3h-vehicles-convoy.md`** — `VEHICLES` (parked) + `CONVOY` (trucks + turrets). Carries the
  **null-`selection` NPE** gotcha (CONVOY debug overlays).
- **`s3i-fog-highlights.md`** — `FOG` + `HIGHLIGHTS`. Both carry **design calls** (does a
  fleet-commander view want fog? where do highlights come from with no on-screen selection?).
- **`s3j-fx-fbo-retarget.md`** — `DECALS`, `LIGHTING`, `IMPACT_FX`. The hard bucket: retarget the
  screen-space FBO blits to the combat world projection. Presentation polish; lowest priority.
