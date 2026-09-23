package com.dillon.starsectormarines.battle.decision.goap;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.infantry.EliminateEnemiesGoal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Goal-priority bucket selection tests — pure goal math, no sim.
 * Verifies that {@link Goal#pickMostRelevant} respects {@link Goal.Priority}
 * buckets first and {@link Goal#relevance} only as a within-bucket tiebreaker.
 */
public class GoalTest {

    @Test
    public void defaultPriorityIsEngagement() {
        assertEquals(Goal.Priority.ENGAGEMENT, EliminateEnemiesGoal.INSTANCE.priority());
    }

    @Test
    public void priorityOutranksRelevance() {
        Goal mission   = stubGoal("Mission",    Goal.Priority.MISSION,    0.3f);
        Goal engagement = stubGoal("Engagement", Goal.Priority.ENGAGEMENT, 1.0f);
        Goal picked = Goal.pickMostRelevant(
                List.of(engagement, mission), WorldState.EMPTY, null, null);
        assertSame(mission, picked, "MISSION bucket must beat ENGAGEMENT even with lower relevance");
    }

    @Test
    public void withinBucketRelevanceWins() {
        Goal low  = stubGoal("Low",  Goal.Priority.ENGAGEMENT, 0.5f);
        Goal high = stubGoal("High", Goal.Priority.ENGAGEMENT, 0.9f);
        Goal picked = Goal.pickMostRelevant(
                List.of(low, high), WorldState.EMPTY, null, null);
        assertSame(high, picked, "within a single bucket, higher relevance wins");
    }

    @Test
    public void zeroRelevanceIsExcludedEvenForMission() {
        Goal disabledMission = stubGoal("DisabledMission", Goal.Priority.MISSION, 0f);
        Goal picked = Goal.pickMostRelevant(
                List.of(disabledMission), WorldState.EMPTY, null, null);
        assertNull(picked, "relevance <= 0 disables the goal regardless of priority bucket");
    }

    @Test
    public void emptyAndAllZeroReturnNull() {
        assertNull(Goal.pickMostRelevant(List.of(), WorldState.EMPTY, null, null),
                "no goals → null");
        Goal a = stubGoal("A", Goal.Priority.ENGAGEMENT, 0f);
        Goal b = stubGoal("B", Goal.Priority.SURVIVAL, -0.5f);
        assertNull(Goal.pickMostRelevant(List.of(a, b), WorldState.EMPTY, null, null),
                "all goals at or below zero relevance → null");
    }

    @Test
    public void survivalBeatsEngagementButMissionBeatsSurvival() {
        Goal engagement = stubGoal("E", Goal.Priority.ENGAGEMENT, 1.0f);
        Goal survival   = stubGoal("S", Goal.Priority.SURVIVAL,   0.4f);
        Goal mission    = stubGoal("M", Goal.Priority.MISSION,    0.2f);

        // Survival vs engagement.
        assertSame(survival, Goal.pickMostRelevant(
                List.of(engagement, survival), WorldState.EMPTY, null, null));
        // Mission trumps both.
        assertSame(mission, Goal.pickMostRelevant(
                List.of(engagement, survival, mission), WorldState.EMPTY, null, null));
    }

    @Test
    public void declinedGoalYieldsToTheNextBucketDown() {
        Goal mission    = stubGoal("M", Goal.Priority.MISSION,    0.8f);
        Goal engagement = stubGoal("E", Goal.Priority.ENGAGEMENT, 1.0f);
        Goal floor      = stubGoal("F", Goal.Priority.IDLE,       1.0f);
        List<Goal> all = List.of(mission, engagement, floor);

        assertSame(mission, Goal.pickMostRelevant(all, WorldState.EMPTY, null, null, Set.of()));
        assertSame(engagement, Goal.pickMostRelevant(
                all, WorldState.EMPTY, null, null, Set.of(mission)),
                "a declined MISSION goal must not block the ENGAGEMENT bucket");
        assertSame(floor, Goal.pickMostRelevant(
                all, WorldState.EMPTY, null, null, Set.of(mission, engagement)),
                "the IDLE floor is reachable once the buckets above it decline");
        assertNull(Goal.pickMostRelevant(
                all, WorldState.EMPTY, null, null, Set.of(mission, engagement, floor)),
                "every goal declined → null, the genuinely-idle case");
    }

    @Test
    public void decliningOneGoalLeavesItsBucketSiblingInContention() {
        Goal preferred = stubGoal("P", Goal.Priority.ENGAGEMENT, 1.0f);
        Goal sibling   = stubGoal("S", Goal.Priority.ENGAGEMENT, 0.5f);
        assertSame(sibling, Goal.pickMostRelevant(
                List.of(preferred, sibling), WorldState.EMPTY, null, null, Set.of(preferred)),
                "declining the bucket winner promotes the runner-up, not the next bucket");
    }

    @Test
    public void fallbackReusesGoalEvaluationsWithinOneReplan() {
        int[] evaluations = {0};
        Goal expensive = new Goal() {
            @Override public String name() { return "expensive"; }
            @Override public Priority priority() { return Priority.MISSION; }
            @Override public float relevance(WorldState s, Squad sq, BattleView sim) {
                evaluations[0]++;
                return 1f;
            }
            @Override public WorldState desiredState(Squad sq, BattleView sim) {
                return WorldState.EMPTY;
            }
        };
        Goal fallback = stubGoal("fallback", Goal.Priority.ENGAGEMENT, 1f);
        Goal.EvaluationContext context = new Goal.EvaluationContext(
                WorldState.EMPTY, null, null, null);
        assertSame(expensive, Goal.pickMostRelevantPrepared(
                List.of(expensive, fallback), context, Set.of()).goal());
        assertSame(fallback, Goal.pickMostRelevantPrepared(
                List.of(expensive, fallback), context, Set.of(expensive)).goal());
        assertEquals(1, evaluations[0]);
    }

    @Test
    public void preparedPlanIsPublishedWithoutRunningCustomPlanAgain() {
        SquadPlan prepared = new SquadPlan(List.of());
        Goal goal = new Goal() {
            @Override public String name() { return "prepared"; }
            @Override public float relevance(WorldState s, Squad sq, BattleView sim) {
                return 1f;
            }
            @Override public Evaluation evaluate(WorldState s, Squad sq,
                                                 BattleView sim, EvaluationContext context) {
                return new Evaluation(1f, prepared);
            }
            @Override public SquadPlan customPlan(Squad sq, BattleView sim) {
                throw new AssertionError("prepared plan must be reused");
            }
            @Override public WorldState desiredState(Squad sq, BattleView sim) {
                return WorldState.EMPTY;
            }
        };
        Goal.EvaluationContext context = new Goal.EvaluationContext(
                WorldState.EMPTY, null, null, null);
        Goal.Choice choice = Goal.pickMostRelevantPrepared(
                List.of(goal), context, Set.of());
        assertSame(prepared, goal.customPlan(choice.evaluation(), null, null));
    }

    // --- stubs -----------------------------------------------------------

    private static Goal stubGoal(String name, Goal.Priority priority, float relevance) {
        return new Goal() {
            @Override public String name() { return name; }
            @Override public Goal.Priority priority() { return priority; }
            @Override public float relevance(WorldState s, Squad sq, BattleView sim) { return relevance; }
            @Override public WorldState desiredState(Squad sq, BattleView sim) { return WorldState.EMPTY; }
        };
    }
}
