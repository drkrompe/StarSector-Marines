# 07 — EliminateEnemies Goal

**Stage 1, task 7.** Single always-on goal sufficient for parity.

## Files added

- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/goals/EliminateEnemiesGoal.java`

## Goal

- **Name:** `ELIMINATE_ENEMIES`
- **Relevance:** `1.0` when `HAS_TARGET`, `0.1` otherwise (still in the running
  so the planner finds a no-target plan that reduces to maintain-cohesion).
- **DesiredState:** `ENEMY_DAMAGED = true`

The planner backward-chains: to satisfy `ENEMY_DAMAGED`, run `EngageVisible`;
preconditions need `HAS_LOS_TO_TARGET` + `IN_RANGE_OF_TARGET`; if those don't
hold in current state, run `MoveToFiringPosition` first; if `HAS_TARGET`
itself doesn't hold (e.g. all enemies dead), the goal isn't satisfiable and
the planner falls back to a lower-relevance goal — at Stage 1 there's only
the one goal, so the squad idles (which is what `InfantryCombatantBehavior`
does when `target == null` too).

## Acceptance

- With parity actions registered, planning from a snapshot where a squadmate
  has LOS to a target returns `[EngageVisible]`.
- Planning from a snapshot with `HAS_TARGET` but not `HAS_LOS_TO_TARGET`
  returns `[MoveToFiringPosition, EngageVisible]`.
- Planning from a snapshot where the squad is scattered + no LOS returns a
  plan including `MaintainCohesion`.

## Open questions

None for Stage 1. Stage 2+ adds `SurviveContact`, `SecurePosition`, etc.
