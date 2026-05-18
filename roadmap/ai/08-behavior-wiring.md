# 08 — GoapInfantryBehavior + dispatch flag

**Stage 1, task 8.** The integration point. Where the planner meets the
per-tick sim loop.

## Files added / modified

- ADD: `src/main/java/com/dillon/starsectormarines/battle/ai/GoapInfantryBehavior.java`
- MODIFY: `src/main/java/com/dillon/starsectormarines/battle/Squad.java` — add
  `SquadPlan currentPlan` field + `Unit primaryTarget` field (see
  [04-world-state-builder](04-world-state-builder.md) open question).
- MODIFY: `src/main/java/com/dillon/starsectormarines/battle/Unit.java` — add
  `Action currentAction` field (or look it up from squad.plan each tick;
  field is the fast path).
- MODIFY: `src/main/java/com/dillon/starsectormarines/battle/BattleSimulation.java` —
  add `public static boolean USE_GOAP_INFANTRY = false;`; in `behaviorFor`
  / `CombatantBehavior`, dispatch infantry to `GoapInfantryBehavior` when
  the flag is true; per-tick alert-update pass calls the planner for squads
  that need to replan.
- MODIFY: `src/main/java/com/dillon/starsectormarines/battle/ai/CombatantBehavior.java` —
  the dispatcher honors the flag for non-mech units.

## Per-tick flow

```
GoapInfantryBehavior.update(unit, sim):
  Squad squad = sim.getSquad(unit.squadId);
  if (squad == null) {
    InfantryCombatantBehavior.INSTANCE.update(unit, sim);   // solo units
    return;
  }
  if (squad.currentPlan == null || squad.currentPlan.isComplete()) {
    return;   // planner will (re)plan in the alert-update pass this tick or next
  }
  SquadPlan.Step step = squad.currentPlan.currentStep();
  if (!step.assignedMembers.contains(unit)) return;   // not my turn
  ActionStatus status = step.action.execute(unit, squad, sim);
  if (status == SUCCESS) squad.currentPlan.advance();
  else if (status == FAILURE) squad.currentPlan = null;   // triggers replan
```

## Planner cadence (in BattleSimulation alert-update pass)

```
for each squad:
  if needsReplan(squad):
    WorldState current = WorldStateBuilder.build(squad, sim);
    Goal goal = pickGoal(current, squad, sim);          // max relevance
    SquadPlan plan = Planner.plan(current, goal.desiredState(), ACTIONS, ...);
    if (plan != null) {
      assignMembers(plan, squad);                       // uses RoleAssigner
      squad.currentPlan = plan;
    }
```

**`needsReplan` triggers:**
- `squad.currentPlan == null`
- `squad.currentPlan.isComplete()`
- Squad member died this tick
- `SquadAlertLevel` transitioned this tick
- `squad.timeSinceReplan > 2.0f` (sim-seconds, periodic fallback)

## Acceptance

- With `USE_GOAP_INFANTRY = false`, behavior is identical to current — flag
  off is the safe default.
- With `USE_GOAP_INFANTRY = true`, infantry units route through GOAP and
  squad plans are visible in a debug log.
- Mechs continue routing to `MechCombatantBehavior` regardless of flag.

## Open questions

- Toggle mechanism for runtime A/B: a static flag is dev-only. Hotkey or
  mission-config later when we want playtest comparisons.
