# Bridge Decal Projection

Status: DEFERRED

Written: 2026-08-23

Read `vanilla-combat-bridge-nouns.md` before implementing this story.

## Current substrate

Camera-projected shots and impact effects already render in the combat world,
and the retired lighting layer is not coming back. Persistent `DECALS` remain
the only bridge render bucket whose standalone path depends on screen-space FBO
accumulation and blitting.

## Goal

Give persistent ground decals an explicit combat-world projection without
making the shared renderer guess which framebuffer or coordinate system owns
them.

## Acceptance

- Decal accumulation and presentation use a declared projection and target
  sized for the bridge camera.
- Panning and zooming keep scars registered to ground cells.
- Resize, pause, host exit, and GL-state restoration are verified in the live
  combat engine.
- The standalone render path remains unchanged.

## Out of scope

- Restoring the removed lighting layer.
- Reimplementing already shipped shot/impact particles.
- General post-processing for vanilla ships.
