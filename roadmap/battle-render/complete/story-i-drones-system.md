# Story I — DRONES pass → DroneRenderSystem — ✅ SHIPPED (in-game verify pending)

Recon/attack drones (`renderDrones`) migrated into the command model as
`DroneRenderSystem`, emitting the `DRONES` layer. First system to land directly
into the `List<RenderSystem>` registry (added to the list + dropped its bespoke
inline drain slot — no new wiring shape).

## Command decomposition (per live/crashing drone, submission = paint order)

1. **Hull** → `SPRITE` — whole rotated drone sprite (`ShuttleSpriteCache.sprite`,
   sized `Drone.VISUAL_CELLS`, rotated by `body.facingDegrees`).
2. **HP bar** (alive only) → two `SOLID_RECT`s — background then green fill scaled
   by `hp/maxHp`, at the same `barX/barY` geometry the inline pass used.

Per-drone hull-then-bar order is preserved exactly, so a later drone's hull can
overlap an earlier drone's bar identically to before. Whole-texture sprite → uses
the non-batched `SPRITE` command, **no batch registration**.

## Gating ported verbatim

Same skip/fade logic as the inline pass: skip `crashed`; skip dead-and-not-
crash-started; vision gate (`VIS_HIDDEN` skip while alive); `drawAlpha` folds the
`VIS_FADING` fade-alpha (alive) or the crash fade-out `t = crashTimer /
CRASH_DURATION_SEC` (dead). Lives above `ROOFS` so drones over buildings overlay
the roof rather than being occluded.

## Sprite load hoisted to attach (collect stays GL-free)

`renderDrones` lazily called `sprites.ensureDroneSprite()` (a `loadTexture` GL
upload) inside the pass. Since `collect` must stay GL-free (the registry's
collect-all phase runs before any GL), the ensure moved to `BattleScreen.attach`
alongside the other `ensure*` sprite loads; `DroneRenderSystem.collect` just reads
`sprites.droneSprite()` and no-ops if null. The `DRONE_HUB` pass
(`renderDroneHubs`) is **not** part of this story — it's a UNITS-layer pass
(dispatched from `renderUnits`) and stays inline + lazy until UNITS migrates.

## Shared constants

`HP_BG` / `HP_FG` / `HP_BAR_H` / `HP_BAR_GAP` are shared with the still-inline
UNITS HP-bar pass, so (like `RECOIL_*` for shuttle turrets) they stayed in
`BattleRenderer` as the single source of truth and were made package-visible
(`static final`, dropped `private`); `DroneRenderSystem` references them.

## No rotation-parity risk

Hull rotation goes through the `SPRITE` command (`SpriteAPI.setAngle` +
`renderAtCenter`) — the exact API the inline `drawTurretLayer` used — so rotation
is identical by construction (no `appendRotated` convention question like CONVOY).

## Verified

`mcp__intellij__build_project` clean; `gradlew test` green. **In-game render
verification pending** — confirm drones render rotated over roofs with HP bars,
correct fade on going-hidden and on crash. The inline `renderDrones` is retained
`@Deprecated` + **uncalled** as a one-line-rewire rollback; delete once parity is
confirmed. (`drawTurretLayer` stays — still live for the map-turret / drone-hub
passes.)
