# Neutral Battle Timestep

Status: PROPOSED

Written: 2026-08-24

Read `ecs-nouns.md` before implementing this story.

## Problem

The shared 30 Hz fixed step is declared as `BattleSimulation.TICK_DT`, so
otherwise independent combat, AI, appearance, infantry, mech, turret,
evacuation, presentation, bridge, and test code import the battle coordinator
only for a literal. That dependency weakens the existing `BattleView` and
`BattleControl` boundary without representing behavioral authority.

## Goal

Give the fixed simulation interval one coordinator-independent value contract,
`BattleTimestep` in `battle.sim`. `BattleSimulation` remains the tick
coordinator and consumes that contract; the new type does not become a service,
clock, scheduler, or addition to either narrow battle facade.

## Scope

- Add `BattleTimestep` with the existing `1f / 30f` fixed-step value.
- Repoint every production and test use of `BattleSimulation.TICK_DT`, including
  the coordinator's own fixed-step loop.
- Remove the old public constant and imports made unnecessary solely by it.
- Update nearby Javadoc or comments that name the old owner.

## Acceptance

- `BattleSimulation.TICK_DT` no longer exists or is referenced in source or
  tests.
- `BattleTimestep` is the only owner of the fixed 30 Hz interval; the migration
  introduces no duplicate `1f / 30f` value.
- Existing elapsed-time, cooldown, cadence, tick-index, determinism, and
  behavior tests retain their prior semantics.
- Consumers that imported `BattleSimulation` only for the timestep no longer
  depend on the coordinator; genuine coordinator dependencies remain intact.
- Neither `BattleView` nor `BattleControl` gains an API, and tick ordering or
  scheduling behavior does not change.

## Authority boundary

ECS owns battle composition and phase-boundary cleanup. `BattleTimestep` owns
only the fixed interval value: it does not choose tick order, advance time,
expose simulation state, or let consumers bypass their appropriate
`BattleView` or `BattleControl` contract.

## Out of scope

- Variable timesteps, pause or speed policy, tick ordering, parallelism, and
  simulation-loop refactoring.
- Narrowing unrelated existing `BattleSimulation` dependencies.
- Changing combat, movement, cooldown, AI, presentation, or bridge behavior.

## Dependencies

None. This is a mechanical, behavior-preserving cleanup independent of the
remaining ECS presentation and acceptance stories.
