package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadRouteFieldTest {
    @Test
    void exactPathStopsAtRequiredStartAndDoesNotRetainBuilderState() {
        NavigationGrid grid = openGrid(8, 1);
        SquadRouteField.Builder builder = new SquadRouteField.Builder(grid);
        SquadRouteField field = builder.build(allCells(grid), null, 7, new int[]{4}, true);
        assertEquals(8, field.corridorCellCount());
        assertEquals(4, field.settledCellCount());
        assertArrayEquals(new int[]{4, 0, 5, 0, 6, 0, 7, 0}, field.extract(4, 0));
        assertSame(GridPathfinder.EMPTY_PATH, field.extract(3, 0));
        builder.build(new int[]{0, 1}, null, 0, new int[]{1}, true);
        assertArrayEquals(new int[]{4, 0, 5, 0, 6, 0, 7, 0}, field.extract(4, 0));
        assertArrayEquals(new int[]{7, 0}, field.extract(7, 0));
    }

    @Test
    void splitStartsBothReachGoalAndDuplicateRequestsDoNotExtendSearch() {
        NavigationGrid grid = openGrid(7, 3);
        SquadRouteField.Builder builder = new SquadRouteField.Builder(grid);
        int[] starts = {grid.index(0, 1), grid.index(6, 1)};
        SquadRouteField field = builder.build(allCells(grid), null, grid.index(3, 1), starts, true);
        assertEquals(8, field.extract(0, 1).length);
        assertEquals(8, field.extract(6, 1).length);
        SquadRouteField repeated = builder.build(allCells(grid), null, grid.index(3, 1),
                new int[]{starts[1], starts[0], starts[0], -1, 100}, true);
        assertEquals(field.settledCellCount(), repeated.settledCellCount());
        assertArrayEquals(field.extract(0, 1), repeated.extract(0, 1));
        assertArrayEquals(field.extract(6, 1), repeated.extract(6, 1));
    }

    @Test
    void detourHonorsObstacleAndDiagonalCornerGuards() {
        NavigationGrid grid = openGrid(5, 4);
        for (int y = 0; y < 3; y++) grid.setWalkable(2, y, false);
        SquadRouteField field = new SquadRouteField.Builder(grid).build(allCells(grid),
                null, grid.index(4, 0), new int[]{0}, false);
        int[] path = field.extract(0, 0);
        assertTrue(contains(path, 2, 3));
        assertEquals(pathCost(GridPathfinder.findPath(grid, 0, 0, 4, 0, false, null), grid, null),
                pathCost(path, grid, null), 0.0001f);
        NavigationGrid corner = openGrid(2, 2);
        corner.setWalkable(1, 0, false);
        SquadRouteField guarded = new SquadRouteField.Builder(corner).build(allCells(corner),
                null, 3, new int[]{0}, false);
        assertArrayEquals(new int[]{0, 0, 0, 1, 1, 1}, guarded.extract(0, 0));
    }

    @Test
    void missingCorridorCoverageDoesNotBecomeAnUnreachableClaim() {
        NavigationGrid grid = openGrid(5, 2);
        SquadRouteField.Builder builder = new SquadRouteField.Builder(grid);
        SquadRouteField field = builder.build(new int[]{0, 1, 2, 3, 4}, null, 4,
                new int[]{0, 5}, true);
        assertSame(GridPathfinder.EMPTY_PATH, field.extract(0, 1));
        assertSame(GridPathfinder.EMPTY_PATH, field.extract(-1, 0));
        assertTrue(GridPathfinder.findPath(grid, 0, 1, 4, 0, true, null).length > 0);
        SquadRouteField disconnected = builder.build(new int[]{0, 1, 3, 4}, null, 4,
                new int[]{0}, true);
        assertSame(GridPathfinder.EMPTY_PATH, disconnected.extract(0, 0));
        assertEquals(2, disconnected.settledCellCount());
    }

    @Test
    void closedEdgesAreRespectedAndRebuildSeesAnOpenedDoor() {
        NavigationGrid grid = openGrid(3, 1);
        grid.setSharedEdgePassable(1, 0, Direction.E, false);
        SquadRouteField.Builder builder = new SquadRouteField.Builder(grid);
        SquadRouteField blocked = builder.build(allCells(grid), null, 2, new int[]{0}, true);
        assertSame(GridPathfinder.EMPTY_PATH, blocked.extract(0, 0));
        grid.setSharedEdgePassable(1, 0, Direction.E, true);
        SquadRouteField opened = builder.build(allCells(grid), null, 2, new int[]{0}, true);
        assertArrayEquals(new int[]{0, 0, 1, 0, 2, 0}, opened.extract(0, 0));
        assertSame(GridPathfinder.EMPTY_PATH, blocked.extract(0, 0));
    }

    @Test
    void routeCostsAreChargedOnEntryIncludingGoalAndSelectCheaperDetour() {
        NavigationGrid grid = openGrid(5, 3);
        float[] costs = new float[15];
        Arrays.fill(costs, 1f);
        costs[grid.index(2, 1)] = 10f;
        costs[grid.index(4, 1)] = 3f;
        SquadRouteField field = new SquadRouteField.Builder(grid).build(allCells(grid),
                new RouteCostField(costs, RouteCostField.nextRevision()),
                grid.index(4, 1), new int[]{grid.index(0, 1)}, false);
        int[] path = field.extract(0, 1);
        assertFalse(contains(path, 2, 1));
        int[] ordinary = GridPathfinder.findPath(grid, 0, 1, 4, 1, false, null, costs, null);
        assertEquals(pathCost(ordinary, grid, costs), pathCost(path, grid, costs), 0.0001f);
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static int[] allCells(NavigationGrid grid) {
        return IntStream.range(0, grid.getWidth() * grid.getHeight()).toArray();
    }

    private static boolean contains(int[] path, int x, int y) {
        for (int i = 0; i < path.length; i += 2) {
            if (path[i] == x && path[i + 1] == y) return true;
        }
        return false;
    }

    private static float pathCost(int[] path, NavigationGrid grid, float[] costs) {
        float total = 0;
        for (int i = 2; i < path.length; i += 2) {
            float step = path[i] != path[i - 2] && path[i + 1] != path[i - 1]
                    ? (float) Math.sqrt(2) : 1f;
            total += step * (costs == null ? 1 : costs[grid.index(path[i], path[i + 1])]);
        }
        return total;
    }
}
