package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoreAssaultTest {

    private static final int BORE = 40;

    @Test
    void theRingIsAShareOfReachButNeverInsideTheMouth() {
        assertEquals(BoreAssault.MOUTH_CELLS + BoreAssault.RING_MARGIN_CELLS, BoreAssault.ringFor(0f));
        assertEquals(BoreAssault.MOUTH_CELLS + BoreAssault.RING_MARGIN_CELLS, BoreAssault.ringFor(12f),
                "a short gun still never stands in the mouth");
        assertEquals(21, BoreAssault.ringFor(30f));
    }

    @Test
    void postsStandOnTheNearSideSpreadApart() {
        NavigationGrid grid = open(80, 80);
        VehicleClearance clearance = VehicleClearance.erode(grid, 1);
        List<int[]> taken = new ArrayList<>();
        int ring = 18;
        for (int i = 0; i < 3; i++) {
            int[] post = BoreAssault.ringPost(grid, clearance, BORE, BORE, 0f, -1f, ring,
                    BoreAssault.slotDegrees(i, 3), taken, 5);
            assertNotNull(post);
            double dx = post[0] - BORE, dy = post[1] - BORE, distance = Math.hypot(dx, dy);
            assertTrue(distance >= ring && distance < ring + 1.5, "on the ring");
            assertTrue(-dy / distance >= Math.cos(Math.toRadians(BoreAssault.NEAR_ARC_DEG)) - 1e-6, "near side");
            for (int[] other : taken) {
                assertTrue(Math.max(Math.abs(other[0] - post[0]), Math.abs(other[1] - post[1])) >= 5, "spread");
            }
            taken.add(post);
        }
    }

    @Test
    void aPostWithALineOfFireBeatsTheSlotItself() {
        NavigationGrid grid = open(80, 80);
        // A wall straight between the bore and the near way's slot.
        for (int x = BORE - 2; x <= BORE + 2; x++) grid.setWalkable(x, BORE - 8, false);
        int[] post = BoreAssault.ringPost(grid, VehicleClearance.erode(grid, 1), BORE, BORE, 0f, -1f, 18, 0f,
                List.of(), 5);
        assertNotNull(post);
        assertTrue(grid.hasLineOfFire(post[0] + .5f, post[1] + .5f, BORE + .5f, BORE + .5f));
    }

    @Test
    void aSurgeIsSpentOnceItHasSettledAndTheMouthIsQuiet() {
        assertFalse(BoreAssault.spent(2f, 30f, 0, 8), "still pouring out");
        assertTrue(BoreAssault.spent(4f, 30f, 2, 8));
        assertFalse(BoreAssault.spent(4f, 30f, BoreAssault.QUIET_BODIES + 1, 8), "the mouth is full for a small lane");
        assertFalse(BoreAssault.spent(-1f, 30f, 0, 8), "no surge yet: wait");
        assertTrue(BoreAssault.spent(-1f, BoreAssault.QUIET_SECONDS, 0, 8), "a bore that never surges is gone in on");
    }

    @Test
    void aStrongLaneGoesInPastMoreOfTheSwarm() {
        assertFalse(BoreAssault.quiet(12, 8));
        assertTrue(BoreAssault.quiet(12, 24), "half of twenty-four");
        assertFalse(BoreAssault.quiet(13, 24));
    }

    @Test
    void aLaneGoesInAfterTheLongestWaitButNotIntoAFreshSurge() {
        assertFalse(BoreAssault.waitedOut(BoreAssault.MAX_WAIT_SECONDS - 1f, -1f));
        assertTrue(BoreAssault.waitedOut(BoreAssault.MAX_WAIT_SECONDS, -1f));
        assertFalse(BoreAssault.waitedOut(BoreAssault.MAX_WAIT_SECONDS, 1f), "a surge is still pouring out");
        assertTrue(BoreAssault.waitedOut(BoreAssault.MAX_WAIT_SECONDS, BoreAssault.SETTLE_SECONDS));
    }

    @Test
    void aFormationRalliesOnTheSupportLineNotOnTheBore() {
        int back = BoreAssault.MOUTH_CELLS + BoreAssault.RING_MARGIN_CELLS + BoreAssault.SUPPORT_BACK_CELLS;
        int[] rally = BoreAssault.rallyCell(BORE, BORE - 60, BORE, BORE);
        assertNotNull(rally);
        assertEquals(BORE, rally[0]);
        assertEquals(BORE - back, rally[1], "on the support line, on the way the lane comes");
        assertNull(BoreAssault.rallyCell(BORE, BORE - back, BORE, BORE), "already there: no advance to make");
    }

    @Test
    void aNewSurgeIsSeenOnce() {
        BoreAssault assault = new BoreAssault("1", BORE, BORE, 0f, -1f, false, 3, 0);
        assertFalse(assault.surged(3, 10));
        assertTrue(assault.surged(4, 20));
        assertEquals(20, assault.lastSurgeTick);
        assertFalse(assault.surged(4, 30));
    }

    private static NavigationGrid open(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
