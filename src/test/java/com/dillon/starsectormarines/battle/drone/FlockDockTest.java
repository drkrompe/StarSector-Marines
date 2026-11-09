package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlockDockTest {

    @Test
    void onlyAReturningFlockHomeLandsAMemberAtItsCradle() {
        assertTrue(FlockDock.lands(FlockTask.RETURN, true, true, 1f));
        assertFalse(FlockDock.lands(FlockTask.RETURN, true, true, FlockDock.LAND_CELLS + .5f), "too far out");
        assertFalse(FlockDock.lands(FlockTask.RETURN, false, true, 1f), "the flock is not home yet");
        assertFalse(FlockDock.lands(FlockTask.SCREEN, true, true, 1f), "a screening flock holds over its home");
        assertFalse(FlockDock.lands(FlockTask.RETURN, true, false, 1f), "nothing lands in a cradle that is down");
    }

    @Test
    void aFlockSentOutPoursOutOneDroneAtATimeTheFirstAtOnce() {
        FlockDock dock = new FlockDock();
        for (int i = 0; i < 3; i++) dock.board();
        assertEquals(0, dock.launches(.05f, FlockTask.RETURN, true), "going home, nothing leaves");
        assertEquals(1, dock.launches(.01f, FlockTask.STRIKE, true), "sent out, the first goes at once");
        assertEquals(0, dock.launches(.1f, FlockTask.STRIKE, true));
        assertEquals(1, dock.launches(.05f, FlockTask.STRIKE, true));
        assertEquals(1, dock.launches(1f, FlockTask.STRIKE, true), "never more than are aboard");
        assertEquals(0, dock.aboard());
    }

    @Test
    void theDronesAboardAreLostWithTheCradle() {
        FlockDock dock = new FlockDock();
        dock.board();
        dock.board();
        assertEquals(0, dock.launches(.1f, FlockTask.SCREEN, false));
        assertEquals(0, dock.aboard());
    }
}
