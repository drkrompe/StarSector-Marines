package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationServiceSquadRouteCostRetentionTest {
    private String previous;

    @BeforeEach
    void enableRetention() {
        previous = System.getProperty(NavigationService.RETAIN_SQUAD_ROUTE_COSTS_PROPERTY);
        System.setProperty(NavigationService.RETAIN_SQUAD_ROUTE_COSTS_PROPERTY, "true");
    }

    @AfterEach
    void restoreRetention() {
        if (previous == null) System.clearProperty(NavigationService.RETAIN_SQUAD_ROUTE_COSTS_PROPERTY);
        else System.setProperty(NavigationService.RETAIN_SQUAD_ROUTE_COSTS_PROPERTY, previous);
    }

    @Test
    void smallCostChangesRebindEpochAndStepWithoutSlidingOriginalDeadline() {
        for (int squad : new int[]{0, 60}) {
            try (NavigationService navigation = navigation()) {
                prepare(navigation, squad, 1, 10, costs(1f));
                int deadline = 10 + 300 + squad;
                for (int tick = 11; tick < deadline; tick++) {
                    SquadRouteRequest request = prepare(navigation, squad, tick, tick, costs(1.1f));
                    assertEquals(0, navigation.lastSquadRouteBuilds());
                    assertEquals(1, navigation.lastSquadRouteReuses());
                    assertFalse(Paths.isEmpty(navigation.findSquadPathToGoal(squad, tick,
                            request.routeToken(), 1, 2, 8, 2, request.cost())));
                }
                prepare(navigation, squad, deadline, deadline, costs(1.1f));
                assertEquals(1, navigation.lastSquadRouteBuilds(), "rebindings must not renew the build age");
            }
        }
    }

    @Test
    void cumulativeSmallIncreasesAreComparedWithBuiltCostRatherThanPreviousSnapshot() {
        try (NavigationService navigation = navigation()) {
            prepare(navigation, 1, 1, 10, costs(1f));
            prepare(navigation, 1, 2, 20, costs(1.1f));
            assertEquals(0, navigation.lastSquadRouteBuilds());
            prepare(navigation, 1, 3, 30, costs(1.2f));
            assertEquals(0, navigation.lastSquadRouteBuilds());
            prepare(navigation, 1, 4, 40, costs(1.25f));
            assertEquals(1, navigation.lastSquadRouteBuilds());
            prepare(navigation, 1, 5, 50, costs(1.4f));
            assertEquals(0, navigation.lastSquadRouteBuilds(), "a real rebuild establishes the new baseline");
        }
    }

    @Test
    void nullMeansBaselineAndCostRemovalWaitsForBoundedReconsideration() {
        try (NavigationService navigation = navigation()) {
            prepare(navigation, 0, 1, 10, null);
            prepare(navigation, 0, 2, 20, costs(1.1f));
            assertEquals(0, navigation.lastSquadRouteBuilds());
            prepare(navigation, 0, 3, 30, costs(2f));
            assertEquals(1, navigation.lastSquadRouteBuilds(), "new local losses cannot inherit baseline forever");
            prepare(navigation, 0, 4, 40, null);
            assertEquals(0, navigation.lastSquadRouteBuilds(), "decay/removal is a soft opportunity to improve");
            prepare(navigation, 0, 5, 330, null);
            assertEquals(1, navigation.lastSquadRouteBuilds());
        }
    }

    @Test
    void sameEpochStillPinsCostUntilReplanningAndControlRestoresIdentityPolicy() {
        try (NavigationService navigation = navigation()) {
            SquadRouteRequest first = prepare(navigation, 0, 1, 10, costs(1f));
            navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(0, 1, first.routeToken(),
                    8, 2, new int[]{21}, costs(4f))), 11);
            assertEquals(0, navigation.lastSquadRouteBuilds());
            prepare(navigation, 0, 2, 12, costs(4f));
            assertEquals(1, navigation.lastSquadRouteBuilds());

            System.setProperty(NavigationService.RETAIN_SQUAD_ROUTE_COSTS_PROPERTY, "false");
            prepare(navigation, 0, 3, 13, costs(4f));
            assertEquals(1, navigation.lastSquadRouteBuilds(), "control rebuilds even numerically identical snapshots");
        }
    }

    @Test
    void expiredCompatibleFieldRemainsUsableWhileAdmissionDefersItsRefresh() {
        try (NavigationService navigation = navigation()) {
            prepare(navigation, 1000, 1, 10, costs(1f));
            SquadRouteRequest old = request(1000, 2, costs(1.1f));
            SquadRouteRequest newcomer = request(0, 1, null);
            navigation.prepareSquadRoutes(List.of(old, newcomer), 400, 1);
            assertEquals(1, navigation.lastSquadRouteDeferred());
            assertFalse(navigation.isSquadRoutePending(1000, 2, old.routeToken(), 8, 2));
            assertFalse(Paths.isEmpty(navigation.findSquadPathToGoal(1000, 2,
                    old.routeToken(), 1, 2, 8, 2, old.cost())));
            navigation.prepareSquadRoutes(List.of(old, newcomer), 401, 1);
            assertEquals(1, navigation.lastSquadRouteBuilds(), "deferred adoption must not renew expiry");
        }
    }

    @Test
    void successorComparisonIgnoresChangedCostsInSettledCellsOutsideActualRoutes() {
        NavigationGrid grid = openGrid();
        int[] starts = {grid.index(1, 2)};
        SquadRouteField field = new SquadRouteField.Builder(grid).build(
                IntStream.range(0, 50).toArray(), null, grid.index(8, 2), starts, true);
        int unrelated = grid.index(8, 1);
        assertTrue(field.covers(unrelated), "fixture needs an explored cell outside the member's route");
        float[] values = new float[50];
        Arrays.fill(values, 1f);
        values[unrelated] = 100f;
        assertFalse(field.hasMaterialCostIncrease(starts, null,
                new RouteCostField(values, RouteCostField.nextRevision()), .25f));
        values = values.clone();
        values[grid.index(5, 2)] = 1.25f;
        assertTrue(field.hasMaterialCostIncrease(starts, null,
                new RouteCostField(values, RouteCostField.nextRevision()), .25f));
    }

    @Test
    void differentGoalTopologyAndUncoveredStartCannotUseCostRetention() {
        try (NavigationService navigation = navigation()) {
            prepare(navigation, 0, 1, 10, null);
            navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(0, 2, new Object(),
                    7, 2, new int[]{21}, costs(1.1f))), 11);
            assertEquals(1, navigation.lastSquadRouteBuilds());
            navigation.getGrid().setWalkable(9, 4, false);
            navigation.rebuildDerivedNavigation();
            navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(0, 3, new Object(),
                    7, 2, new int[]{21}, costs(1.1f))), 12);
            assertEquals(1, navigation.lastSquadRouteBuilds());
        }
        // Start beyond the old reverse field's early stopping boundary.
        try (NavigationService navigation = navigation()) {
            navigation.prepareSquadRoutes(List.of(new SquadRouteRequest(0, 1, new Object(),
                    8, 2, new int[]{27}, null)), 10);
            prepare(navigation, 0, 2, 11, costs(1.1f));
            assertEquals(1, navigation.lastSquadRouteBuilds());
        }
    }

    private static SquadRouteRequest prepare(NavigationService navigation, int squad,
                                              long epoch, int tick, RouteCostField cost) {
        SquadRouteRequest request = request(squad, epoch, cost);
        navigation.prepareSquadRoutes(List.of(request), tick);
        return request;
    }

    private static SquadRouteRequest request(int squad, long epoch, RouteCostField cost) {
        return new SquadRouteRequest(squad, epoch, new Object(), 8, 2, new int[]{21}, cost);
    }

    private static RouteCostField costs(float value) {
        float[] values = new float[50];
        Arrays.fill(values, value);
        return new RouteCostField(values, RouteCostField.nextRevision());
    }

    private static NavigationService navigation() {
        return new NavigationService(openGrid(), new CellTopology(10, 5));
    }

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(10, 5);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 10; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
