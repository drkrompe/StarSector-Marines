package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NavigationServiceSingletonRouteTest {
    private String previous;
    private final TickInnerProfile profile = new TickInnerProfile();

    @BeforeEach
    void enable() {
        previous = System.getProperty(NavigationService.RETAIN_SINGLETON_SEEDS_PROPERTY);
        System.setProperty(NavigationService.RETAIN_SINGLETON_SEEDS_PROPERTY, "true");
        TickInnerProfile.setCurrent(profile);
    }

    @AfterEach
    void restore() {
        if (previous == null) System.clearProperty(NavigationService.RETAIN_SINGLETON_SEEDS_PROPERTY);
        else System.setProperty(NavigationService.RETAIN_SINGLETON_SEEDS_PROPERTY, previous);
        TickInnerProfile.releaseCurrentThread();
    }

    @Test
    void singletonStoresOnlyExactSeedAndSuffixExtractionDoesNotSearch() {
        NavigationGrid grid = openGrid(70, 5);
        try (NavigationService nav = navigation(grid)) {
            SquadRouteRequest request = request(grid, 1, 1, 2, 68, 2);
            nav.prepareSquadRoutes(List.of(request), 1);
            assertEquals(1, count(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_DIRECT));
            assertEquals(0, count(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_REVERSE));
            assertEquals(0, count(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_CORRIDOR));
            assertEquals(68, nav.lastSquadRouteSettledCells());
            assertEquals(68, nav.lastSquadRouteCorridorCells());
            profile.reset();
            int[] suffix = path(nav, request, 30, 2);
            assertEquals(39, Paths.cellCount(suffix));
            assertEquals(1, count(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_EXTRACT));
            assertEquals(0, count(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_FALLBACK));
            assertEquals(0, profile.pathfindExpandedNodes());
        }
    }

    @Test
    void sameInputControlPaysForReverseFieldButNotAnotherSeed() {
        NavigationGrid grid = openGrid(70, 5);
        int[] cells = new int[2];
        int[] expansions = new int[2];
        for (int arm = 0; arm < 2; arm++) {
            System.setProperty(NavigationService.RETAIN_SINGLETON_SEEDS_PROPERTY, Boolean.toString(arm == 1));
            profile.reset();
            try (NavigationService nav = navigation(grid)) {
                SquadRouteRequest request = request(grid, 1, 1, 2, 68, 2);
                nav.prepareSquadRoutes(List.of(request), 1);
                cells[arm] = nav.lastSquadRouteSettledCells();
                expansions[arm] = count(TickInnerProfile.Bucket.SQUAD_ROUTE_SEED_EXPANDED);
                assertEquals(1, count(TickInnerProfile.Bucket.SQUAD_ROUTE_SEED_SEARCH));
                assertEquals(1 - arm, count(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_REVERSE));
                assertEquals(arm, count(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_DIRECT));
                assertEquals(68, Paths.cellCount(path(nav, request, 1, 2)));
            }
        }
        assertEquals(expansions[0], expansions[1]);
        assertTrue(cells[1] < cells[0] / 2);
    }

    @Test
    void offSeedStartFallsBackAndNextReplanPreparesItsOwnRoute() {
        NavigationGrid grid = openGrid(12, 5);
        try (NavigationService nav = navigation(grid)) {
            SquadRouteRequest first = request(grid, 1, 1, 2, 10, 2);
            nav.prepareSquadRoutes(List.of(first), 1);
            profile.reset();
            assertFalse(Paths.isEmpty(path(nav, first, 1, 4)));
            assertEquals("UNCOVERED_START", profile.slowPathSearches().get(0).fallbackReason());
            assertEquals(1, count(TickInnerProfile.Bucket.SQUAD_ROUTE_UNCOVERED_FALLBACK));
            assertTrue(count(TickInnerProfile.Bucket.SQUAD_ROUTE_UNCOVERED_EXPANDED) > 0);
            assertFalse(nav.isSquadRoutePending(1, first.routingEpoch(), first.routeToken(), 10, 2));
            SquadRouteRequest moved = request(grid, 2, 1, 4, 10, 2);
            nav.prepareSquadRoutes(List.of(moved), 2);
            assertEquals(1, nav.lastSquadRouteBuilds());
            profile.reset();
            assertFalse(Paths.isEmpty(path(nav, moved, 1, 4)));
            assertEquals(1, count(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_EXTRACT));
        }
    }

    @Test
    void growingRequestBuildsSharedCoverageWhenNewMemberIsOffSeed() {
        NavigationGrid grid = openGrid(12, 5);
        try (NavigationService nav = navigation(grid)) {
            nav.prepareSquadRoutes(List.of(request(grid, 1, 1, 2, 10, 2)), 1);
            profile.reset();
            nav.prepareSquadRoutes(List.of(new SquadRouteRequest(1, 2, new Object(), 10, 2,
                    new int[]{grid.index(1, 2), grid.index(1, 4)}, null)), 2);
            assertEquals(1, count(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_REVERSE));
            assertEquals(0, count(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_DIRECT));
        }
    }

    @Test
    void weightedSeedPreservesObstacleAndCostAwareDetour() {
        NavigationGrid grid = openGrid(12, 5);
        grid.setSharedEdgePassable(5, 2, Direction.E, false);
        float[] multipliers = new float[60];
        Arrays.fill(multipliers, 1f);
        multipliers[grid.index(4, 2)] = 100f;
        RouteCostField costs = new RouteCostField(multipliers, RouteCostField.nextRevision());
        int[] expected = GridPathfinder.findSquadRouteSeed(grid, 1, 2, 10, 2,
                GridPathfinder.USE_CARDINAL_NAVIGATION, costs);
        try (NavigationService nav = navigation(grid)) {
            SquadRouteRequest request = new SquadRouteRequest(1, 1, new Object(), 10, 2,
                    new int[]{grid.index(1, 2)}, costs);
            nav.prepareSquadRoutes(List.of(request), 1);
            assertArrayEquals(expected, path(nav, request, 1, 2));
        }
    }

    @Test
    void unreachableSeedIsMemoizedAndAlreadyArrivedHasOneCell() {
        NavigationGrid grid = openGrid(5, 1);
        grid.setSharedEdgePassable(2, 0, Direction.E, false);
        try (NavigationService nav = navigation(grid)) {
            SquadRouteRequest request = request(grid, 1, 1, 0, 4, 0);
            nav.prepareSquadRoutes(List.of(request), 1);
            nav.prepareSquadRoutes(List.of(request), 2);
            assertEquals(0, nav.lastSquadRouteAdmissions());
            assertEquals(0, count(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_DIRECT));
            SquadRouteRequest arrived = request(grid, 2, 4, 0, 4, 0);
            nav.prepareSquadRoutes(List.of(arrived), 3);
            assertArrayEquals(new int[]{4, 0}, path(nav, arrived, 4, 0));
            assertEquals(1, nav.lastSquadRouteSettledCells());
        }
    }

    private int count(TickInnerProfile.Bucket bucket) { return profile.countOf(bucket); }

    private static SquadRouteRequest request(NavigationGrid grid, int epoch, int x, int y, int gx, int gy) {
        return new SquadRouteRequest(1, epoch, new Object(), gx, gy, new int[]{grid.index(x, y)}, null);
    }

    private static int[] path(NavigationService nav, SquadRouteRequest request, int x, int y) {
        return nav.findSquadPathToGoal(request.squadId(), request.routingEpoch(), request.routeToken(),
                x, y, request.goalX(), request.goalY(), request.cost());
    }

    private static NavigationService navigation(NavigationGrid grid) {
        return new NavigationService(grid, new CellTopology(grid.getWidth(), grid.getHeight()));
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
