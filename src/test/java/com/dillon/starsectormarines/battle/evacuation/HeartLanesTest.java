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
}
