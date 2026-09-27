package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.nav.mesh.GreedyNavigationMesh;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SquadRouteBuildJobTest {
    @Test
    void shortSharedRoutePreparationDoesNotEnumerateSelectedAreaOrRemoteRegions() {
        long[] work = new long[2];
        int[] widths = {64, 560};
        int[] heights = {64, 336};
        for (int i = 0; i < widths.length; i++) {
            NavigationGrid grid = openGrid(widths[i], heights[i]);
            GreedyNavigationMesh.Snapshot mesh = new GreedyNavigationMesh(grid).snapshot();
            SquadRouteBuildJob job = new SquadRouteBuildJob(grid);
            job.begin(request(grid, 10, 10, null, 9, 10, 10, 11), mesh, true);
            finish(job, 7);
            assertEquals(3072, job.corridorCells());
            assertEquals(1, job.seedSearches());
            assertPath(grid, job.field().extract(9, 10), 10, 10);
            assertPath(grid, job.field().extract(10, 11), 10, 10);
            work[i] = job.workUnits();
            System.out.printf("Region-backed short route %dx%d: work=%d, seed=%d, reverse=%d%n",
                    widths[i], heights[i], work[i], job.seedExpanded(), job.reverseExpanded());
            // The old explicit mask charged 3,110 / 3,304 units here, despite
            // just two seed expansions and five reverse expansions in either.
            assertTrue(work[i] < 100, "Preparation must not visit all 3,072 corridor cells");
        }
        assertEquals(work[0], work[1], "Remote mesh regions must not add route work");
    }

    @Test
    void selectedRegionMembershipPreservesEveryLegacySuccessorOnWeightedSplitTerrain() {
        boolean cardinalBefore = GridPathfinder.USE_CARDINAL_NAVIGATION;
        try {
            for (boolean cardinal : new boolean[]{true, false}) {
                GridPathfinder.USE_CARDINAL_NAVIGATION = cardinal;
                NavigationGrid grid = openGrid(70, 14);
                for (int y = 0; y < 11; y++) grid.setWalkable(31, y, false);
                grid.setSharedEdgePassable(20, 6, Direction.E, false);
                grid.setWalkable(64, 10, false);
                float[] values = new float[70 * 14];
                Arrays.fill(values, 1f);
                for (int x = 40; x < 61; x++) values[grid.index(x, 11)] = 7f;
                values[grid.index(65, 11)] = 3f;
                RouteCostField cost = new RouteCostField(values, RouteCostField.nextRevision());
                GreedyNavigationMesh.Snapshot mesh = new GreedyNavigationMesh(grid).snapshot();
                SquadRouteRequest request = request(grid, 65, 11, cost, 1, 2, 2, 12, 68, 0);
                SquadRouteField expected = legacyField(grid, mesh, request);
                SquadRouteBuildJob job = new SquadRouteBuildJob(grid);
                job.begin(request, mesh, true);
                finish(job, 3);
                assertEquals(expected.corridorCellCount(), job.corridorCells());
                assertEquals(expected.settledCellCount(), job.field().settledCellCount());
                for (int cell = 0; cell < values.length; cell++) {
                    assertEquals(expected.covers(cell), job.field().covers(cell),
                            "Changed coverage at " + cell + ", cardinal=" + cardinal);
                    if (!expected.covers(cell)) continue;
                    int x = cell % 70, y = cell / 70;
                    int[] expectedPath = expected.extract(x, y);
                    int[] actualPath = job.field().extract(x, y);
                    assertArrayEquals(expectedPath, actualPath, "Changed successor at " + cell);
                    assertEquals(pathCost(expectedPath, grid, cost), pathCost(actualPath, grid, cost));
                    assertPath(grid, actualPath, 65, 11);
                }
            }
        } finally {
            GridPathfinder.USE_CARDINAL_NAVIGATION = cardinalBefore;
        }
    }

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
    void currentComponentProofRejectsWithoutFloodButStaleLabelsDoNotRejectNewConnection() {
        NavigationGrid grid = openGrid(15, 3);
        for (int y = 0; y < 3; y++) grid.setWalkable(7, y, false);
        grid.preparePathComponents(GridPathfinder.USE_CARDINAL_NAVIGATION);
        SquadRouteBuildJob job = new SquadRouteBuildJob(grid);
        job.begin(request(grid, 14, 1, null, 0, 1), new GreedyNavigationMesh(grid).snapshot(), true);
        finish(job, 1);
        assertNull(job.field());
        assertEquals(0, job.seedExpanded());
        grid.setWalkableFloor(7, 1);
        job.begin(request(grid, 14, 1, null, 0, 1), new GreedyNavigationMesh(grid).snapshot(), true);
        finish(job, 1);
        assertNotNull(job.field());
        assertTrue(job.seedExpanded() > 0);
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

    /** Independent old representation: seed-region union, padded once, then sorted cell mask. */
    private static SquadRouteField legacyField(NavigationGrid grid,
                                                GreedyNavigationMesh.Snapshot mesh,
                                                SquadRouteRequest request) {
        boolean[] route = new boolean[mesh.regions().size()];
        boolean[] selected = new boolean[route.length];
        for (int start : request.startCells()) {
            int region = mesh.regionIdAt(start % grid.getWidth(), start / grid.getWidth());
            if (region < 0 || selected[region]) continue;
            int[] seed = GridPathfinder.findSquadRouteSeed(grid,
                    start % grid.getWidth(), start / grid.getWidth(),
                    request.goalX(), request.goalY(), GridPathfinder.USE_CARDINAL_NAVIGATION,
                    request.cost());
            for (int i = 0; i < seed.length; i += 2) {
                route[mesh.regionIdAt(seed[i], seed[i + 1])] = true;
            }
            System.arraycopy(route, 0, selected, 0, route.length);
            for (GreedyNavigationMesh.Transition transition : mesh.transitions()) {
                if (route[transition.regionA()]) selected[transition.regionB()] = true;
                if (route[transition.regionB()]) selected[transition.regionA()] = true;
            }
        }
        int[] cells = new int[grid.getWidth() * grid.getHeight()];
        int count = 0;
        for (int cell = 0; cell < cells.length; cell++) {
            int region = mesh.regionIdAt(cell % grid.getWidth(), cell / grid.getWidth());
            if (region >= 0 && selected[region]) cells[count++] = cell;
        }
        return new SquadRouteField.Builder(grid).build(Arrays.copyOf(cells, count),
                request.cost(), grid.index(request.goalX(), request.goalY()),
                request.startCells(), GridPathfinder.USE_CARDINAL_NAVIGATION);
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
