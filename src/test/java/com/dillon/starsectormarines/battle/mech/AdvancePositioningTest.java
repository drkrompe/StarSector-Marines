package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link AdvancePositioning#overlook}: the vantage near doctrine's point that sees the ground the advance is about. */
class AdvancePositioningTest {

    /** Open ground 20x12 with a wall block x 8..9, y 4..7 standing between the point and the key ground. */
    private static NavigationGrid field() {
        NavigationGrid grid = new NavigationGrid(20, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 4; y <= 7; y++) {
            for (int x = 8; x <= 9; x++) grid.setWalkable(x, y, false);
        }
        return grid;
    }

    private static final AdvancePositioning.Standable ANYWHERE = (x, y) -> true;

    @Test
    void aPointBehindAWallStepsAsideToTheNearestCellThatSeesTheKeyGround() {
        NavigationGrid grid = field();
        int[] aim = {6, 5};
        int key = grid.index(14, 5);
        assertFalse(sees(grid, aim[0], aim[1], key), "fixture check: the wall hides the key ground from the point");
        int[] pick = AdvancePositioning.overlook(grid, aim, new int[]{key}, 20f, ANYWHERE);
        assertTrue(sees(grid, pick[0], pick[1], key));
        float picked = distance(pick, aim);
        for (int y = aim[1] - AdvancePositioning.RING; y <= aim[1] + AdvancePositioning.RING; y++) {
            for (int x = aim[0] - AdvancePositioning.RING; x <= aim[0] + AdvancePositioning.RING; x++) {
                if (grid.inBounds(x, y) && sees(grid, x, y, key)) {
                    assertTrue(distance(new int[]{x, y}, aim) >= picked, "a nearer vantage was passed over: " + x + "," + y);
                }
            }
        }
    }

    @Test
    void aPointThatAlreadySeesTheKeyGroundIsKept() {
        NavigationGrid grid = field();
        int[] aim = {6, 9};
        assertArrayEquals(aim, AdvancePositioning.overlook(grid, aim, new int[]{grid.index(14, 9)}, 20f, ANYWHERE));
    }

    @Test
    void seeingMoreOfTheKeyGroundIsWorthAFewCellsMore() {
        NavigationGrid grid = field();
        int[] aim = {6, 5};
        int near = grid.index(6, 1), far = grid.index(14, 5);
        assertTrue(sees(grid, aim[0], aim[1], near), "fixture check: the point sees the near key");
        int[] pick = AdvancePositioning.overlook(grid, aim, new int[]{near, far}, 20f, ANYWHERE);
        assertTrue(sees(grid, pick[0], pick[1], near) && sees(grid, pick[0], pick[1], far),
                "the vantage sees both: " + pick[0] + "," + pick[1]);
    }

    private static boolean sees(NavigationGrid grid, int x, int y, int key) {
        int kx = key % grid.getWidth(), ky = key / grid.getWidth();
        return grid.isWalkable(x, y) && grid.hasLineOfFire(x + .5f, y + .5f, kx + .5f, ky + .5f);
    }

    private static float distance(int[] a, int[] b) {
        return (float) Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    @Test
    void nothingInSightOrInRangeLeavesThePoint() {
        NavigationGrid grid = field();
        int[] aim = {6, 5};
        assertArrayEquals(aim, AdvancePositioning.overlook(grid, aim, new int[]{grid.index(14, 5)}, 3f, ANYWHERE));
        assertArrayEquals(aim, AdvancePositioning.overlook(grid, aim, new int[]{grid.index(14, 5)}, 20f,
                (x, y) -> x == 6 && y == 5));
    }
}
