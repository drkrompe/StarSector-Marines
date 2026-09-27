package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.nav.mesh.GreedyNavigationMesh;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SquadRouteBuildJobTest {
    @Test
    void everyStageHonorsTinyBudgetsAndBothStartsEventuallyReachTheGoal() {
        NavigationGrid grid = openGrid(70, 9);
        SquadRouteBuildJob job = new SquadRouteBuildJob(grid);
        job.begin(request(grid, 35, 4, null, 0, 8, 69, 0),
                new GreedyNavigationMesh(grid).snapshot(), true);
        assertEquals(0, job.advance(0));
        assertFalse(job.isDone());
        finish(job, 3);
        assertNotNull(job.field());
        assertTrue(job.reverseExpanded() > 0);
        assertTrue(job.seedExpanded() > 0);
        assertTrue(job.workUnits() > job.seedExpanded() + job.reverseExpanded());
        assertPath(grid, job.field().extract(0, 8), 35, 4);
        assertPath(grid, job.field().extract(69, 0), 35, 4);
    }

    @Test
    void singletonResumptionMatchesExactWeightedSearchAndDoesNotBuildAReverseField() {
        NavigationGrid grid = openGrid(14, 9);
        for (int y = 0; y < 7; y++) grid.setWalkable(6, y, false);
        float[] costs = new float[14 * 9];
        Arrays.fill(costs, 1f);
        for (int x = 7; x < 12; x++) costs[grid.index(x, 7)] = 5f;
        RouteCostField cost = new RouteCostField(costs, RouteCostField.nextRevision());
        int[] expected = GridPathfinder.findSquadRouteSeed(grid, 1, 1, 12, 1,
                GridPathfinder.USE_CARDINAL_NAVIGATION, cost);
        SquadRouteBuildJob job = new SquadRouteBuildJob(grid);
        job.begin(request(grid, 12, 1, cost, 1, 1), new GreedyNavigationMesh(grid).snapshot(), true);
        finish(job, 1);
        assertNotNull(job.field());
        assertArrayEquals(expected, job.field().extract(1, 1));
        assertEquals(0, job.reverseExpanded());
        assertEquals(expected.length / 2, job.seedPathCells());
        assertEquals(expected.length / 2, job.field().settledCellCount());
    }

    @Test
    void sharedWeightedReverseRoutesRespectWallsAndDestinationCosts() {
        NavigationGrid grid = openGrid(14, 9);
        for (int y = 0; y < 7; y++) grid.setWalkable(6, y, false);
        float[] costs = new float[14 * 9];
        Arrays.fill(costs, 1f);
        costs[grid.index(12, 1)] = 8f;
        costs[grid.index(8, 7)] = 9f;
        RouteCostField cost = new RouteCostField(costs, RouteCostField.nextRevision());
        SquadRouteBuildJob job = new SquadRouteBuildJob(grid);
        job.begin(request(grid, 12, 1, cost, 1, 1, 2, 1),
                new GreedyNavigationMesh(grid).snapshot(), true);
        finish(job, 7);
        for (int x : new int[]{1, 2}) {
            int[] expected = GridPathfinder.findSquadRouteSeed(grid, x, 1, 12, 1,
                    GridPathfinder.USE_CARDINAL_NAVIGATION, cost);
            int[] path = job.field().extract(x, 1);
            assertPath(grid, path, 12, 1);
            assertEquals(pathCost(expected, grid, cost), pathCost(path, grid, cost), 0.001f);
        }
    }

    @Test
    void reusedScratchCannotMutatePublishedSuccessorsAndDoesNotLeakOldRegions() {
        NavigationGrid grid = openGrid(100, 3);
        GreedyNavigationMesh.Snapshot mesh = new GreedyNavigationMesh(grid).snapshot();
        SquadRouteBuildJob job = new SquadRouteBuildJob(grid);
        job.begin(request(grid, 99, 1, null, 0, 1, 0, 2), mesh, true);
        finish(job, 11);
        SquadRouteField first = job.field();
        int[] firstPath = first.extract(0, 1);
        job.begin(request(grid, 2, 0, null, 0, 0), mesh, true);
        finish(job, 2);
        assertArrayEquals(firstPath, first.extract(0, 1));
        assertFalse(job.field().covers(grid.index(99, 1)));
        assertEquals(3, job.field().settledCellCount());
    }

    @Test
    void exhaustedSliceIsPendingWhereasACompletedDisconnectedSearchHasNoField() {
        NavigationGrid grid = openGrid(20, 10);
        for (int y = 0; y < 10; y++) grid.setSharedEdgePassable(10, y, Direction.E, false);
        SquadRouteBuildJob job = new SquadRouteBuildJob(grid);
        job.begin(request(grid, 18, 5, null, 0, 5),
                new GreedyNavigationMesh(grid).snapshot(), true);
        assertEquals(4, job.advance(4));
        assertFalse(job.isDone());
        assertNull(job.field());
        finish(job, 5);
        assertNull(job.field());
        assertFalse(job.invalidated());
        assertTrue(job.seedExpanded() > 4);
    }

    @Test
    void changedTopologyInvalidatesBeforeAnyFurtherSearchOrPublication() {
        NavigationGrid grid = openGrid(15, 3);
        SquadRouteBuildJob job = new SquadRouteBuildJob(grid);
        job.begin(request(grid, 14, 1, null, 0, 1),
                new GreedyNavigationMesh(grid).snapshot(), true);
        job.advance(3);
        long work = job.workUnits();
        grid.setWalkable(7, 1, false);
        assertEquals(0, job.advance(10));
        assertTrue(job.isDone());
        assertTrue(job.invalidated());
        assertNull(job.field());
        assertEquals(work, job.workUnits());
    }

    @Test
    void cancellingAnUnfinishedSearchAndChangingSliceSizeDoesNotChangeThePublishedRoute() {
        NavigationGrid grid = openGrid(40, 7);
        GreedyNavigationMesh.Snapshot mesh = new GreedyNavigationMesh(grid).snapshot();
        SquadRouteBuildJob reused = new SquadRouteBuildJob(grid);
        reused.begin(request(grid, 39, 6, null, 0, 0), mesh, true);
        reused.advance(13);
        assertFalse(reused.isDone());
        reused.release();
        SquadRouteRequest request = request(grid, 0, 3, null, 39, 2, 38, 4);
        reused.begin(request, mesh, true);
        finish(reused, 1);
        SquadRouteBuildJob fresh = new SquadRouteBuildJob(grid);
        fresh.begin(request, mesh, true);
        finish(fresh, 10_000);
        assertEquals(fresh.workUnits(), reused.workUnits());
        assertEquals(fresh.seedExpanded(), reused.seedExpanded());
        assertEquals(fresh.reverseExpanded(), reused.reverseExpanded());
        assertArrayEquals(fresh.field().extract(39, 2), reused.field().extract(39, 2));
        assertArrayEquals(fresh.field().extract(38, 4), reused.field().extract(38, 4));
    }

    private static void finish(SquadRouteBuildJob job, int budget) {
        int calls = 0;
        while (!job.isDone()) {
            long before = job.workUnits();
            int used = job.advance(budget);
            assertTrue(used > 0 && used <= budget);
            assertEquals(used, job.workUnits() - before);
            assertTrue(++calls < 100_000, "Job failed to converge");
        }
    }

    private static SquadRouteRequest request(NavigationGrid grid, int gx, int gy,
                                              RouteCostField cost, int... coordinates) {
        int[] starts = new int[coordinates.length / 2];
        for (int i = 0; i < starts.length; i++) starts[i] = grid.index(
                coordinates[i * 2], coordinates[i * 2 + 1]);
        return new SquadRouteRequest(1, 1, new Object(), gx, gy, starts, cost);
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    private static void assertPath(NavigationGrid grid, int[] path, int gx, int gy) {
        assertTrue(path.length > 0);
        assertEquals(gx, path[path.length - 2]);
        assertEquals(gy, path[path.length - 1]);
        for (int i = 0; i < path.length; i += 2) assertTrue(grid.isWalkable(path[i], path[i + 1]));
        for (int i = 2; i < path.length; i += 2) {
            int dx = path[i] - path[i - 2], dy = path[i + 1] - path[i - 1];
            int direction = 0;
            while (direction < 8 && (GridPathfinder.directionX(direction) != dx
                    || GridPathfinder.directionY(direction) != dy)) direction++;
            assertTrue(direction < GridPathfinder.directionCount(GridPathfinder.USE_CARDINAL_NAVIGATION));
            assertTrue(GridPathfinder.canStep(grid.index(path[i - 2], path[i - 1]),
                    path[i - 2], path[i - 1], grid.index(path[i], path[i + 1]), direction,
                    grid.getWidth(), grid.getHeight(), grid.getCellFlagsArray(),
                    grid.getEdgePassabilityArray(), null));
        }
    }

    private static float pathCost(int[] path, NavigationGrid grid, RouteCostField cost) {
        float total = 0;
        for (int i = 2; i < path.length; i += 2) {
            float step = path[i] != path[i - 2] && path[i + 1] != path[i - 1] ? (float) Math.sqrt(2) : 1f;
            total += step * cost.costAt(grid.index(path[i], path[i + 1]));
        }
        return total;
    }
}
