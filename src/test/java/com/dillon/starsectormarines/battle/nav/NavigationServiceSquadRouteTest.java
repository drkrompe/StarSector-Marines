package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationServiceSquadRouteTest {

    @Test
    void greedyRegionsBoundTheFieldAndUnchangedIntentReusesIt() {
        NavigationGrid grid = openGrid(160, 160);
        NavigationService navigation = new NavigationService(grid,
                new CellTopology(160, 160));
        Object token = new Object();
        SquadRouteRequest first = new SquadRouteRequest(7, 3L, token,
                158, 1, new int[]{grid.index(1, 1), grid.index(2, 1)}, null);

        navigation.prepareSquadRoutes(List.of(first));

        assertEquals(1, navigation.preparedSquadRouteCount());
        assertEquals(1, navigation.lastSquadRouteBuilds());
        assertEquals(0, navigation.lastSquadRouteReuses());
        assertTrue(navigation.lastSquadRouteCorridorCells()
                < grid.getWidth() * grid.getHeight() / 2,
                "one mesh-neighbor ring should not open the empty map");
        assertTrue(navigation.lastSquadRouteSettledCells()
                <= navigation.lastSquadRouteCorridorCells());
        int[] path = navigation.findSquadPathToGoal(7, 3L, token,
                1, 1, 158, 1, null);
        assertFalse(Paths.isEmpty(path));
        assertEquals(158, Paths.destX(path));
        assertEquals(1, Paths.destY(path));

        RouteCostField newerCost = new RouteCostField(
                unitCosts(grid), RouteCostField.nextRevision());
        navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(7, 3L,
                token, 158, 1, new int[]{grid.index(3, 1)}, newerCost)));

        assertEquals(0, navigation.lastSquadRouteBuilds(),
                "cost is pinned for the route epoch rather than republished every casualty snapshot");
        assertEquals(1, navigation.lastSquadRouteReuses());
    }

    @Test
    void replannedSameRouteAdoptsCoveredFieldButNewCostOrStartRebuilds() {
        NavigationGrid grid = openGrid(160, 160);
        NavigationService navigation = new NavigationService(grid,
                new CellTopology(160, 160));
        RouteCostField cost = new RouteCostField(
                unitCosts(grid), RouteCostField.nextRevision());
        Object firstToken = new Object();
        navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(7, 1L,
                firstToken, 158, 1, new int[]{grid.index(1, 1)}, cost)));
        assertEquals(1, navigation.lastSquadRouteBuilds());

        Object nextToken = new Object();
        navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(7, 2L,
                nextToken, 158, 1, new int[]{grid.index(2, 1)}, cost)));
        assertEquals(0, navigation.lastSquadRouteBuilds());
        assertEquals(1, navigation.lastSquadRouteReuses());
        assertFalse(Paths.isEmpty(navigation.findSquadPathToGoal(7, 2L,
                nextToken, 2, 1, 158, 1, cost)));

        // Adoption is now fresh for this epoch, even if the producer publishes
        // a newer casualty-cost snapshot before the squad replans again.
        RouteCostField newerCost = new RouteCostField(
                unitCosts(grid), RouteCostField.nextRevision());
        navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(7, 2L,
                nextToken, 158, 1, new int[]{grid.index(3, 1)}, newerCost)));
        assertEquals(0, navigation.lastSquadRouteBuilds());
        assertEquals(1, navigation.lastSquadRouteReuses());

        Object costChangeToken = new Object();
        navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(7, 3L,
                costChangeToken, 158, 1, new int[]{grid.index(3, 1)}, newerCost)));
        assertEquals(1, navigation.lastSquadRouteBuilds(),
                "a new epoch must adopt the current casualty costing");

        navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(7, 4L,
                new Object(), 158, 1, new int[]{grid.index(1, 150)}, newerCost)));
        assertEquals(1, navigation.lastSquadRouteBuilds(),
                "a start outside the settled field needs a new corridor");
    }

    @Test
    void uncoveredStartAndNewStepAreExactFallbacksNotUnreachableAnswers() {
        NavigationGrid grid = openGrid(160, 160);
        NavigationService navigation = new NavigationService(grid,
                new CellTopology(160, 160));
        Object token = new Object();
        navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(4, 9L,
                token, 158, 1, new int[]{grid.index(1, 1)}, null)));

        int[] outsideCorridor = navigation.findSquadPathToGoal(4, 9L, token,
                1, 150, 158, 1, null);
        assertFalse(Paths.isEmpty(outsideCorridor));
        assertEquals(1, Paths.cellX(outsideCorridor, 0));
        assertEquals(150, Paths.cellY(outsideCorridor, 0));
        assertEquals(158, Paths.destX(outsideCorridor));
        assertEquals(1, Paths.destY(outsideCorridor));

        int[] newStep = navigation.findSquadPathToGoal(4, 9L, new Object(),
                1, 1, 158, 1, null);
        assertFalse(Paths.isEmpty(newStep));
        assertEquals(158, Paths.destX(newStep));
    }

    @Test
    void serialBuildBudgetAmortizesAReplanWaveAcrossTicks() {
        NavigationGrid grid = openGrid(160, 160);
        NavigationService navigation = new NavigationService(grid,
                new CellTopology(160, 160));
        int budget = SharedGoalPolicy.maximumSquadRouteBuildsPerTick();
        assertTrue(budget > 0, "shipping default must make forward progress");
        List<SquadRouteRequest> requests = IntStream.range(0, budget + 2)
                .mapToObj(id -> new SquadRouteRequest(id, 1L, new Object(),
                        158, 1, new int[]{grid.index(1, 1)}, null))
                .toList();

        navigation.prepareSquadRoutes(requests);
        assertEquals(budget, navigation.lastSquadRouteBuilds());
        assertEquals(budget, navigation.preparedSquadRouteCount());

        navigation.prepareSquadRoutes(requests);
        assertEquals(2, navigation.lastSquadRouteBuilds());
        assertEquals(budget, navigation.lastSquadRouteReuses());
        assertEquals(budget + 2, navigation.preparedSquadRouteCount());
    }

    @Test
    void failedBuildsAreRememberedSoTheyCannotStarveLaterSquads() {
        NavigationGrid grid = new NavigationGrid(20, 3);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < 5; x++) grid.setWalkableFloor(x, y);
            for (int x = 10; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        NavigationService navigation = new NavigationService(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
        int budget = SharedGoalPolicy.maximumSquadRouteBuildsPerTick();
        List<SquadRouteRequest> requests = IntStream.range(0, budget + 1)
                .mapToObj(id -> id < budget
                        ? new SquadRouteRequest(id, 1L, new Object(), 18, 1,
                        new int[]{grid.index(1, 1)}, null)
                        : new SquadRouteRequest(id, 1L, new Object(), 18, 1,
                        new int[]{grid.index(12, 1)}, null))
                .toList();

        navigation.prepareSquadRoutes(requests);
        assertEquals(0, navigation.lastSquadRouteBuilds());
        assertEquals(budget, navigation.preparedSquadRouteCount(),
                "failed attempts should be retained for this exact intent");

        navigation.prepareSquadRoutes(requests);
        assertEquals(1, navigation.lastSquadRouteBuilds(),
                "remembered misses must leave budget for the later reachable squad");
        assertEquals(budget, navigation.lastSquadRouteReuses());
        assertFalse(Paths.isEmpty(navigation.findSquadPathToGoal(budget, 1L,
                requests.get(budget).routeToken(), 12, 1, 18, 1, null)));
    }

    @Test
    void pinnedNullCostRemainsBaselineForOffCorridorFallback() {
        NavigationGrid grid = openGrid(160, 160);
        NavigationService navigation = new NavigationService(grid,
                new CellTopology(160, 160));
        Object token = new Object();
        navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(8, 2L,
                token, 158, 1, new int[]{grid.index(1, 1)}, null)));

        int[] baseline = GridPathfinder.findPathUnprofiled(grid,
                1, 150, 158, 1, GridPathfinder.USE_CARDINAL_NAVIGATION,
                null, null, null);
        float[] costs = unitCosts(grid);
        for (int cell = 1; cell + 1 < Paths.cellCount(baseline); cell++) {
            costs[grid.index(Paths.cellX(baseline, cell),
                    Paths.cellY(baseline, cell))] = 1000f;
        }
        int[] costAware = GridPathfinder.findPathUnprofiled(grid,
                1, 150, 158, 1, GridPathfinder.USE_CARDINAL_NAVIGATION,
                null, costs, null);
        assertFalse(Arrays.equals(baseline, costAware),
                "the fixture must distinguish baseline from the newer cost field");

        int[] fallback = navigation.findSquadPathToGoal(8, 2L, token,
                1, 150, 158, 1,
                new RouteCostField(costs, RouteCostField.nextRevision()));
        assertArrayEquals(baseline, fallback,
                "null is the pinned baseline snapshot, not permission to read a newer cost");
    }

    @Test
    void topologyRebuildInvalidatesPreparedSquadFields() {
        NavigationGrid grid = openGrid(64, 64);
        NavigationService navigation = new NavigationService(grid,
                new CellTopology(64, 64));
        navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(3, 1L,
                new Object(), 62, 1, new int[]{grid.index(1, 1)}, null)));
        assertEquals(1, navigation.preparedSquadRouteCount());

        navigation.rebuildDerivedNavigation();

        assertEquals(0, navigation.preparedSquadRouteCount());
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static float[] unitCosts(NavigationGrid grid) {
        float[] costs = new float[grid.getWidth() * grid.getHeight()];
        Arrays.fill(costs, 1f);
        return costs;
    }
}
