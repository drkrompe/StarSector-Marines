package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
