package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.HeartLane;
import com.dillon.starsectormarines.battle.command.allied.ScreenedAdvance;
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
    void armourGoesNoFurtherThanAScreensDistanceInFrontOfItsMilitia() {
        // The Burn: armour stood 4 cells ahead of its aim, the aim at 181, the militia back at 145.
        int screen = (int) ScreenedAdvance.DEFAULT_SCREEN_AHEAD;
        int along = HeartLanes.screenedAlong(181, 145f, 1, -4);
        assertEquals(145 + screen, along + 4, "the armour's line stands a screen's distance in front of the militia");
        assertEquals(181, HeartLanes.screenedAlong(181, 179f, 1, -4), "militia up with the aim let the armour go on to it");
        // Advancing toward lower cells, armour standing 4 behind (above) its aim.
        assertEquals(100 - screen, HeartLanes.screenedAlong(40, 100f, -1, 4) + 4);
        assertEquals(40, HeartLanes.screenedAlong(40, 41f, -1, 4));
    }

    @Test
    void aLaneIsBrokenAgainstTheStrengthItsWavesArrivedWith() {
        // A column of sixteen that fought its way up and came in six strong is a whole lane of six, not a broken one.
        assertFalse(HeartLanes.broken(6, 6));
        assertFalse(HeartLanes.broken(2, 6));
        assertTrue(HeartLanes.broken(1, 6));
    }
}
