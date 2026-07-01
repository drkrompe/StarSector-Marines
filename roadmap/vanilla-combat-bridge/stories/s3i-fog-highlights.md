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

## Decision (2026-06) — both layers skipped; no wiring

Resolved as a **decision-record**: neither `FOG` nor `HIGHLIGHTS` joins the bridge scene layers.

- **FOG — skipped.** The bridge is a *fleet-commander / orbital* POV: from above, the player sees
  the whole battlefield (their forces + the planet's defenses). Fog-of-war is a *ground-commander*
  mechanic and stays on the standalone `BattleScreen`. (Also a no-op in the bridge today anyway —
  the FOG collect early-returns on `!vis.isInitialized()` and the map-only sim never inits vision.)
  Revisit only if a future bridge mode wants squad-scoped recon tension.
- **HIGHLIGHTS — deferred (no source).** The collect reads `ctx.highlights`, which is sourced from
  an on-screen squad selection — and the bridge has **no selection/input model**. Building one is
  its own thread (out of scope here). Left out until the bridge grows a selection model; revisit
  then.

No code change: `GroundBattleConfig.DEFAULT_SCENE_LAYERS` is unchanged. This story is complete as a
recorded decision.

## Reversal (2026-07) — FOG wired in; HIGHLIGHTS still deferred

The FOG-skip call is **reversed**. `RenderLayer.FOG` now joins
`GroundBattleConfig.DEFAULT_SCENE_LAYERS`.

- **Why the reversal.** The 2026-06 skip leaned partly on "a no-op anyway — the map-only sim never
  inits vision." That premise no longer holds: the bridge sim runs full vision (`BattleSimulation`
  inits `FogOfWarService` and registers player units as contributors), so the **unit-visibility gate
  is live in the bridge** — enemies pop in and out of sight as the player's LoS changes. With no
  matching fog overlay, those units read as *emerging from nowhere*. Drawing the per-cell fog
  darkening restores the visual contract that "hidden = dark, revealed = clear," so appearance
  tracks the LoS the player already experiences. The orbital-POV framing is superseded by this
  concrete UX need.
- **Mechanics (verified before wiring, per the "design call" warning above).** `collectFogOverlay`
  (`BattleRenderer.java:366`) is fully `rc.camera`-projected — same as the SHOTS/IMPACT_FX passes the
  bridge already draws — so it projects correctly under the world-space bridge camera with no retarget
  work. `renderWorld(rc, layers)` collects a pass only if its layer is in the set and drains in
  enum-ordinal order, so adding `FOG` slots it after DOODADS and before UNITS/ROOFS (its intended
  paint order) automatically. The collect early-returns on `!isInitialized()`, so it stays safe on any
  bridge path that hasn't inited vision.
- **HIGHLIGHTS — still deferred.** Unchanged from the 2026-06 record: no selection/input model in the
  bridge, so no source for `ctx.highlights`. Revisit when the bridge grows a selection model.

Code change: `RenderLayer.FOG` added to `GroundBattleConfig.DEFAULT_SCENE_LAYERS`.
