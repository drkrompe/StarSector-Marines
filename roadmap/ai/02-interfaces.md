# 02 — Action / Goal / ActionStatus / SquadPlan

**Stage 1, task 2.** The contracts the planner and behavior layer code against.

## Files added

- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/Action.java`
- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/Goal.java`
- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/ActionStatus.java`
- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/SquadPlan.java`

## `Action`

```
String name()
WorldState preconditions()                  // static, immutable
WorldState effects()                        // static, immutable
float cost(WorldState state, Squad squad, BattleSimulation sim)
int requiredMembers()                       // 1 for solo actions; >1 for coordinated
ActionStatus execute(Unit member, Squad squad, BattleSimulation sim)
```

`preconditions` / `effects` are static for the planner. `cost` is dynamic
(so an action can be cheaper when conditions favor it — e.g.
`MoveToFiringPosition` is cheaper when a cover-rich cell exists nearby).
`execute` runs one tick of the action for one assigned member.

## `Goal`

```
String name()
float relevance(WorldState state, Squad squad, BattleSimulation sim)
WorldState desiredState(Squad squad, BattleSimulation sim)
```

`relevance` is a priority score; the squad's planner picks the goal with the
highest relevance whose plan is reachable. `desiredState` is what the planner
backward-chains from.

## `ActionStatus`

```
enum ActionStatus { RUNNING, SUCCESS, FAILURE }
```

Returned from `Action.execute` each tick. `SUCCESS` advances the plan;
`FAILURE` triggers replan.

## `SquadPlan`

```
SquadPlan(List<Step> steps)
record Step(Action action, List<Unit> assignedMembers)
Step currentStep()
void advance()
boolean isComplete()
```

Lives on `Squad` as a new field. Created by the planner, advanced by
`GoapInfantryBehavior`, invalidated on replan triggers.

## Acceptance

- Interfaces compile.
- A stub `Action` and `Goal` instance can be constructed.
- `SquadPlan.advance` moves the index; `isComplete` returns true at the end.

## Open questions

- Does `Action.execute` need access to the full plan (e.g. to peek at the next
  action)? Defer until a real action wants it.
