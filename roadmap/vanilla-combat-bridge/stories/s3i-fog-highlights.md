# S3i — FOG + HIGHLIGHTS layers in the bridge sink

> Bring fog-of-war darkening and cell highlights into the combat-bridge backdrop. Both are
> projection-agnostic command passes, but both carry **design calls**, not just wiring. See
> `../render-layers.md`.

## Goal

Decide whether — and how — the bridge view shows `RenderLayer.FOG` (per-cell vision darkening) and
`RenderLayer.HIGHLIGHTS` (selected-squad / debug cell tints), then wire whichever ones survive the
design call.

## Why this is a design story, not just wiring

Unlike UNITS/OBJECTIVES, these two layers encode a **player-POV assumption** that may not hold in the
bridge:

- **FOG** — the standalone screen is a ground-commander POV with fog-of-war. The bridge is a
  *fleet*-commander looking down at a battle below. Does that view have fog at all? If the player
  commands from orbit, the whole surface may be revealed; if vision is still squad-scoped, fog
  applies. **Open question — resolve before wiring.** Mechanically it's safe either way: the FOG
  collect early-returns on `!vis.isInitialized()` (`BattleRenderer.java:389`), so in the map-only
  probe it's a no-op until vision exists.

- **HIGHLIGHTS** — the collect reads `ctx.highlights` (`BattleRenderer.java:218`), which the bridge
  builds as **`null`**. There is no on-screen squad selection in the bridge yet, so there is no
  source for highlights. This story must either (a) thread a real `HighlightState` from the bridge's
  input layer, or (b) leave HIGHLIGHTS out until the bridge grows a selection model. Verify
  `HighlightRenderer.collect` null-safety before adding the layer either way.

## Approach (once the design calls are made)

1. **`SCENE_LAYERS`** — add `RenderLayer.FOG` and/or `RenderLayer.HIGHLIGHTS` per the decisions above.
2. **`initOnGlThread()`** — no sheets (both are solid-rect / tint passes).
3. For HIGHLIGHTS: thread a non-null `highlights` source into the bridge `RenderContext`, or gate the
   collect on `ctx.highlights != null`.

## Out of scope

- Building a selection/input model for the bridge — if HIGHLIGHTS needs it, that's its own thread.

## Acceptance

The fog/highlights design calls are recorded (with rationale); whichever layers are kept render
correctly under the ships with no NPE on the bridge's null `highlights`/uninitialized vision.
