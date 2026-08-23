# Bridge Ground Highlights

Status: PLANNED

Written: 2026-08-23

Read `vanilla-combat-bridge-nouns.md` and `ground-control-mode.md` before
implementing this story.

## Current substrate

The shared renderer supports highlights, but the bridge has no authoritative
ground selection source. Passing an empty or fabricated selection through the
render context would make presentation invent interaction state.

## Goal

Render ground hover, selection, and order-target highlights from the real
bridge ground-control model.

## Acceptance

- Every highlight is derived from current ground-control state and projected by
  the same camera used for picking.
- Hidden or non-selectable entities do not leak through fog.
- Leaving ground-control mode clears bridge highlights without mutating
  simulation selection or vanilla ship selection.
- The bridge adds `HIGHLIGHTS` only when all required render-context inputs are
  genuinely supplied.

## Out of scope

- Designing the selection/order model itself.
- Highlighting vanilla fleet targets.
- Persistent ground decals.
