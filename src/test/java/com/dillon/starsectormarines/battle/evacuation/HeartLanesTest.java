package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.HeartLane;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartLanesTest {

    @Test
    void aLaneIsStalledByPushesInARowAndAClearedContactEndsTheRun() {
        int run = 0;
        run = HeartLanes.pushesAfter(run, "lane-2.contact_1");
        run = HeartLanes.pushesAfter(run, "lane-2.pushed_1");
        run = HeartLanes.pushesAfter(run, "lane-2.contact_2");
        run = HeartLanes.pushesAfter(run, "lane-2.pushed_2");
        assertEquals(HeartLanes.STALL_PUSHES, run);
        assertEquals(0, HeartLanes.pushesAfter(run, "lane-2.cleared_3"));
        assertEquals(0, HeartLanes.pushesAfter(run, "lane-2.rallied"));
    }

    @Test
    void theCompanyIsInALanesGapInsideItsBand() {
        HeartLane lane = new HeartLane(true, 100, 20);
        assertTrue(HeartLanes.inBand(lane, 115, 40));
        assertFalse(HeartLanes.inBand(lane, 125, 40));
    }

    @Test
    void onlyANeighbourAcrossTheFrontSendsAWaveToAGap() {
        // Broad Front's four lanes at x 84, 184, 370, 475, and the company's support ground at 280 between the middle two.
        int[] across = {84, 184, 370, 475, 280};
        boolean[] waves = {true, true, true, true, false};
        assertTrue(HeartLanes.neighbours(across, waves, 1, 2), "the support ground takes no waves and does not part them");
        assertTrue(HeartLanes.neighbours(across, waves, 3, 2));
        assertFalse(HeartLanes.neighbours(across, waves, 0, 2), "lane 1's wave would cross lane 2 to reach lane 3's gap");
        assertFalse(HeartLanes.neighbours(across, waves, 0, 3));
    }

    @Test
    void aLaneIsBrokenAgainstTheStrengthItsWavesArrivedWith() {
        // A column of sixteen that fought its way up and came in six strong is a whole lane of six, not a broken one.
        assertFalse(HeartLanes.broken(6, 6));
        assertFalse(HeartLanes.broken(2, 6));
        assertTrue(HeartLanes.broken(1, 6));
    }
}
