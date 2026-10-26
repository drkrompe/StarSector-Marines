package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a hull's way breaks, and what breaking it leaves of its way. */
class HullMomentumTest {

    @Test
    void aHullBreaksWithTheSquareOfItsSpeedAndInProportionToItsMass() {
        assertEquals(0f, HullMomentum.structure(12f, 0f));
        assertEquals(0f, HullMomentum.structure(12f, -2f), "backing into something is not driving into it");
        assertEquals(4f * HullMomentum.structure(12f, 1f), HullMomentum.structure(12f, 2f), 1e-3f);
        assertEquals(3f * HullMomentum.structure(12f, 2f), HullMomentum.structure(36f, 2f), 1e-3f);
    }

    @Test
    void breakingWhatItCarriesStopsItAndBreakingLessSlowsItInProportion() {
        float all = HullMomentum.structure(12f, 2.4f);
        assertEquals(0f, HullMomentum.speedAfter(12f, 2.4f, all), 1e-4f);
        assertEquals(0f, HullMomentum.speedAfter(12f, 2.4f, all * 2f), "more than it carries stops it");
        assertEquals(2.4f * (float) Math.sqrt(.75f), HullMomentum.speedAfter(12f, 2.4f, all / 4f), 1e-4f);
        assertEquals(2.4f, HullMomentum.speedAfter(12f, 2.4f, 0f), 1e-6f);
    }

    @Test
    void theHeavierHullIsCheckedLessByTheSameObstacle() {
        float apc = HullMomentum.speedAfter(12f, 2.8f, 120f), tank = HullMomentum.speedAfter(45f, 2.8f, 120f);
        assertTrue(tank > apc && apc > 0f, "tank " + tank + ", apc " + apc);
    }
}
