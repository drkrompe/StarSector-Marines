# Story: moveProgress + renderX/renderY SoA promotion

## Context

Three floats that form the movement interpolation group: `moveProgress`
controls the lerp factor, `renderX`/`renderY` are the interpolated
world-space coordinates written each tick. All three are updated
together in `Unit.advanceAlongPath` and read by the renderer.

Natural batch — promoting them separately would leave an awkward split
where half the movement state routes through SoA and half through the
POJO.

## Scope

### Primitive promotion (UnitRegistry + Unit)

- `float[] moveProgress` — decrementer-style like cooldownTimer.
- `float[] renderX`, `float[] renderY` — parallel arrays like cellX/cellY.
- `Unit.localMoveProgress`, `Unit.localRenderX`, `Unit.localRenderY` —
  transient seed/snapshot fields.
- Final accessors on Unit: `getMoveProgress()` / `setMoveProgress()`,
  `getRenderX()` / `setRenderX()`, `getRenderY()` / `setRenderY()`.
- Three tests per primitive on UnitRegistryTest (allocate-seed,
  release-snapshot, tail-swap) = 9 new tests.

### Consumer migration

~177 combined references across ~40 files. Major consumers:

- **`Unit.advanceAlongPath`** — primary writer of all three. This is the
  hot per-tick method. Writes `moveProgress`, then lerps `renderX`/`renderY`
  from current cell toward destination cell.
- **`BattleScreen`** rendering loops — reads `renderX`/`renderY` for
  sprite placement. ~10 read sites.
- **`FlybyOverlay`** — reads `renderX`/`renderY` for camera-relative
  positioning. ~15 read sites.
- **Weapon targeting** (`ShotEndpoint`, `InfantryWeapons`, `HeavyWeapons`)
  — reads render position for shot origin/target.
- **AI action classes** (~18 files) — many reset `renderX = cellX`,
  `renderY = cellY` on arrival. These become `setRenderX(getCellX())` etc.
- **`FireStance.of`** — reads `moveProgress` for stance determination.
- **`SquadStateDumper`** — reads `moveProgress` for debug output.

### Non-Unit types to leave alone

- Any `renderX`/`renderY` on non-Unit types (vehicles, turret mounts,
  air bodies) are separate fields — don't touch.

## Slicing

Could split into two slices if the consumer surface is too large for
one pass:

1. **Slice A: promotion + advanceAlongPath** — registry arrays, Unit
   accessors, tests, and the primary writer. Build must compile.
2. **Slice B: consumer sweep** — remaining ~170 read/write sites across
   AI actions, renderer, weapons, debug.

Or do it in one pass if the accessor rename is mechanical (which it
likely is — same pattern as cooldownTimer's 13-file sweep, just larger).

## Acceptance

- `gradlew.bat compileJava` clean.
- All tests pass.
- Three tests per primitive (9 total) on UnitRegistryTest.
- No direct field reads of `moveProgress`, `renderX`, `renderY` remain
  outside Unit's `local*` fields and registry's allocate/release.
