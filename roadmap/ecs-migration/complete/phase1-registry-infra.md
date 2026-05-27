# Phase 1 — UnitRegistry infrastructure

Shipped with the initial registry commits.

Dense `Unit[]` + monotonic `long entityId` + `Long2IntOpenHashMap` +
swap-and-pop release. No generation bits (IDs never recycle;
lazy-validity via `getOrNull` returning null).

## What landed

- `UnitRegistry` class: dense array, allocate/release, entityId→denseIdx
  lookup via fastutil map.
- `Unit.entityId`, `Unit.denseIdx`, `Unit.registry` back-references.
- `UnitUpdateSystem` dispatch reads from registry's dense array (Phase 2a).
