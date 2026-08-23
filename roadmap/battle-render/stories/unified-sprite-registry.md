# Unified Sprite Registry

Status: PROPOSED

Written: 2026-08-23

Read `battle-render-nouns.md` before implementing this story.

## Current substrate

`BattleSprites` centralizes loading but still repeats lazy flags, category maps,
and two basic cache shapes across unit, vehicle, turret, shuttle, projectile,
layered-actor, decal, and tile assets. `SheetTexture` already normalizes part of
the tile-sheet lifecycle, while layered unit/mech assets have real composed
metadata that must not be flattened away.

## Goal

Consolidate repeated path-based load, slice, aspect, and failure caching behind
one render-tier resolver while retaining typed wrappers for genuinely different
asset contracts.

## Contract

- Asset path is the stable universal key until profiling justifies an interned
  handle.
- Resolution is lazy, idempotent, and failure-cached; one path never loads the
  same graphics resource twice.
- Domain identity maps to paths outside the generic store. Simulation state
  never receives `SpriteAPI`, cache objects, or renderer-owned handles.
- Whole textures and sliced sheets share one base descriptor; tile dimensions
  and layered actor composition remain typed extensions where needed.
- Existing fail-soft rendering and auto-slicing behavior remains unchanged.

## Acceptance

Projectile assets prove the resolver first, then categories migrate in bounded
slices. Redundant load flags/maps and `UnitSpriteCache`/`ShuttleSpriteCache`
disappear only after their callers use the shared descriptor. The final
`BattleSprites` surface expresses asset policy and special composition rather
than duplicating generic cache lifecycle.

## Out of scope

- Animation playback or simulation components.
- JSON descriptors without a concrete sheet the current slicer cannot handle.
- Batching, command, or GL changes.
