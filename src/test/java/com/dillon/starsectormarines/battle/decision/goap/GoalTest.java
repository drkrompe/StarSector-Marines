package com.dillon.starsectormarines.battle.decision.goap;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.infantry.EliminateEnemiesGoal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
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

    @Test
    public void orderedEvaluationSkipsLowerBucketsButScoresEveryMissionContender() {
        withPriorityOrder(true, () -> {
            List<String> evaluated = new ArrayList<>();
            Goal engagement = stubGoal("engagement", Goal.Priority.ENGAGEMENT, 1f);
            Goal first = stubGoal("first", Goal.Priority.MISSION, 0.2f);
            Goal winner = stubGoal("winner", Goal.Priority.MISSION, 0.8f);
            Goal tied = stubGoal("tied", Goal.Priority.MISSION, 0.8f);
            Goal survival = stubGoal("survival", Goal.Priority.SURVIVAL, 1f);
            Goal.EvaluationContext context = contextRecording(evaluated);

            assertSame(winner, Goal.pickMostRelevantPrepared(
                    List.of(engagement, first, winner, survival, tied), context, Set.of()).goal());
            assertEquals(List.of("first", "winner", "tied"), evaluated,
                    "registration order breaks ties; lower categories do no work");
        });
    }

    @Test
    public void declinedWinnersDescendLazilyWithoutRepeatingAnyEvaluation() {
        withPriorityOrder(true, () -> {
            List<String> evaluated = new ArrayList<>();
            Goal mission = stubGoal("mission", Goal.Priority.MISSION, 1f);
            Goal sibling = stubGoal("sibling", Goal.Priority.MISSION, 0.5f);
            Goal disabled = stubGoal("disabled", Goal.Priority.SURVIVAL, 0f);
            Goal engagement = stubGoal("engagement", Goal.Priority.ENGAGEMENT, 1f);
            Goal idle = stubGoal("idle", Goal.Priority.IDLE, 1f);
            List<Goal> goals = List.of(idle, engagement, disabled, sibling, mission);
            Goal.EvaluationContext context = contextRecording(evaluated);
            Set<Goal> declined = new HashSet<>();
            for (Goal expected : List.of(mission, sibling, engagement, idle)) {
                assertSame(expected, Goal.pickMostRelevantPrepared(goals, context, declined).goal());
                declined.add(expected);
                if (expected == sibling) assertEquals(List.of("sibling", "mission"), evaluated);
            }
            assertNull(Goal.pickMostRelevantPrepared(goals, context, declined));
            assertEquals(List.of("sibling", "mission", "disabled", "engagement", "idle"), evaluated);
        });
    }

    @Test
    public void eagerControlStillEvaluatesAllGoalsInRegistrationOrder() {
        withPriorityOrder(false, () -> {
            List<String> evaluated = new ArrayList<>();
            Goal engagement = stubGoal("engagement", Goal.Priority.ENGAGEMENT, 1f);
            Goal mission = stubGoal("mission", Goal.Priority.MISSION, 0.1f);
            Goal idle = stubGoal("idle", Goal.Priority.IDLE, 1f);
            assertSame(mission, Goal.pickMostRelevantPrepared(List.of(engagement, mission, idle),
                    contextRecording(evaluated), Set.of()).goal());
            assertEquals(List.of("engagement", "mission", "idle"), evaluated);
        });
    }

    @Test
    public void orderedAndEagerModesChooseTheSameCompleteFallbackLadder() {
        Goal firstTie = stubGoal("firstTie", Goal.Priority.ENGAGEMENT, 0.8f);
        Goal secondTie = stubGoal("secondTie", Goal.Priority.ENGAGEMENT, 0.8f);
        Goal survival = stubGoal("survival", Goal.Priority.SURVIVAL, 0.1f);
        Goal mission = stubGoal("mission", Goal.Priority.MISSION, 0.01f);
        Goal disabled = stubGoal("disabled", Goal.Priority.MISSION, -1f);
        Goal idle = stubGoal("idle", Goal.Priority.IDLE, 1f);
        List<Goal> goals = List.of(firstTie, idle, survival, disabled, secondTie, mission);
        List<Goal> expected = List.of(mission, survival, firstTie, secondTie, idle);
        for (boolean ordered : List.of(false, true)) {
            withPriorityOrder(ordered, () -> {
                Goal.EvaluationContext context = contextRecording(new ArrayList<>());
                Set<Goal> declined = new HashSet<>();
                for (Goal next : expected) {
                    assertSame(next, Goal.pickMostRelevantPrepared(goals, context, declined).goal());
                    declined.add(next);
                }
                assertNull(Goal.pickMostRelevantPrepared(goals, context, declined));
            });
        }
    }

    @Test
    public void explicitLowerPriorityDependencyKeepsItsPreparedPlanForFallback() {
        withPriorityOrder(true, () -> {
            List<String> evaluated = new ArrayList<>();
            SquadPlan prepared = new SquadPlan(List.of());
            Goal dependency = new Goal() {
                @Override public String name() { return "dependency"; }
                @Override public float relevance(WorldState s, Squad sq, BattleView sim) {
                    throw new AssertionError("use the combined evaluation");
                }
                @Override public Evaluation evaluate(WorldState s, Squad sq,
                                                     BattleView sim, EvaluationContext context) {
                    return new Evaluation(1f, prepared);
                }
                @Override public WorldState desiredState(Squad sq, BattleView sim) {
                    return WorldState.EMPTY;
                }
            };
            Goal mission = new Goal() {
                @Override public String name() { return "mission"; }
                @Override public Priority priority() { return Priority.MISSION; }
                @Override public float relevance(WorldState s, Squad sq, BattleView sim) { return 1f; }
                @Override public Evaluation evaluate(WorldState s, Squad sq,
                                                     BattleView sim, EvaluationContext context) {
                    return new Evaluation(context.evaluate(dependency).relevance(), null);
                }
                @Override public WorldState desiredState(Squad sq, BattleView sim) {
                    return WorldState.EMPTY;
                }
            };
            List<Goal> goals = List.of(dependency, mission);
            Goal.EvaluationContext context = contextRecording(evaluated);
            assertSame(mission, Goal.pickMostRelevantPrepared(goals, context, Set.of()).goal());
            Goal.Choice fallback = Goal.pickMostRelevantPrepared(goals, context, Set.of(mission));
            assertSame(dependency, fallback.goal());
            assertSame(prepared, dependency.customPlan(fallback.evaluation(), null, null));
            assertEquals(List.of("dependency", "mission"), evaluated);
        });
    }

    // --- stubs -----------------------------------------------------------

    private static Goal.EvaluationContext contextRecording(List<String> evaluated) {
        return new Goal.EvaluationContext(WorldState.EMPTY, null, null,
                (goal, nanos) -> evaluated.add(goal.name()));
    }

    private static void withPriorityOrder(boolean enabled, Runnable assertion) {
        String property = "battle.goap.priorityOrderedEvaluation";
        String previous = System.getProperty(property);
        System.setProperty(property, Boolean.toString(enabled));
        try {
            assertion.run();
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private static Goal stubGoal(String name, Goal.Priority priority, float relevance) {
        return new Goal() {
            @Override public String name() { return name; }
            @Override public Goal.Priority priority() { return priority; }
            @Override public float relevance(WorldState s, Squad sq, BattleView sim) { return relevance; }
            @Override public WorldState desiredState(Squad sq, BattleView sim) { return WorldState.EMPTY; }
        };
    }
}
