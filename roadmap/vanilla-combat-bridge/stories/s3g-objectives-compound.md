# S3g — OBJECTIVES + COMPOUND layers in the bridge sink

> Bring the objective markers (charge sites, equipment drops) and compound capture-state markers
> into the combat-bridge backdrop. Drop-in command passes; see `../render-layers.md`.

## Goal

`GroundSceneBackdrop` draws `RenderLayer.OBJECTIVES` (pulsing charge-site / kit-drop icons + the
charge-plant progress arc) and `RenderLayer.COMPOUND` (faction ring + capture-progress arc + kind
glyph) under the ships, so the strategic state of the ground battle is legible from the fleet view.

## Why this exists

Once UNITS lands, the player sees *who* is on the ground; OBJECTIVES + COMPOUND show *what they're
fighting over* — the capture/charge state that drives a Conquest or Sabotage battle. This is the
layer that makes the backdrop read as an objective-driven battle, not just a skirmish.

## Approach

Two edits to `GroundSceneBackdrop` (same shape as `s3f`):

1. **`SCENE_LAYERS`** — add `RenderLayer.OBJECTIVES` and `RenderLayer.COMPOUND`.
2. **`initOnGlThread()`** — `sprites.ensureObjectiveIcons()` (`BattleSprites.java:501`) for the
   alarm/danger/star icons. COMPOUND draws via `CompoundMarkerRenderer` (vector rings/arcs/glyph,
   no sheet) — nothing extra to ensure.

Both collects are `RenderContext`-safe: `collectObjectiveMarkers` reads `sim`/`camera`/`alphaMult`
(`BattleRenderer.java:627`); the COMPOUND collect reads `sim`, `sim.getCompoundService()`, `camera`,
`alphaMult` (`:228`). No `highlights`/`selection` touch. Pulse timing uses wall-clock
(`System.currentTimeMillis`), independent of sim dt — fine under the bridge's frame-driven advance.

## Latent vs. immediate

The current map-only probe has no charge sites / equipment drops and (depending on the generated
map) may have compounds. So OBJECTIVES is latent until a battle scenario stamps objectives; COMPOUND
renders as soon as the bridge sim has compounds. Both code paths are wired regardless.

## Out of scope

- The objective *gameplay* in the bridge (charge-plant progress, capture ticking) — that's sim-side,
  not this render story.

## Acceptance

With the layers wired, a bridge sim that contains a charge site / equipment drop / compound shows the
correct markers (pulsing icon, progress arc, capture ring) at world scale under the ships, no NPE.

## Status

**DONE, build-clean.** `OBJECTIVES` + `COMPOUND` added to `GroundBattleConfig.DEFAULT_SCENE_LAYERS`;
`sprites.ensureObjectiveIcons()` added to `GroundSceneBackdrop.initOnGlThread()` (COMPOUND is
vector-drawn, no sheet). Confirmed zero-risk: a grep of `BattleRenderer` for `rc.selection`/
`rc.highlights`/`rc.layout`/`rc.debugZonesVisible` finds **only** the CONVOY debug overlay (line 533,
S3h's gotcha) — neither OBJECTIVES nor COMPOUND touches a null `RenderContext` field. Latent on the
map-only probe (no objectives stamped; compounds render if the generated map has them).
