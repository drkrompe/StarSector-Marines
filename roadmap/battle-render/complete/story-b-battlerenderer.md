# Story B — Extract `BattleRenderer` + `RenderContext` — ✅ SHIPPED

## What landed

`BattleScreen` **3640 → 1251 lines**. New in `ops/battleview/`:
- `BattleRenderer.java` (1911) — owns the world-pass machinery (six `QuadBatch`es
  + `buildTileBatches()`, `solidBatch`, `contrailBatch`, `decalAccumulator`,
  `lightAccumulator`, `impactFx`, `flybyOverlay`, `compoundMarkers`,
  `contrailsLive/Decaying`, `timeOfDay`, render-only constants) and all ~36
  world-layer `render*`/`draw*` methods. `renderWorld(RenderContext)` holds the
  verbatim pass sequence; `onAttach()` does the flyby/impactFx wiring.
- `RenderContext.java` — per-frame carrier: `sim, camera, layout, alphaMult,
  realDt (was lastAdvanceDt), debugZonesVisible, highlights, selection`. Promoted
  to `public` (BattleScreen is in `ops`, not `ops.battleview`); fields stay
  package-private.

`BattleScreen.render()` now: scissor bracket → build `RenderContext` →
`renderer.renderWorld(rc)` → pop → chrome (`renderSpeedMarker`/`hud`/
`renderBanner`/`widgets`, all kept). `attach()` calls `renderer.buildTileBatches()`
+ `renderer.onAttach()`. `advance()`/`detach()` reach the FX subsystems through
new renderer accessors: `getFlybyOverlay/getImpactFx/getCompoundMarkers/
getLightAccumulator/getDecalAccumulator`.

Verified: `gradlew build` green + tests pass. Audited: scissor logic verbatim,
`RenderContext` arg order correct, `renderWorld` pass order + `debugZonesVisible`
/`timeOfDay` gates intact. In-game render check still recommended.

## Deviations from spec / follow-ups

- **Dual-use constants duplicated, not cross-referenced.** `MARINE_TRACER` /
  `DEFENDER_TRACER` and the `bearingDeg()` helper are needed by both the moved
  `renderShots` and the staying `spawnImpactFx`; the agent duplicated them
  (identical values) rather than referencing `BattleScreen.NAME` (which would
  create a renderer→screen back-dependency). Acceptable but two sources of
  truth. **Follow-up:** dedupe into a shared home — likely move `spawnImpactFx`'s
  render-coloring concern toward the renderer, or a small shared constants type.
- **Inter-pass comments condensed.** `renderWorld` trimmed some load-bearing
  rationale (e.g. the drone pass lost the `TurretAim.airLosVisible` air-LoS
  note). Order is fully preserved; the rationale lives in git history.
  **Follow-up:** restore the fuller inter-pass comments (overview flags them as
  load-bearing).
- **Unused imports** left in `BattleScreen` after the move (compile clean).
  **Follow-up:** trivial import cleanup.
- `advanceRoofAlphaLerp()` and `DEBUG_RENDER_DOCKING_PATHS` correctly identified
  as loop/debug-side and kept on / wired from `BattleScreen`.

---

# Original design / spec

