# Ground Collector Allocation Cleanup

Status: PROPOSED — concrete cohesion cleanup.

Written: 2026-08-23

Read `battle-render-nouns.md` before implementing this story.

## Problem

`GroundRenderSystem.emitFloors` describes the command path as allocation-free in
steady state, but each render frame rebuilds the ground-kind array, block/sheet/
fill mapping arrays, and several `Color` values. These mappings change only when
the installed tile and generation registries change.

## Goal

Move stable ground-kind render resolution out of the hot per-frame collector if
profiling confirms the allocations are material.

## Acceptance

- A benchmark or allocation profile records the current cost and justifies the
  change.
- Stable ground-kind block, sheet, fill, street fallback, and related color
  values are resolved once per installed registry/version rather than per frame.
- Registry replacement or reload invalidates the cached resolution explicitly.
- Ground commands, autotile selection, fallbacks, visible-cell culling, and
  paint order remain byte-for-byte equivalent at the command boundary where
  practical.
- The collector's allocation claim matches measured steady-state behavior.

## Out of scope

- Baked terrain or FBO residency; see `dense-render-tiles.md`.
- Changes to tileset/generation authority.
- Unmeasured micro-optimization elsewhere in the renderer.
