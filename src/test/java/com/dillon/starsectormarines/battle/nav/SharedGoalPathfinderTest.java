package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedGoalPathfinderTest {

    @Test
    void preservesEndpointAndUnreachableContracts() {
        NavigationGrid grid = openGrid(4, 1);
        byte[] occupancy = new byte[4];
        SharedGoalPathfinder pathfinder =
                new SharedGoalPathfinder(grid, occupancy, 1);
        pathfinder.beginSnapshot();

        assertArrayEquals(new int[]{1, 0},
                pathfinder.findPath(1, 0, 1, 0, false));

        grid.setWalkable(1, 0, false);
        assertSame(GridPathfinder.EMPTY_PATH,
                pathfinder.findPath(1, 0, 3, 0, false));
        assertSame(GridPathfinder.EMPTY_PATH,
                pathfinder.findPath(-1, 0, 3, 0, false));

        grid.setWalkable(1, 0, true);
        grid.setWalkable(2, 0, false);
        pathfinder.beginSnapshot();
        assertSame(GridPathfinder.EMPTY_PATH,
                pathfinder.findPath(0, 0, 3, 0, false));
    }

    @Test
    void occupancyPenaltyChoosesTheLowerCostDetour() {
        NavigationGrid grid = openGrid(5, 3);
        byte[] occupancy = new byte[15];
        occupancy[grid.index(2, 1)] = 1;
        SharedGoalPathfinder pathfinder =
                new SharedGoalPathfinder(grid, occupancy);
        pathfinder.beginSnapshot();

        int[] path = pathfinder.findPath(0, 1, 4, 1, false);

        assertArrayEquals(new int[]{0, 1}, firstCell(path));
        assertArrayEquals(new int[]{4, 1}, lastCell(path));
        assertFalse(contains(path, 2, 1),
                "one occupied center cell makes the open diagonal detour cheaper");
    }

    @Test
    void diagonalStillRequiresBothAdjacentCellsToBeWalkable() {
        NavigationGrid grid = openGrid(2, 2);
        grid.setWalkable(1, 0, false);
        SharedGoalPathfinder pathfinder =
                new SharedGoalPathfinder(grid, new byte[4]);
        pathfinder.beginSnapshot();

        int[] path = pathfinder.findPath(0, 0, 1, 1, false);

        assertArrayEquals(new int[]{0, 0, 0, 1, 1, 1}, path,
                "the blocked east guard cell must prevent a direct NE step");
    }

    @Test
    void maxAgeOneRebuildsForEveryNewOccupancySnapshot() {
        NavigationGrid grid = openGrid(5, 3);
        byte[] occupancy = new byte[15];
        SharedGoalPathfinder pathfinder =
                new SharedGoalPathfinder(grid, occupancy, 1);
        pathfinder.beginSnapshot();

        int[] first = pathfinder.findPath(0, 1, 4, 1, false);
        occupancy[grid.index(2, 1)] = 1;
        int[] stillFrozen = pathfinder.findPath(0, 1, 4, 1, false);

        assertArrayEquals(first, stillFrozen,
                "a built field remains immutable for its snapshot");
        pathfinder.beginSnapshot();
        int[] rebuilt = pathfinder.findPath(0, 1, 4, 1, false);
        assertFalse(contains(rebuilt, 2, 1),
                "the next snapshot must rebuild against the new occupancy map");
    }

    @Test
    void retainedFieldKeepsFrozenOccupancyUntilExactExpiry() {
        NavigationGrid grid = openGrid(5, 3);
        byte[] occupancy = new byte[15];
        SharedGoalPathfinder pathfinder =
                new SharedGoalPathfinder(grid, occupancy, 3);
        pathfinder.beginSnapshot();
        int[] first = pathfinder.findPath(0, 1, 4, 1, false);
        assertTrue(contains(first, 2, 1));
        pathfinder.endSnapshot();

        occupancy[grid.index(2, 1)] = 1;
        pathfinder.beginSnapshot();
        assertArrayEquals(first,
                pathfinder.findPath(0, 1, 4, 1, false));
        pathfinder.endSnapshot();
        pathfinder.beginSnapshot();
        assertArrayEquals(first,
                pathfinder.findPath(0, 1, 4, 1, false),
                "age two remains below the max age of three");
        pathfinder.endSnapshot();

        pathfinder.beginSnapshot();
        int[] rebuilt = pathfinder.findPath(0, 1, 4, 1, false);
        assertFalse(contains(rebuilt, 2, 1),
                "age three expires and rebuilds against current occupancy");
    }

    @Test
    void explicitInvalidationRebuildsBeforeCadenceExpiry() {
        NavigationGrid grid = openGrid(5, 3);
        byte[] occupancy = new byte[15];
        SharedGoalPathfinder pathfinder =
                new SharedGoalPathfinder(grid, occupancy, 15);
        pathfinder.beginSnapshot();
        int[] first = pathfinder.findPath(0, 1, 4, 1, false);
        assertTrue(contains(first, 2, 1));
        pathfinder.endSnapshot();

        occupancy[grid.index(2, 1)] = 1;
        pathfinder.invalidateAll();
        pathfinder.beginSnapshot();

        assertFalse(contains(
                        pathfinder.findPath(0, 1, 4, 1, false), 2, 1),
                "topology invalidation must not wait for occupancy cadence");
    }

    @Test
    void retainedCacheIsBoundedAtSerialSnapshotBoundary() {
        NavigationGrid grid = openGrid(42, 1);
        SharedGoalPathfinder pathfinder =
                new SharedGoalPathfinder(grid, new byte[42], 100);
        pathfinder.beginSnapshot();
        for (int goalX = 1; goalX <= 40; goalX++) {
            pathfinder.findPath(0, 0, goalX, 0, false);
        }
        assertEquals(40, pathfinder.retainedFieldCountForTest());

        pathfinder.endSnapshot();

        assertEquals(32, pathfinder.retainedFieldCountForTest());
    }

    @Test
    void productionCadenceRetainsFieldsForFifteenSnapshots() {
        assertEquals(15,
                SharedGoalPathfinder.DEFAULT_MAX_BUILD_AGE_SNAPSHOTS);
    }

    @Test
    void matchesAStarReachabilityAndMinimumCostAcrossManyStartsAndGoals() {
        NavigationGrid grid = openGrid(8, 6);
        for (int y = 0; y < 6; y++) {
            if (y != 1 && y != 4) grid.setWalkable(3, y, false);
        }
        byte[] occupancy = new byte[8 * 6];
        occupancy[grid.index(2, 1)] = 2;
        occupancy[grid.index(4, 4)] = 1;
        occupancy[grid.index(6, 2)] = 3;
        SharedGoalPathfinder shared =
                new SharedGoalPathfinder(grid, occupancy);
        shared.beginSnapshot();

        for (boolean cardinalOnly : new boolean[]{false, true}) {
            for (int goalY = 0; goalY < 6; goalY++) {
                for (int goalX = 0; goalX < 8; goalX++) {
                    if (!grid.isWalkable(goalX, goalY)) continue;
                    for (int startY = 0; startY < 6; startY++) {
                        for (int startX = 0; startX < 8; startX++) {
                            if (!grid.isWalkable(startX, startY)) continue;
                            int[] expected = GridPathfinder.findPath(grid,
                                    startX, startY, goalX, goalY,
                                    cardinalOnly, occupancy);
                            int[] actual = shared.findPath(startX, startY,
                                    goalX, goalY, cardinalOnly);

                            assertEquals(Paths.isEmpty(expected),
                                    Paths.isEmpty(actual));
                            if (!Paths.isEmpty(expected)) {
                                assertEquals(pathCost(expected, occupancy, grid),
                                        pathCost(actual, occupancy, grid), 0.0001f,
                                        "minimum cost must match for start "
                                                + startX + "," + startY + " goal "
                                                + goalX + "," + goalY
                                                + " cardinalOnly=" + cardinalOnly);
                            }
                        }
                    }
                }
            }
        }
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return grid;
    }

    private static boolean contains(int[] path, int x, int y) {
        for (int cell = 0; cell < path.length / 2; cell++) {
            if (path[cell * 2] == x && path[cell * 2 + 1] == y) return true;
        }
        return false;
    }

    private static int[] firstCell(int[] path) {
        return new int[]{path[0], path[1]};
    }

    private static int[] lastCell(int[] path) {
        return new int[]{path[path.length - 2], path[path.length - 1]};
    }

    private static float pathCost(int[] path, byte[] occupancy,
                                  NavigationGrid grid) {
        float cost = 0f;
        for (int cell = 1; cell < Paths.cellCount(path); cell++) {
            int previousX = Paths.cellX(path, cell - 1);
            int previousY = Paths.cellY(path, cell - 1);
            int x = Paths.cellX(path, cell);
            int y = Paths.cellY(path, cell);
            boolean diagonal = previousX != x && previousY != y;
            cost += diagonal ? (float) Math.sqrt(2.0) : 1f;
            cost += GridPathfinder.OCCUPANCY_PENALTY
                    * (occupancy[grid.index(x, y)] & 0xFF);
        }
        return cost;
    }
}
