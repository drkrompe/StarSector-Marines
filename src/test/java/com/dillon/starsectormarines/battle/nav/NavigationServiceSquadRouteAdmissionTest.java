package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationServiceSquadRouteAdmissionTest {
    private final String previous = System.getProperty(NavigationService.SQUAD_ROUTE_ADMISSION_PROPERTY);

    @BeforeEach
    void enableAdmission() {
        System.setProperty(NavigationService.SQUAD_ROUTE_ADMISSION_PROPERTY, "true");
    }

    @AfterEach
    void restore() {
        if (previous == null) System.clearProperty(NavigationService.SQUAD_ROUTE_ADMISSION_PROPERTY);
        else System.setProperty(NavigationService.SQUAD_ROUTE_ADMISSION_PROPERTY, previous);
        TickInnerProfile.releaseCurrentThread();
    }

    @Test
    void onlyBudgetDeferredExactIntentsArePendingAndPreflightDoesNoSearch() {
        try (NavigationService navigation = navigation()) {
            List<SquadRouteRequest> requests = wave(budget() + 2, 1, 6);
            TickInnerProfile profile = new TickInnerProfile();
            TickInnerProfile.setCurrent(profile);
            navigation.prepareSquadRoutes(requests, 10);
            assertEquals(budget(), navigation.lastSquadRouteAdmissions());
            assertEquals(budget(), navigation.lastSquadRouteBuilds());
            assertEquals(2, navigation.lastSquadRoutePending());
            assertEquals(0, navigation.lastSquadRouteOldestWaitTicks());
            assertEquals(2, profile.countOf(TickInnerProfile.Bucket.SQUAD_ROUTE_PENDING_REQUEST));
            assertEquals(budget(), profile.countOf(TickInnerProfile.Bucket.SQUAD_ROUTE_ADMITTED));
            profile.reset();
            for (int repeat = 0; repeat < 100; repeat++) {
                for (int i = 0; i < requests.size(); i++) {
                    assertEquals(i >= budget(), pending(navigation, requests.get(i)));
                }
            }
            assertEquals(0, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
            assertEquals(0, profile.pathfindExpandedNodes());
            SquadRouteRequest deferred = requests.get(budget());
            assertFalse(navigation.isSquadRoutePending(deferred.squadId(), 2,
                    deferred.routeToken(), 6, 1));
            assertFalse(navigation.isSquadRoutePending(deferred.squadId(), 1,
                    new Object(), 6, 1));
            assertFalse(navigation.isSquadRoutePending(deferred.squadId(), 1,
                    deferred.routeToken(), 5, 1));
            assertFalse(navigation.isSquadRoutePending(1000, 1, deferred.routeToken(), 6, 1));
        }
    }

    @Test
    void nextPreparationAdmitsWaitersAndReportsReadinessNotJustAttempts() {
        try (NavigationService navigation = navigation()) {
            List<SquadRouteRequest> requests = wave(budget() + 2, 1, 6);
            navigation.prepareSquadRoutes(requests, 10);
            navigation.prepareSquadRoutes(requests, 13);
            assertEquals(2, navigation.lastSquadRouteAdmissions());
            assertEquals(2, navigation.lastSquadRouteResumed());
            assertEquals(0, navigation.lastSquadRoutePending());
            assertEquals(3, navigation.lastSquadRouteAdmittedWaitTicks());
            SquadRouteRequest request = requests.get(budget());
            assertFalse(pending(navigation, request));
            TickInnerProfile profile = new TickInnerProfile();
            TickInnerProfile.setCurrent(profile);
            assertTrue(path(navigation, request, 1).length > 0);
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_EXTRACT));
            assertEquals(0, profile.countOf(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_FALLBACK));
        }
    }

    @Test
    void oldestHighIdBeatsFreshLowIdChurnAndConsumesLatestCostAndStart() {
        try (NavigationService navigation = navigation()) {
            Object highToken = new Object();
            SquadRouteRequest high = request(1000, 1, highToken, 6, 1, null);
            List<SquadRouteRequest> first = new ArrayList<>(wave(budget(), 1, 6));
            first.add(high);
            navigation.prepareSquadRoutes(first, 10);
            assertTrue(pending(navigation, high));

            float[] costs = new float[24];
            Arrays.fill(costs, 1f);
            costs[11] = costs[12] = 1000f;
            RouteCostField latest = new RouteCostField(costs, RouteCostField.nextRevision());
            high = request(1000, 1, highToken, 6, 2, latest);
            List<SquadRouteRequest> next = new ArrayList<>(wave(budget(), 2, 5));
            next.add(high);
            navigation.prepareSquadRoutes(next, 12);

            assertFalse(pending(navigation, high), "old high-id request precedes fresh low-id requests");
            assertEquals(1, navigation.lastSquadRouteResumed());
            assertEquals(2, navigation.lastSquadRouteAdmittedWaitTicks());
            assertEquals(1, navigation.lastSquadRoutePending());
            int[] path = path(navigation, high, 2);
            assertEquals(2, path[0]);
            assertEquals(6, Paths.destX(path));
            for (int i = 0; i < path.length; i += 2) {
                assertFalse(path[i + 1] == 1 && (path[i] == 3 || path[i] == 4),
                        "admission uses latest cost, not the first waiting snapshot");
            }
        }
    }

    @Test
    void changedIntentAndMissingProviderRetireWaitingAge() {
        try (NavigationService navigation = navigation()) {
            List<SquadRouteRequest> first = wave(budget() + 1, 1, 6);
            SquadRouteRequest old = first.get(budget());
            navigation.prepareSquadRoutes(first, 10);
            assertTrue(pending(navigation, old));
            List<SquadRouteRequest> changed = wave(budget() + 1, 2, 5);
            navigation.prepareSquadRoutes(changed, 20);
            assertFalse(pending(navigation, old));
            assertTrue(pending(navigation, changed.get(budget())));
            assertEquals(0, navigation.lastSquadRouteOldestWaitTicks(), "new identity gets a fresh queue age");
            navigation.prepareSquadRoutes(List.of(), 21);
            assertFalse(pending(navigation, changed.get(budget())));
            assertEquals(0, navigation.lastSquadRoutePending());
        }
    }

    @Test
    void failedAdmissionIsNotResumptionAndKeepsOrdinaryFallback() {
        try (NavigationService navigation = navigation()) {
            List<SquadRouteRequest> requests = new ArrayList<>(wave(budget(), 1, 6));
            SquadRouteRequest bad = request(1000, 1, new Object(), -1, 1, null);
            requests.add(bad);
            navigation.prepareSquadRoutes(requests, 10);
            assertTrue(pending(navigation, bad));
            navigation.prepareSquadRoutes(requests, 11);
            assertFalse(pending(navigation, bad));
            assertEquals(1, navigation.lastSquadRouteAdmissions());
            assertEquals(0, navigation.lastSquadRouteBuilds());
            assertEquals(0, navigation.lastSquadRouteResumed());
            assertEquals(1, navigation.lastSquadRouteAdmittedWaitTicks());
            TickInnerProfile profile = new TickInnerProfile();
            TickInnerProfile.setCurrent(profile);
            assertEquals(0, path(navigation, bad, 1).length);
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_FALLBACK));
        }
    }

    @Test
    void compatibleRefreshRemainsUsableWhileNewIntentsConsumeBudget() {
        try (NavigationService navigation = navigation()) {
            SquadRouteRequest old = request(1000, 1, new Object(), 6, 1, null);
            navigation.prepareSquadRoutes(List.of(old), 1);
            float[] costs = new float[24];
            Arrays.fill(costs, 2f);
            SquadRouteRequest refresh = request(1000, 2, new Object(), 6, 1,
                    new RouteCostField(costs, RouteCostField.nextRevision()));
            List<SquadRouteRequest> next = new ArrayList<>(wave(budget(), 1, 5));
            next.add(refresh);
            navigation.prepareSquadRoutes(next, 2);
            assertEquals(1, navigation.lastSquadRouteDeferred());
            assertEquals(0, navigation.lastSquadRoutePending());
            assertFalse(pending(navigation, refresh));
            assertTrue(path(navigation, refresh, 1).length > 0);
        }
    }

    @Test
    void controlAndTopologyResetClearPendingWithoutReclassifyingUnpreparedCalls() {
        try (NavigationService navigation = navigation()) {
            List<SquadRouteRequest> requests = wave(budget() + 1, 1, 6);
            SquadRouteRequest deferred = requests.get(budget());
            navigation.prepareSquadRoutes(requests, 1);
            navigation.getGrid().setWalkable(7, 2, false);
            assertFalse(pending(navigation, deferred), "raw topology change invalidates the frozen pending intent");
            navigation.rebuildDerivedNavigation();
            assertFalse(pending(navigation, deferred));
            assertEquals(1, navigation.lastSquadRoutePending(), "flush retains last-preparation diagnostics");
            navigation.prepareSquadRoutes(requests, 2);
            assertTrue(pending(navigation, deferred));
            System.setProperty(NavigationService.SQUAD_ROUTE_ADMISSION_PROPERTY, "false");
            // Replacing every goal prevents the existing successful fields being reused.
            requests = wave(budget() + 1, 2, 5);
            deferred = requests.get(budget());
            navigation.prepareSquadRoutes(requests, 3);
            assertEquals(0, navigation.lastSquadRoutePending());
            assertFalse(pending(navigation, deferred));
            TickInnerProfile profile = new TickInnerProfile();
            TickInnerProfile.setCurrent(profile);
            assertTrue(path(navigation, deferred, 1).length > 0);
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_FALLBACK));
            assertEquals("MISSING_FIELD", profile.slowPathSearches().get(0).fallbackReason());
        }
    }

    @Test
    void zeroBuildAllowanceRetiresWaitersAndPreservesLegacyFallback() {
        try (NavigationService navigation = navigation()) {
            List<SquadRouteRequest> requests = wave(2, 1, 6);
            SquadRouteRequest deferred = requests.get(1);
            navigation.prepareSquadRoutes(requests, 1, 1);
            assertTrue(pending(navigation, deferred));

            navigation.prepareSquadRoutes(requests, 2, 0);
            assertEquals(0, navigation.lastSquadRouteAdmissions());
            assertEquals(0, navigation.lastSquadRouteBuilds());
            assertEquals(0, navigation.lastSquadRoutePending());
            assertFalse(pending(navigation, deferred), "zero allowance must not create an endless wait");
            TickInnerProfile profile = new TickInnerProfile();
            TickInnerProfile.setCurrent(profile);
            assertTrue(path(navigation, deferred, 1).length > 0);
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_FALLBACK));
            assertEquals("MISSING_FIELD", profile.slowPathSearches().get(0).fallbackReason());
        }
    }

    private static NavigationService navigation() {
        NavigationGrid grid = new NavigationGrid(8, 3);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 8; x++) grid.setWalkableFloor(x, y);
        }
        return new NavigationService(grid, new CellTopology(8, 3));
    }

    private static int budget() {
        int value = SharedGoalPolicy.maximumSquadRouteBuildsPerTick();
        assertTrue(value > 0);
        return value;
    }

    private static List<SquadRouteRequest> wave(int count, long epoch, int goalX) {
        List<SquadRouteRequest> requests = new ArrayList<>();
        for (int id = 0; id < count; id++) requests.add(request(id, epoch, new Object(), goalX, 1, null));
        return requests;
    }

    private static SquadRouteRequest request(int id, long epoch, Object token,
                                              int goalX, int startX, RouteCostField cost) {
        return new SquadRouteRequest(id, epoch, token, goalX, 1, new int[]{8 + startX}, cost);
    }

    private static boolean pending(NavigationService navigation, SquadRouteRequest request) {
        return navigation.isSquadRoutePending(request.squadId(), request.routingEpoch(),
                request.routeToken(), request.goalX(), request.goalY());
    }

    private static int[] path(NavigationService navigation, SquadRouteRequest request, int startX) {
        return navigation.findSquadPathToGoal(request.squadId(), request.routingEpoch(),
                request.routeToken(), startX, 1, request.goalX(), request.goalY(), request.cost());
    }
}
