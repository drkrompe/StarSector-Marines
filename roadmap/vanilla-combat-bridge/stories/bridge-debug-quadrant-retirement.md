# Retire `BridgeRenderer` Quadrant Diagnostics

Status: PROPOSED

Written: 2026-08-24

Read `vanilla-combat-bridge-nouns.md` before changing this story.

## Problem

`BridgeRenderer` still carries disabled `DEBUG_QUADRANTS` and
`DEBUG_DIRECT_QUADRANTS` modes. Their branches bypass the normal scene/FBO path
and retain quadrant helpers, pixel readback, and diagnostic-only state handling
that no longer participates in production or acceptance.

## Authority boundary

The Vanilla Combat Bridge board owns this bounded renderer cleanup, but it does
not acquire scene-composition or UI authority. `BridgeRenderer` remains the
shared FBO renderer used by `BridgePanelPlugin` and `PlanetIntelPanel`; removing
dead alternatives must preserve both callers' scene, camera, framebuffer, and
borrowed GL-state contracts.

## Scope

- Remove both quadrant flags and their gated render branches.
- Remove `renderDirectQuadrants`, `renderQuadrantsToFbo`, `fboPixelLogged`, and
  diagnostic-only imports, state, or logging made unused by that deletion.
- Make the established scene-to-FBO path unconditional.
- Preserve normal initialization, failure handling, allocation, resizing,
  scene-null clearing, UI-framebuffer restoration, and first-render diagnostics.

## Acceptance

- No quadrant flag, quadrant helper, or `glReadPixels` diagnostic path remains
  in `BridgeRenderer`.
- `BridgeRenderer.render` always renders the normal scene to its FBO before
  blitting.
- A live smoke check confirms the `BridgePanelPlugin` scene and
  `PlanetIntelPanel` globe still render.
- The renderer restores the inherited UI framebuffer and viewport and retains
  its normal failure-disable behavior.

## Out of scope

- Renaming `BridgeRenderer`.
- Production bridge launch, HUD policy, ground-scene decal projection, or
  combat-host rendering behavior.
- Changes to scene, camera, drawable, or GL-state architecture beyond deleting
  the diagnostics.

## Dependencies

None. This cleanup is independent of `bridge-production-launch.md`; its smoke
check uses the two existing UI hosts.
