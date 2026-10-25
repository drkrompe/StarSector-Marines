package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachableRegionTest {

    /** Two rooms, x 1..4 and 6..9, with a wall column at x 5 between them. */
    private static NavigationGrid twoRooms() {
        NavigationGrid grid = new NavigationGrid(11, 6);
        for (int y = 1; y < 5; y++) {
            for (int x = 1; x < 10; x++) if (x != 5) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static ReachableRegion refusedFromLeftRoom(NavigationGrid grid) {
        ClearanceRoutePlanner.Result result =
                new ClearanceRoutePlanner().findRoute(grid, 2.5f, 2.5f, 7.5f, 2.5f, .5f, .75f, 100_000);
        assertEquals(ClearanceRoutePlanner.Status.UNREACHABLE, result.status());
        assertNotNull(result.reached());
        return result.reached();
    }

    @Test
    void anExhaustedRefusalHoldsTheStartsGroundAndNotTheGoalsSide() {
        ReachableRegion region = refusedFromLeftRoom(twoRooms());
        assertTrue(region.holds(2.5f, 2.5f));
        assertTrue(region.reaches(3, 3));
        assertFalse(region.reaches(7, 2));
        assertFalse(region.holds(7.5f, 2.5f));
    }

    @Test
    void aRefusalCutShortByItsBudgetKeepsNoRegion() {
        ClearanceRoutePlanner.Result result =
                new ClearanceRoutePlanner().findRoute(twoRooms(), 2.5f, 2.5f, 7.5f, 2.5f, .5f, .75f, 3);
        assertNull(result.reached());
    }

    @Test
    void theNearestReachedCellToAnUnreachedPointLiesOnTheStartsSide() {
        ReachableRegion region = refusedFromLeftRoom(twoRooms());
        int[] near = region.nearestReachedCell(7.5f, 2.5f, 6);
        assertNotNull(near);
        assertTrue(near[0] <= 4, () -> "picked " + near[0] + "," + near[1]);
        assertTrue(region.reaches(near[0], near[1]));
        assertNull(region.nearestReachedCell(7.5f, 2.5f, 1));
        assertArrayEquals(new int[]{3, 3}, region.nearestReachedCell(3.5f, 3.5f, 6));
    }

    @Test
    void memoryPublishesNextTickReplacesTheSameComponentAndForgetsWithAge() {
        NavigationGrid grid = twoRooms();
        MechReachability memory = new MechReachability();
        ReachableRegion first = refusedFromLeftRoom(grid);
        memory.beginTick(1);
        memory.learn(7L, first);
        assertNull(memory.regionFor(2.5f, 2.5f, .5f));
        memory.beginTick(2);
        assertSame(first, memory.regionFor(2.5f, 2.5f, .5f));
        assertNull(memory.regionFor(2.5f, 2.5f, .6f));
        assertNull(memory.regionFor(7.5f, 2.5f, .5f));

        ReachableRegion again = refusedFromLeftRoom(grid);
        memory.learn(3L, again);
        memory.beginTick(3);
        assertSame(again, memory.regionFor(2.5f, 2.5f, .5f));

        memory.beginTick(3 + MechReachability.MAX_AGE_TICKS + 1);
        assertNull(memory.regionFor(2.5f, 2.5f, .5f));
    }
}
