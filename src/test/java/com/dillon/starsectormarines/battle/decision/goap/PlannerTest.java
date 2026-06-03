package com.dillon.starsectormarines.battle.decision.goap;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.Entity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standalone planner shape tests — pure goal-and-action math, no sim.
 * Each test uses tiny stub actions whose preconditions/effects are crafted
 * to exercise one planner property.
 */
public class PlannerTest {

    @Test
    public void emptyPlanWhenGoalAlreadySatisfied() {
        WorldState current = WorldState.EMPTY.with(Predicate.ENEMY_DAMAGED, true);
        WorldState goal    = WorldState.EMPTY.with(Predicate.ENEMY_DAMAGED, true);
        SquadPlan plan = Planner.plan(current, goal, List.of(stubFire()), null, null, 64);
        assertNotNull(plan, "trivially-satisfied goal returns empty plan, not null");
        assertEquals(0, plan.stepCount());
    }

    @Test
    public void singleStepWhenOneActionSatisfiesGoal() {
        WorldState current = WorldState.EMPTY
                .with(Predicate.HAS_LOS_TO_TARGET, true)
                .with(Predicate.IN_RANGE_OF_TARGET, true);
        WorldState goal = WorldState.EMPTY.with(Predicate.ENEMY_DAMAGED, true);
        SquadPlan plan = Planner.plan(current, goal, List.of(stubFire()), null, null, 64);
        assertNotNull(plan);
        assertEquals(1, plan.stepCount());
        assertEquals("Fire", plan.steps().get(0).action.name());
    }

    @Test
    public void twoStepChainsMoveBeforeFire() {
        // Current has a target but no LOS/range. Plan should be [Move, Fire].
        WorldState current = WorldState.EMPTY.with(Predicate.HAS_TARGET, true);
        WorldState goal    = WorldState.EMPTY.with(Predicate.ENEMY_DAMAGED, true);
        SquadPlan plan = Planner.plan(current, goal, List.of(stubMove(), stubFire()), null, null, 64);
        assertNotNull(plan);
        assertEquals(2, plan.stepCount());
        assertEquals("Move", plan.steps().get(0).action.name());
        assertEquals("Fire", plan.steps().get(1).action.name());
    }

    @Test
    public void returnsNullWhenGoalUnreachable() {
        // No action produces ENEMY_DAMAGED.
        WorldState current = WorldState.EMPTY.with(Predicate.HAS_TARGET, true);
        WorldState goal    = WorldState.EMPTY.with(Predicate.ENEMY_DAMAGED, true);
        SquadPlan plan = Planner.plan(current, goal, List.of(stubMove()), null, null, 64);
        assertNull(plan);
    }

    @Test
    public void rejectsConflictingAction() {
        // BadFire SETS ENEMY_DAMAGED=false. Goal wants true. Even though they
        // share the predicate, the conflict should make the action inapplicable.
        WorldState current = WorldState.EMPTY
                .with(Predicate.HAS_LOS_TO_TARGET, true)
                .with(Predicate.IN_RANGE_OF_TARGET, true);
        WorldState goal = WorldState.EMPTY.with(Predicate.ENEMY_DAMAGED, true);
        SquadPlan plan = Planner.plan(current, goal, List.of(stubBadFire()), null, null, 64);
        assertNull(plan, "conflicting effect should not satisfy goal");
    }

    @Test
    public void cohesionPlanFromScatteredState() {
        // Squad is scattered; goal is to rejoin. Single-action plan via Cohere.
        WorldState current = WorldState.EMPTY;   // unspecified WITHIN_COHESION_RADIUS reads as false
        WorldState goal    = WorldState.EMPTY.with(Predicate.WITHIN_COHESION_RADIUS, true);
        SquadPlan plan = Planner.plan(current, goal, List.of(stubCohere()), null, null, 64);
        assertNotNull(plan);
        assertEquals(1, plan.stepCount());
        assertEquals("Cohere", plan.steps().get(0).action.name());
    }

    // --- stub actions ----------------------------------------------------

    private static Action stubFire() {
        return stub("Fire",
                WorldState.EMPTY
                        .with(Predicate.HAS_LOS_TO_TARGET, true)
                        .with(Predicate.IN_RANGE_OF_TARGET, true),
                WorldState.EMPTY.with(Predicate.ENEMY_DAMAGED, true),
                1f);
    }

    private static Action stubMove() {
        return stub("Move",
                WorldState.EMPTY.with(Predicate.HAS_TARGET, true),
                WorldState.EMPTY
                        .with(Predicate.HAS_LOS_TO_TARGET, true)
                        .with(Predicate.IN_RANGE_OF_TARGET, true),
                2f);
    }

    private static Action stubCohere() {
        return stub("Cohere",
                WorldState.EMPTY,
                WorldState.EMPTY.with(Predicate.WITHIN_COHESION_RADIUS, true),
                3f);
    }

    private static Action stubBadFire() {
        return stub("BadFire",
                WorldState.EMPTY
                        .with(Predicate.HAS_LOS_TO_TARGET, true)
                        .with(Predicate.IN_RANGE_OF_TARGET, true),
                WorldState.EMPTY.with(Predicate.ENEMY_DAMAGED, false),
                1f);
    }

    private static Action stub(String name, WorldState pre, WorldState eff, float cost) {
        return new Action() {
            @Override public String name() { return name; }
            @Override public WorldState preconditions() { return pre; }
            @Override public WorldState effects() { return eff; }
            @Override public float cost(WorldState s, Squad sq, BattleView sim) { return cost; }
            @Override public int requiredMembers() { return 1; }
            @Override public ActionStatus execute(Entity m, Squad sq, BattleControl sim) {
                return ActionStatus.SUCCESS;
            }
        };
    }
}
