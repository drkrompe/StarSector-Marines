package com.dillon.starsectormarines.battle.command.allied;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefusedGoalsTest {

    @Test
    void remembersAGoalUntilTheMemoryAgesOutButKeepsTheRetryCount() {
        RefusedGoals goals = new RefusedGoals(10f, 10f, 0, 1.5f, 100);
        goals.remember(30, 40);
        goals.stallRetried();

        goals.age(100);
        assertTrue(goals.refused(30, 40));
        assertFalse(goals.refused(30, 41));

        goals.age(101);
        assertFalse(goals.refused(30, 40), "the ground may have changed; the goal is asked once more");
        assertEquals(40, goals.stallWait(20, 3), "a hull still standing where it stalled waits longer");
    }

    @Test
    void aMoveIsWhatForgetsAndTheBackoffIsCapped() {
        RefusedGoals goals = new RefusedGoals(10f, 10f, 0, 1.5f, 100);
        assertFalse(goals.movedAway(11f, 10.5f));
        assertTrue(goals.movedAway(12f, 10f));

        for (int i = 0; i < 10; i++) goals.stallRetried();
        assertEquals(160, goals.stallWait(20, 3));
    }
}
