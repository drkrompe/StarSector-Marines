package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class NavigationServiceSquadRouteDiagnosticsTest {
    @AfterEach
    void releaseProfile() {
        TickInnerProfile.releaseCurrentThread();
    }

    @Test
    void seedsHaveTheirOwnWorkCountsAndBuildGeometry() {
        NavigationGrid grid = openGrid(70, 5);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        try (NavigationService navigation = new NavigationService(grid, new CellTopology(70, 5))) {
            Object token = new Object();
            navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(9, 1, token,
                    68, 2, new int[]{grid.index(1, 2), grid.index(2, 2)}, null)), 10);
            var build = profile.slowSquadRouteBuilds().get(0);
            assertEquals("NEW", build.reason());
            assertEquals(9, build.squadId());
            assertEquals(2, build.startCount());
            assertEquals(67, build.maxStartGoalManhattan());
            assertEquals(1, build.seedSearches(), "covered member does not need another seed");
            assertTrue(build.seedExpanded() > 0);
            assertTrue(build.seedPathCells() > 0);
            assertTrue(build.unpaddedCells() <= build.corridorCells());
            assertEquals(navigation.lastSquadRouteSettledCells(), build.settledCells());
            assertEquals(build.seedExpanded(), profile.countOf(TickInnerProfile.Bucket.SQUAD_ROUTE_SEED_EXPANDED));
            assertEquals(0, profile.pathfindExpandedNodes(), "seed work is not flat member A*");
            profile.reset();
            navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(9, 1, token,
                    68, 2, new int[]{grid.index(1, 2)}, null)), 11);
            assertTrue(profile.slowSquadRouteBuilds().isEmpty());
        }
    }

    @Test
    void earlySeedRejectionDoesNotInheritPreviousExpansions() {
        NavigationGrid grid = openGrid(8, 3);
        GridPathfinder.findSquadRouteSeed(grid, 1, 1, 6, 1, true, null);
        assertTrue(GridPathfinder.squadSeedExpandedNodes() > 0);
        GridPathfinder.findSquadRouteSeed(grid, -1, 1, 6, 1, true, null);
        assertEquals(0, GridPathfinder.squadSeedExpandedNodes());
    }

    @Test
    void identicalRequestStreamMeasuresAvoidedBuildsAgainstSameBuildControl() {
        String property = NavigationService.RETAIN_SQUAD_ROUTE_COSTS_PROPERTY;
        String previous = System.getProperty(property);
        try {
            int[] builds = new int[2];
            int[] expansions = new int[2];
            for (int arm = 0; arm < 2; arm++) {
                System.setProperty(property, Boolean.toString(arm == 1));
                NavigationGrid grid = openGrid(70, 5);
                TickInnerProfile profile = new TickInnerProfile();
                TickInnerProfile.setCurrent(profile);
                try (NavigationService navigation = new NavigationService(grid, new CellTopology(70, 5))) {
                    for (int tick = 0; tick <= 600; tick += 60) {
                        float[] values = new float[350];
                        Arrays.fill(values, 2f - tick / 1200f);
                        RouteCostField cost = new RouteCostField(values, RouteCostField.nextRevision());
                        Object token = new Object();
                        navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(0, tick, token,
                                68, 2, new int[]{grid.index(1, 2)}, cost)), tick);
                        builds[arm] += navigation.lastSquadRouteBuilds();
                        assertFalse(Paths.isEmpty(navigation.findSquadPathToGoal(0, tick, token,
                                1, 2, 68, 2, cost)));
                    }
                }
                expansions[arm] = profile.countOf(TickInnerProfile.Bucket.SQUAD_ROUTE_SEED_EXPANDED);
            }
            assertArrayEquals(new int[]{11, 3}, builds);
            assertTrue(expansions[1] < expansions[0] / 2,
                    "route checks must not hide replacement seed A*");
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