Sever the world-render pipeline from `BattleScreen` (the loop/input/audio).
Move the ~36 world-layer `render*`/`draw*` methods **verbatim** into a new
`BattleRenderer` in `com.dillon.starsectormarines.ops.battleview`, plus a small
`RenderContext` carrier for the per-frame inputs. Still a call sequence (no
DrawList yet — that's Story C); the win is that `BattleScreen` shrinks toward
"just the loop."

## The seam (locked)

`BattleScreen.render()` today is three parts:
1. **Scissor bracket** setup/teardown (lines ~1276-1292 + the `glPopAttrib`).
2. **World-layer passes** (`renderGrid` … `lightAccumulator.render`, ~1293-1352).
3. **Chrome** — `renderSpeedMarker`, `hud.render`, `renderBanner`, `widgets.render`.

Per the north-star ("`BattleScreen` owns the scissor bracket"), the split is:

- **`BattleScreen` keeps:** the scissor bracket, all chrome (`renderSpeedMarker`
  + `renderBanner` stay here), the loop/input/audio, and ownership of
  `camera`, `layout`, `position`, `ctx`, `hud`, `widgets`, `highlights`,
  `sprites`, `debugZonesVisible`, `lastAdvanceDt`. `render()` becomes: set up
  scissor → build a `RenderContext` → `renderer.renderWorld(rc)` → pop scissor →
  chrome.
- **`BattleRenderer` owns:** the world-pass machinery and the world-render
  methods. It holds a ref to `BattleSprites` and owns the six per-sheet
  `QuadBatch`es + `buildTileBatches()`, `solidBatch`, `contrailBatch`,
  `decalAccumulator`, `lightAccumulator`, `impactFx`, `flybyOverlay`,
  `compoundMarkers`, `contrailsLive`/`contrailsDecaying`, `timeOfDay`, and the
  render-only constants.

### Why these stay shared (→ `RenderContext`, not renderer fields)

- `camera`, `layout` — rebuilt in `rebuild()` and read by **input** handlers
  (`processInput` ~1143/1229). The screen owns them; the renderer reads them
  per-frame.
- `highlights` — `final HighlightOverlay`, **published by HUD panels** (getter
  at ~793), rendered in the world layer.
- `debugZonesVisible` — toggled by **input** (Z key); gates `renderZoneOverlay`.
- `lastAdvanceDt` — written by `advance()` (the loop), read by `renderShots` /
  `renderContrails` to age contrails in real time. Becomes `RenderContext.realDt`.

## `RenderContext` (per-frame carrier)

```java
final class RenderContext {
    final BattleSimulation sim;
    final BattleCamera camera;
    final BattleLayout layout;
    final float alphaMult;
    final float realDt;            // was lastAdvanceDt
    final boolean debugZonesVisible;
    final HighlightOverlay highlights;
}
```

`renderWorld(RenderContext rc)` stashes `rc` in a field and the moved methods
read `rc.camera` / `rc.layout` where they used the bare `camera` / `layout`
fields. (Mechanical: `camera` → `rc.camera`, `layout` → `rc.layout`,
`lastAdvanceDt` → `rc.realDt`, `debugZonesVisible` → `rc.debugZonesVisible`,
`highlights` → `rc.highlights`. `alphaMult` stays the per-method param it
already is; `sim`/`units` stay params too.)

`renderWorld` body = the verbatim contents of `render()` lines 1293-1352
(`renderGrid` through the `lightAccumulator.render` block), with the
`debugZonesVisible`/`timeOfDay` reads pointed at `rc`/the renderer field.

## Methods that MOVE (verbatim, into `BattleRenderer`)

`renderGrid`, `renderTiledFloorsAndWalls`, `renderZoneOverlay`,
`drawCrosswalkStripes`, `renderDoodads`, `drawRoadTile`, `drawUrbanTile3Frame`,
`drawNatureTile`, `drawSameKindAutotile`, `drawFloorsTile`, `drawWaterTile`,
`drawTile`, `renderDecals`, `renderFogOverlay`, `renderRoofs`, `renderUnits`,
`renderUnitSprite`, `renderVehicles`, `renderTurrets`, `renderDroneHubs`,
`renderDrones`, `drawTurretLayer`, `renderTurretQuadFallback`,
`renderDeadUnits`, `renderUnitQuadFallback`, `renderConvoyVehicles`,
`renderConvoyDockingPaths`, `renderSelectedVehicleDebug`, `renderShuttles`,
`renderShuttleEngines`, `renderShuttleTurrets`, `renderObjectiveMarkers`,
`drawTintedIcon`, `drawProgressArc`, `renderShots`, `renderContrails`.
Plus the new `renderWorld(RenderContext)` orchestrator and `buildTileBatches()`.

## Methods that STAY (`BattleScreen`)

`render()` (now: scissor + `renderer.renderWorld(rc)` + chrome),
`renderSpeedMarker`, `renderBanner`, `advance`, `attach`, `processInput`/input
handlers, `rebuild`, audio helpers.

## Constant partition

Render-only constants move with the methods (resolve by usage; build is the
gate): `FLOOR_COLOR`, `WALL_COLOR`, `MARINE_COLOR`, `DEFENDER_COLOR`,
`CIVILIAN_COLOR`, `HP_BG`, `HP_FG`, `MARINE_TRACER`, `DEFENDER_TRACER`,
`SHOT_LIFETIME_REF`, `UNIT_FRAC`, `HP_BAR_H`, `HP_BAR_GAP`, `CHARGE_*`,
`KIT_DROP_*`, `PROGRESS_ARC_SEGMENTS`, `WEAPON_UP_TIME`, `RECOIL_DURATION`,
`RECOIL_DISTANCE_FRAC`, `ROAD_FILL`, `COURTYARD_FILL`, `CROSSWALK_*`,
`GROUND_TILE_EDGE_INSET_PX`, `GROUND_SMALL_TILE_EDGE_INSET_PX`.

Stay in `BattleScreen` (chrome/loop/audio/input): `BANNER_BG`, `VICTORY_COLOR`,
`DEFEAT_COLOR`, `HEADER_COLOR`, `ACTIVE_SPEED`, `SPEED_*`, `SPEED_OPTIONS`,
`SPEED_KEYS`, all audio pools/SFX/music/loop constants.

**Rule:** a constant referenced by *both* moved and staying code stays in
`BattleScreen` as `static final` and the renderer takes a copy or it's promoted
to a shared home — but verify first; most partition cleanly. Don't create a
back-dependency from renderer → screen.

## Ownership wiring

`BattleScreen` keeps `private final BattleSprites sprites` (its `attach()` still
drives the `sprites.ensureX()` calls) and gains
`private final BattleRenderer renderer = new BattleRenderer(sprites);`.
`attach()` calls the ensures, then `renderer.buildTileBatches()`. The
`impactFx`/`flyby`/`lightAccumulator` wiring that lived in `attach()`
(`flybyOverlay.setLightAccumulator(...)`, `impactFx.ensureSprites()`) moves to
operate on the renderer's instances — expose `renderer.onAttach()` or wire via
accessors. `advance()` writes `lastAdvanceDt` (still a screen field) and feeds
it into the per-frame `RenderContext`.

## Verify

`gradlew build` green + tests pass. In-game battle renders identically (all
passes, correct paint order, scissor clip intact at zoom, contrails age during
pause). Opus critique pass on the commit.
