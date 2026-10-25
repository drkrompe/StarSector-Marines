package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryRetryAllowanceTest {

    private static final class Purse implements StoryRetryAllowance.StoryPoints {
        int points;
        int spent;

        Purse(int points) {
            this.points = points;
        }

        @Override public int available() {
            return points;
        }

        @Override public void spend(int amount, String logText) {
            points -= amount;
            spent += amount;
        }
    }

    @Test
    void firstRetryIsFreeThenEachCostsAStoryPoint() {
        Purse purse = new Purse(1);
        StoryRetryAllowance allowance = new StoryRetryAllowance(purse);

        assertEquals(StoryRetryAllowance.Cost.FREE, allowance.cost());
        assertTrue(allowance.charge("retry"));
        assertEquals(0, purse.spent);

        assertEquals(StoryRetryAllowance.Cost.STORY_POINT, allowance.cost());
        assertTrue(allowance.charge("retry"));
        assertEquals(1, purse.spent);

        assertEquals(StoryRetryAllowance.Cost.UNAVAILABLE, allowance.cost());
        assertFalse(allowance.charge("retry"));
        assertEquals(1, purse.spent);
    }

    @Test
    void missionSelectReturnsTheFreeRetry() {
        Purse purse = new Purse(0);
        StoryRetryAllowance allowance = new StoryRetryAllowance(purse);
        assertTrue(allowance.charge("retry"));
        assertEquals(StoryRetryAllowance.Cost.UNAVAILABLE, allowance.cost());

        allowance.reset();

        assertEquals(StoryRetryAllowance.Cost.FREE, allowance.cost());
    }
}
