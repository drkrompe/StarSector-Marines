package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatriarchRunRulesTest {

    private static final float REACH = SwarmMatriarchBehavior.REACH;

    @Test
    void runsOnlyAtQuarryOnFootInTheBandOverOpenGroundOffCooldown() {
        float mid = (REACH + MatriarchRunRules.BAND) / 2f;
        assertTrue(MatriarchRunRules.shouldRun(mid, REACH, true, true, 100, 100));
        assertFalse(MatriarchRunRules.shouldRun(REACH, REACH, true, true, 100, 0), "in reach: strike");
        assertFalse(MatriarchRunRules.shouldRun(MatriarchRunRules.BAND + .1f, REACH, true, true, 100, 0),
                "too far: walk");
        assertFalse(MatriarchRunRules.shouldRun(mid, REACH, false, true, 100, 0), "a hull or a mech is walked down");
        assertFalse(MatriarchRunRules.shouldRun(mid, REACH, true, false, 100, 0), "not open ground");
        assertFalse(MatriarchRunRules.shouldRun(mid, REACH, true, true, 99, 100), "cooldown running");
    }

    @Test
    void aRunEndsOnItsTimeAnotherQuarryOrClosingToStrike() {
        float far = MatriarchRunRules.STRIKE_GAP + 1f;
        assertFalse(MatriarchRunRules.runOver(MatriarchRunRules.RUN - .1f, true, far));
        assertTrue(MatriarchRunRules.runOver(MatriarchRunRules.RUN, true, far), "ran its time");
        assertTrue(MatriarchRunRules.runOver(0f, false, far), "another quarry");
        assertTrue(MatriarchRunRules.runOver(0f, true, MatriarchRunRules.STRIKE_GAP), "closed to strike");
    }

    @Test
    void runningSheClosesInBeforeSheReaps() {
        assertEquals(REACH, MatriarchRunRules.strikeGap(false, REACH));
        assertTrue(MatriarchRunRules.strikeGap(true, REACH) < REACH);
        // never further than the reach she walks with
        assertEquals(.5f, MatriarchRunRules.strikeGap(true, .5f));
    }
}
