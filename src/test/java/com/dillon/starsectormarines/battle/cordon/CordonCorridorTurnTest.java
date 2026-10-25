package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The corridor turns where a truck has room to turn, and drives a narrow lane straight at no extra cost. */
class CordonCorridorTurnTest {

    private static NavigationGrid walled(int size) {
        NavigationGrid grid = new NavigationGrid(size, size);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) grid.setWalkable(x, y, false);
        return grid;
    }

    private static void open(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) grid.setWalkableFloor(x, y);
    }

    @Test
    void aTurnIntoALaneThreeWideGoesRoundByTheOpenGround() {
        NavigationGrid grid = walled(30);
        open(grid, 0, 9, 29, 11);   // a street three wide along y = 10
        open(grid, 9, 9, 11, 29);   // an alley three wide off it at x = 10: a tight corner
        open(grid, 21, 7, 29, 17);  // a yard further on, with room to turn
        open(grid, 24, 9, 26, 29);  // and a lane three wide out of it at x = 25
        assertFalse(CordonCorridor.roomy(grid, 10, 10));
        assertTrue(CordonCorridor.roomy(grid, 25, 10));
        List<int[]> path = CordonCorridor.search(grid, (x, y) -> true, List.of(new int[]{1, 10}), (x, y) -> y == 28);
        assertNotNull(path);
        assertEquals(25, path.get(path.size() - 1)[0], "the longer way, turning in the yard");
    }

    @Test
    void aTightTurnIsStillTakenWhereItIsTheOnlyWay() {
        NavigationGrid grid = walled(30);
        open(grid, 0, 9, 29, 11);
        open(grid, 9, 9, 11, 29);
        List<int[]> path = CordonCorridor.search(grid, (x, y) -> true, List.of(new int[]{1, 10}), (x, y) -> y == 28);
        assertNotNull(path);
        assertEquals(10, path.get(path.size() - 1)[0]);
    }

    @Test
    void aLaneDrivenStraightCostsOnlyItsLength() {
        NavigationGrid grid = walled(30);
        open(grid, 0, 9, 29, 11);
        open(grid, 3, 0, 8, 20);    // open ground beside the lane's start: no reason to leave the lane
        List<int[]> path = CordonCorridor.search(grid, (x, y) -> y == 10, List.of(new int[]{1, 10}), (x, y) -> x == 28);
        assertNotNull(path);
        assertEquals(28, path.size());
        for (int[] c : path) assertEquals(10, c[1]);
    }
}
