# 03 — Backward-Chaining A* Planner

**Stage 1, task 3.** The core algorithm. F.E.A.R.-style backward search over
plan-space.

## Files added

- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/Planner.java`

## Algorithm

Backward A* from the goal's `desiredState` toward the current `WorldState`,
expanding through actions whose effects satisfy at least one unsatisfied
predicate in the current search-node state.

**Node:** `(regressedState, planPrefix, gCost)`
- `regressedState` — what the world would need to look like for the prefix to
  still produce the goal. Start with `desiredState`; each action expanded
  regresses it by removing predicates the action's `effects` satisfy and
  adding the action's `preconditions`.
- `planPrefix` — the actions chosen so far, in reverse-execution order. On
  termination, reverse to get the execution order.
- `gCost` — accumulated cost.

**Termination:** when `currentState.satisfies(regressedState)` — the world
already satisfies what the prefix needs.

**Heuristic (h):** count of predicates in `regressedState` not satisfied by
`currentState`. Admissible.

**Returns:** `SquadPlan` (with empty member assignments — assignment is the
caller's job), or `null` if no plan reachable within the search limit.

## API

```
public final class Planner {
    public static SquadPlan plan(
        WorldState current,
        WorldState goal,
        List<Action> available,
        Squad squad,
        BattleSimulation sim,
        int nodeLimit       // safety cap; 256 is plenty for our action library
    );
}
```

## Performance notes

- We replan rarely (a handful per sim-second across all squads), so micro-opt
  is unnecessary at Stage 1.
- WorldState `equals`/`hashCode` lets the open/closed sets dedupe.
- A `nodeLimit` cap returns null instead of hanging if the action set is
  pathological.

## Acceptance

- Given a trivial action library and goal, returns a plan whose chained
  effects satisfy the goal from the current state.
- Returns null when no plan exists (no action produces a missing predicate).
- A simple unit-test-style smoke run in a `main` (or temporary test scaffold)
  before integration.

## Open questions

- Heuristic could weight by predicate "importance" later. Not needed for
  Stage 1.
