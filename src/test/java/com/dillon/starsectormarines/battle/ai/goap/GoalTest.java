package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.ai.goap.goals.EliminateEnemiesGoal;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    // --- stubs -----------------------------------------------------------

    private static Goal stubGoal(String name, Goal.Priority priority, float relevance) {
        return new Goal() {
            @Override public String name() { return name; }
            @Override public Goal.Priority priority() { return priority; }
            @Override public float relevance(WorldState s, Squad sq, BattleSimulation sim) { return relevance; }
            @Override public WorldState desiredState(Squad sq, BattleSimulation sim) { return WorldState.EMPTY; }
        };
    }
}
