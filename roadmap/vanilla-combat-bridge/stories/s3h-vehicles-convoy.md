# S3h — VEHICLES + CONVOY layers in the bridge sink

> Bring parked map vehicles and convoy trucks (+ their turrets) into the combat-bridge backdrop.
> Drop-in command passes, but carries the **null-`selection` NPE** gotcha. See `../render-layers.md`.

## Goal

`GroundSceneBackdrop` draws `RenderLayer.VEHICLES` (static parked vehicles stamped into the map) and
`RenderLayer.CONVOY` (moving supply trucks + their turrets) under the ships, at world scale.

## Why this exists

Parked vehicles are part of the cityscape's texture (cover, props); convoys are a live objective
target. Both belong on the ground under the fleet fight.

## Approach

1. **`SCENE_LAYERS`** — add `RenderLayer.VEHICLES` and `RenderLayer.CONVOY`.
2. **`initOnGlThread()`** — `sprites.ensureVehicleSheets()` (`BattleSprites.java:546`) and
   `sprites.ensureConvoySprites()` (`:480`).

## Gotcha — CONVOY debug overlays read `ctx.selection` (NPE)

The `CONVOY` collect (`BattleRenderer.java:233`) appends two **DebugOnly own-GL overlays**:
`renderConvoyDockingPathsDebug` and `renderSelectedVehicleDebug`. The latter calls
`rc.selection.getSelectedVehicleIdx()` (`BattleRenderer.java:533`) — and the bridge builds its
`RenderContext` with **`selection = null`**, so collecting the CONVOY layer as-is **NPEs**.

Resolution options (decide at build):
- **Guard the debug overlays** on `ctx.selection != null` (cleanest — they're DebugOnly dev cruft
  that has no meaning in the bridge anyway).
- Or pass a no-op `SelectionState` into the bridge `RenderContext`.

The convoy *body* sprites themselves are `RenderContext`-safe (the `ConvoyRenderSystem` reads
`sim`/`camera`/`alphaMult`); only the bolted-on debug lambdas reach for `selection`.

## Latent vs. immediate

The bridge currently passes `List.of()` vehicles to `buildMap` (map-only probe), and convoys only
exist in a Sabotage-style scenario. So both layers are latent today; wire them now, populate when a
scenario does.

## Out of scope

- Convoy gameplay / docking in the bridge — sim-side.

## Acceptance

Layers wired + the `selection`-null path guarded; a bridge sim containing parked vehicles / a convoy
renders them at world scale under the ships with no NPE (including when no vehicle is selected).
