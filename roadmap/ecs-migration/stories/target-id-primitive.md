# Story: targetId SoA promotion (the keystone reference)

## Context

`targetId` is the most-read per-unit cross-reference in the sim: every
alive unit resolves its current target **every tick** via
`BattleSimulation.targetOf(u)` → `registry.getOrNull(u.targetId)`. It's
the natural keystone for fully deref-free dense target resolution — once
it's a `long[]` on the registry, a dense loop can resolve every unit's
target straight from `targetIdArray()` without a `Unit` dereference.

Already a `long` on `Unit`, and writes already funnel through the
`setTarget(Unit)` chokepoint, so the promotion is mechanical. The
template exists: `secondaryAimTargetId` (the first `long[]` primitive,
shipped `01fe905`) is the exact shape.

~28 referencing files, but most read through `targetOf()` / write
through `setTarget()` — the field-touching surface is small.

## Scope

### Primitive promotion (UnitRegistry + Unit)

- `long[] targetId` on UnitRegistry — grow/swap/snapshot lifecycle like
  `secondaryAimTargetId`.
- `Unit.localTargetId` transient seed/snapshot field (rename of the
  current `public long targetId`; the field becomes `local*` per design
  rule 2).
- Final accessors on Unit: `getTargetId()` / `setTargetId(long)`.
  `setTarget(Unit t)` keeps its signature and routes through
  `setTargetId(t == null ? 0L : t.entityId)`.
- Three tests per primitive on UnitRegistryTest (allocate-seed,
  release-snapshot, tail-swap) = 3 new tests.

### Consumer migration

The only direct field touch outside the chokepoints:

- **`BattleSimulation.writeReprioInline`** (`target.targetId = 0L`) →
  `target.setTargetId(0L)`. Runs on both the serial and queued reprio
  paths.
- **`AttackerIndexService.rebuild`** — reads `targetId` to build the
  attacker map; hot, runs once per tick in the serial phase. Migrate to
  the array read if it's iterating the registry; otherwise route through
  `getTargetId()`.
- **`TacticalScoring`, `SquadAlertSystem`, GOAP actions** — read via
  `targetOf()` already; `targetOf` stays a single delegate over
  `getOrNull(getTargetId())`, so these don't change.

### Leave alone

- `PendingTargetMutation.expectedTargetId` — a field on the mutation
  record (race-check payload), not on `Unit`. Don't touch.
- Non-Unit `*targetId` fields (`MapTurret`, `MountedTurret`) — already
  migrated to long ids in Phase 2d; separate storage.

## Acceptance

- `gradlew.bat compileJava` clean; all tests pass.
- Three new UnitRegistryTest tests.
- No direct read/write of the `targetId` field remains outside Unit's
  `localTargetId` and the registry's allocate/release.
- `targetOf(u)` and `setTarget(u, …)` semantics unchanged.

## Priority

Highest-value remaining promotion (hottest cross-reference, sets up
deref-free target resolution). Do after the burst triple if you want a
warm-up repeat first, or lead with this — it's self-contained.
