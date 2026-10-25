package com.dillon.starsectormarines.battle.world.gen.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutStreetsTest {

    /** Fills rows {@code y0..y1} across columns {@code x0..x1}. */
    private static void fill(boolean[][] g, int x0, int y0, int x1, int y1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) g[x][y] = true;
    }

    @Test
    void aWideStreetIsCheaperThanANarrowOne() {
        assertTrue(LayoutStreets.cost(true, 4) < LayoutStreets.cost(true, 1));
        assertTrue(LayoutStreets.cost(true, 1) < LayoutStreets.cost(false, 0));
        assertEquals(LayoutStreets.cost(true, LayoutStreets.WIDE), LayoutStreets.cost(true, 9));
    }

    @Test
    void clearanceGrowsTowardAStreetsMiddle() {
        boolean[][] road = new boolean[20][20];
        fill(road, 0, 5, 19, 11);
        int[][] c = LayoutStreets.clearance(road);
        assertEquals(1, c[10][5]);
        assertEquals(2, c[10][6]);
        assertEquals(4, c[10][8]);
        assertEquals(0, c[10][2]);
    }

    @Test
    void theWayTakesTheBoulevardRoundRatherThanTheBackStreetAcross() {
        // Two ends on a wide street's loop; a one-cell alley cuts straight between them.
        boolean[][] road = new boolean[40][40], open = new boolean[40][40];
        fill(road, 0, 0, 39, 6);
        fill(road, 0, 0, 6, 39);
        fill(road, 33, 0, 39, 39);
        fill(road, 6, 30, 33, 30);
        for (int x = 0; x < 40; x++) for (int y = 0; y < 40; y++) open[x][y] = road[x][y];
        List<int[]> way = LayoutStreets.way(open, road, new int[]{3, 30}, new int[]{36, 30});
        assertNotNull(way);
        int minY = Integer.MAX_VALUE;
        for (int[] c : way) minY = Math.min(minY, c[1]);
        assertTrue(minY <= 6, "the way should go round by the wide street, not through the alley");
    }

    @Test
    void noWayWhereNothingOpenJoinsTheEnds() {
        boolean[][] road = new boolean[20][20], open = new boolean[20][20];
        fill(road, 0, 0, 7, 19);
        fill(road, 12, 0, 19, 19);
        for (int x = 0; x < 20; x++) for (int y = 0; y < 20; y++) open[x][y] = road[x][y];
        assertNull(LayoutStreets.way(open, road, new int[]{2, 10}, new int[]{17, 10}));
    }
}
