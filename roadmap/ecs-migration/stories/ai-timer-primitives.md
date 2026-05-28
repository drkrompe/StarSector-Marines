# Story: AI countdown-timer SoA promotion

## Context

The per-tick AI countdown timers are the last cluster of decrementer-
style floats still living on the `Unit` POJO. The keystone observation:
`repositionCooldown` is decremented in `InfantryUnitPrep.tickCooldowns`
on the line **directly below** the already-SoA `cooldownTimer` and
`secondaryCooldownTimer` drains — promoting it makes that hot loop touch
three SoA arrays instead of two-plus-one-POJO-field. Nearly free.

The break-contact group (`fallbackTimer` + `fallbackCellX` /
`fallbackCellY`) is the other half: a float countdown plus an int cell
pair (the `cellX/cellY` template), driven by `SquadFallbackSystem`,
`FallbackBehavior`, and `HitResponseService`'s fallback rolls.

`wanderDwellTimer` (FleeBehavior only, 1 file) is an optional ride-along.

## Scope

### Primitive promotion (UnitRegistry + Unit)

- `float[] repositionCooldown` — decrementer like cooldownTimer.
- `float[] fallbackTimer` — decrementer.
- `int[] fallbackCellX`, `int[] fallbackCellY` — parallel int pair like
  cellX/cellY (paired setter `setFallbackCell(x, y)`).
- *(optional)* `float[] wanderDwellTimer`.
- `Unit.local*` transient seed/snapshot fields + final accessors.
- Three tests per primitive: 12 tests for the four core arrays (15 with
  wanderDwell).

### Consumer migration

- **`InfantryUnitPrep.tickCooldowns`** — `repositionCooldown` drain
  (line 64); route through accessor or registry array.
- **`SquadFallbackSystem`** — reads `fallbackTimer` + fallback cells
  (arrival detection, trigger eval).
- **`FallbackBehavior` / GOAP `BreakContact` / `MechBreakContact` /
  `BreakLOS` / `RepositionToCover`** — read/write fallback + reposition
  state.
- **`BattleSimulation.writeFallbackInline`** (lines 585–589) — writes
  `fallbackCellX/Y` + `fallbackTimer` directly; route through setters.
- **`HitResponseService`** — fallback roll origin.
- **`WorldStateBuilder`** — reads timers for GOAP world state.

### Leave alone

- `PendingTargetMutation`'s own `fbX` / `fbY` payload fields (queued
  mutation record, not Unit storage).

## Slicing

1. **Slice A — `repositionCooldown`.** Trivial; one array, rides the
   existing `tickCooldowns` drain. 3 tests. Ship first.
2. **Slice B — fallback group** (`fallbackTimer` + `fallbackCellX/Y`,
   optional `wanderDwellTimer`). Larger consumer surface across the
   break-contact behaviors. 9–12 tests.

## Acceptance

- `gradlew.bat compileJava` clean; all tests pass.
- Three tests per promoted primitive on UnitRegistryTest.
- No direct field reads of the promoted timers remain outside Unit's
  `local*` fields and the registry's allocate/release.

## Priority

`repositionCooldown` (Slice A) is the cheapest promotion left — good
filler between bigger stories. Fallback group is medium churn.
