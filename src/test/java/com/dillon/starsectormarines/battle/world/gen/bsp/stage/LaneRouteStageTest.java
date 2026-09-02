package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The walk one leg of a lane route takes, asked of the search directly.
 *
 * <p>Nine cells by five, hand-drawn, because what is being asked is whether the
 * search prefers road to open ground and refuses to cross a wall — and a whole
 * generated map would answer for the map as well.
 */
class LaneRouteStageTest {

    private static final int WIDTH = 9;
    private static final int HEIGHT = 5;

    private static NavigationGrid openGround() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) grid.setWalkable(x, y, true);
        }
        return grid;
    }

    private static int[][] noRoad() {
        int[][] road = new int[WIDTH][HEIGHT];
        for (int[] column : road) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        return road;
    }

    @Test
    @DisplayName("open ground is crossed in a straight line when there is no road")
    void openGroundIsCrossedStraight() {
        List<int[]> route = LaneRouteStage.walk(openGround(), noRoad(),
                new int[]{0, 2}, new int[]{8, 2}, WIDTH, HEIGHT);
        assertNotNull(route);
        assertEquals(9, route.size(), "nine cells from one side to the other");
        for (int[] cell : route) assertEquals(2, cell[1], "and it never leaves the row");
    }

    @Test
    @DisplayName("a road worth half again the distance is taken over open ground")
    void theRoadIsPreferredOverTheShortcut() {
        int[][] road = noRoad();
        // Up the near side, along the top, down the far side: twelve cells of
        // tarmac against nine of field.
        for (int x = 0; x < WIDTH; x++) road[x][0] = 0;
        road[0][1] = 0;
        road[0][2] = 0;
        road[8][1] = 0;
        road[8][2] = 0;

        List<int[]> route = LaneRouteStage.walk(openGround(), road,
                new int[]{0, 2}, new int[]{8, 2}, WIDTH, HEIGHT);
        assertNotNull(route);
        assertTrue(route.size() > 9, "the shortcut was taken: " + route.size() + " cells");
        for (int[] cell : route) {
            assertEquals(0, road[cell[0]][cell[1]],
                    "route left the road at " + cell[0] + "," + cell[1]);
        }
    }

    @Test
    @DisplayName("a wall across the map with no gate is no route at all")
    void aSealedMapHasNoRoute() {
        NavigationGrid grid = openGround();
        for (int y = 0; y < HEIGHT; y++) grid.setWalkable(4, y, false);
        assertNull(LaneRouteStage.walk(grid, noRoad(),
                new int[]{0, 2}, new int[]{8, 2}, WIDTH, HEIGHT));
    }

    @Test
    @DisplayName("a wall with a gate is walked through the gate")
    void aGateIsFound() {
        NavigationGrid grid = openGround();
        for (int y = 0; y < HEIGHT; y++) grid.setWalkable(4, y, false);
        grid.setWalkable(4, 0, true);

        List<int[]> route = LaneRouteStage.walk(grid, noRoad(),
                new int[]{0, 2}, new int[]{8, 2}, WIDTH, HEIGHT);
        assertNotNull(route);
        assertTrue(route.stream().anyMatch(cell -> cell[0] == 4 && cell[1] == 0),
                "the route crossed the wall somewhere other than its gate");
        for (int[] cell : route) {
            assertTrue(grid.isWalkable(cell[0], cell[1]),
                    "route through " + cell[0] + "," + cell[1] + ", which is a wall");
        }
    }

    @Test
    @DisplayName("a link standing on unwalkable ground is not a link a route can reach")
    void anUnwalkableEndIsRefused() {
        NavigationGrid grid = openGround();
        grid.setWalkable(8, 2, false);
        assertNull(LaneRouteStage.walk(grid, noRoad(),
                new int[]{0, 2}, new int[]{8, 2}, WIDTH, HEIGHT));
    }
}
