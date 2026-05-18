# 04 — WorldStateBuilder

**Stage 1, task 4.** Snapshots the sim into a `WorldState` for the planner.

## Files added

- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/world/WorldStateBuilder.java`
- `src/main/java/com/dillon/starsectormarines/battle/ai/goap/world/PredicateEvaluator.java`

## Design

One `PredicateEvaluator` per `Predicate`. Registered in a static table at
class init. The builder iterates registered predicates, calls each evaluator
against the (squad, sim) context, and stuffs the result into a fresh
`WorldState`.

**Why per-predicate evaluators rather than a god-method:** adding a new
predicate = adding one enum entry + one evaluator. The planner doesn't
care; existing actions/goals don't care.

```
@FunctionalInterface
public interface PredicateEvaluator {
    boolean evaluate(Squad squad, BattleSimulation sim);
}
```

```
public final class WorldStateBuilder {
    public static WorldState build(Squad squad, BattleSimulation sim);
}
```

## Stage 1 predicate evaluators

- `HAS_TARGET` — `findBestTarget` on any alive squadmate returns non-null.
- `HAS_LOS_TO_TARGET` — given the squad's chosen primary target (squad's
  `lastSeenEnemy` or freshly-picked), at least one squadmate has LOS.
- `IN_RANGE_OF_TARGET` — same, distance check.
- `WITHIN_COHESION_RADIUS` — at least one squadmate within 12 of centroid.
  (Stage 1 cohesion is per-squadmate, not squad-wide; this predicate is a
  squad-wide proxy.)
- `ENEMY_DAMAGED` — false by default. Set true by `EngageVisible.effects`.
  This is a "goal-satisfaction" predicate, not a sim observation.

## Acceptance

- Building a WorldState for a freshly-spawned, idle squad with enemies in
  sight produces predicates matching hand-inspection.
- Building twice in a row from an unchanged sim produces equal WorldStates.

## Open questions

- Does the squad need a sticky "primary target" field so LOS/range predicates
  evaluate consistently across the planner search? Probably yes — add
  `Squad.primaryTarget` and refresh it once per replan, before the build.
