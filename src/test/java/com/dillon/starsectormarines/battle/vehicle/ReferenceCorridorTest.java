package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceCorridorTest {

    @Test
    void rollingGoalFacesAlongTheSegmentContainingTheGoal() {
        ReferenceCorridor corridor = new ReferenceCorridor(
                new float[]{10.5f, 10.5f, 20.5f},
                new float[]{5.5f, 10.5f, 10.5f},
                1);

        Pose goal = corridor.targetAhead(10.5f, 7.5f, 8f);

        assertEquals(270f, normalize(goal.facingDeg), 0.01f,
                "a goal on the eastbound leg must carry the eastbound tangent, not the diagonal body-to-goal bearing");
    }

    private static float normalize(float degrees) {
        return ((degrees % 360f) + 360f) % 360f;
    }
}
