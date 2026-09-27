package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tiny navigation-only maps exercise scheduling rather than elapsed-time thresholds. */
class NavigationServiceSquadRouteWorkBudgetTest {
    private static final String PREFIX = "battle.pathfinding.";
    private static final String ENABLED = PREFIX + "squadRouteWorkBudget";
    private static final String PER_TICK = PREFIX + "squadRouteWorkPerTick";
    private static final String PER_SLICE = PREFIX + "squadRouteWorkPerSlice";
    private static final String PER_REQUEST = PREFIX + "squadRouteWorkPerRequest";
    private static final String RETRY = PREFIX + "squadRouteRetryTicks";
    private static final int WIDTH = 32;
    private static final int HEIGHT = 5;
    private final Map<String, String> previousProperties = new HashMap<>();
    private TickInnerProfile previousProfile;

    @BeforeEach
    void configure() {
        previousProfile = TickInnerProfile.currentIfBound();
        configure(ENABLED, "true");
        configure(PER_TICK, "7");
        configure(PER_SLICE, "3");
        configure(PER_REQUEST, "10000");
        configure(RETRY, "20");
        configure(NavigationService.SQUAD_ROUTE_ADMISSION_PROPERTY, "true");
        configure(NavigationService.RETAIN_SINGLETON_SEEDS_PROPERTY, "true");
        configure(NavigationService.RETAIN_SQUAD_ROUTE_COSTS_PROPERTY, "true");
    }

    @AfterEach
    void restore() {
        previousProperties.forEach((key, value) -> {
            if (value == null) System.clearProperty(key);
            else System.setProperty(key, value);
        });
        if (previousProfile == null) TickInnerProfile.releaseCurrentThread();
        else TickInnerProfile.setCurrent(previousProfile);
    }

    @Test
    void waveSharesAnAggregateBudgetAndEveryRequestEventuallyCompletes() {
        try (NavigationService navigation = navigation()) {
            List<SquadRouteRequest> requests = new ArrayList<>();
            for (int id = 0; id < 5; id++) {
                requests.add(request(id, 1, new Object(), 28, cell(1, 2), null));
            }
            TickInnerProfile profile = new TickInnerProfile();
            TickInnerProfile.setCurrent(profile);
            boolean sawPending = false;
            boolean allReady = false;
            for (int tick = 1; tick <= 2000; tick++) {
                profile.reset();
                navigation.prepareSquadRoutes(requests, tick, 4);
                assertTrue(navigation.lastSquadRouteWorkUnits() <= 7, "aggregate tick budget");
                long sampledWork = 0;
                for (TickInnerProfile.SquadRouteWork sample : profile.slowSquadRouteWork()) {
                    assertTrue(sample.sliceWorkUnits() <= 3, "each request receives at most one quantum");
                    assertTrue(sample.lifetimeWorkUnits() <= 10000);
                    assertEquals("TestMove", sample.action());
                    assertEquals(28, sample.goalX());
                    assertEquals(2, sample.goalY());
                    sampledWork += sample.sliceWorkUnits();
                }
                assertEquals(navigation.lastSquadRouteWorkUnits(), sampledWork,
                        "all five requests fit inside the eight-sample diagnostic retention");
                sawPending |= requests.stream().anyMatch(request -> pending(navigation, request));
                allReady = requests.stream().noneMatch(request -> pending(navigation, request));
                if (allReady) break;
            }
            assertTrue(sawPending, "a long request cannot complete inside one tiny slice");
            assertTrue(allReady, "low-id requests must not starve the rest of the wave");
            assertEquals(0, navigation.activeSquadRouteJobs());
            for (SquadRouteRequest request : requests) assertExtracts(navigation, request, cell(1, 2));
        }
    }

    @Test
    void freshShortRequestChurnCannotStarveAnAlreadyRunningLongRoute() {
        System.setProperty(PER_TICK, "32");
        System.setProperty(PER_SLICE, "32");
        try (NavigationService navigation = navigation()) {
            SquadRouteRequest longRoute = request(1, 1, new Object(), 28, cell(1, 2), null);
            navigation.prepareSquadRoutes(List.of(longRoute), 1, 4);
            assertTrue(pending(navigation, longRoute));
            boolean ready = false;
            long resumedWork = 0;
            TickInnerProfile profile = new TickInnerProfile();
            TickInnerProfile.setCurrent(profile);
            for (int tick = 2; tick <= 100; tick++) {
                List<SquadRouteRequest> requests = new ArrayList<>();
                requests.add(longRoute);
                for (int i = 0; i < 3; i++) {
                    requests.add(request(100 + tick * 3 + i, 1, new Object(),
                            28, cell(24, 2), null));
                }
                profile.reset();
                navigation.prepareSquadRoutes(requests, tick, 4);
                assertTrue(navigation.lastSquadRouteWorkUnits() <= 32);
                for (TickInnerProfile.SquadRouteWork sample : profile.slowSquadRouteWork()) {
                    if (sample.squadId() == longRoute.squadId()) resumedWork += sample.sliceWorkUnits();
                }
                if (!pending(navigation, longRoute)) {
                    ready = true;
                    break;
                }
            }
            assertTrue(resumedWork > 0, "fresh requests cannot permanently win every quantum");
            assertTrue(ready, "a stable old request must finish despite fresh work every tick");
            assertExtracts(navigation, longRoute, cell(1, 2));
        }
    }

    @Test
    void sharedCorridorAndReverseWorkAlsoYieldWithinTheSameBudget() {
        try (NavigationService navigation = navigation()) {
            SquadRouteRequest request = new SquadRouteRequest(1, 1, new Object(),
                    28, 2, new int[]{cell(1, 1), cell(1, 3)}, null, "SharedMove");
            TickInnerProfile profile = new TickInnerProfile();
            TickInnerProfile.setCurrent(profile);
            boolean ready = false;
            long reverseExpanded = 0;
            for (int tick = 1; tick <= 2000; tick++) {
                profile.reset();
                navigation.prepareSquadRoutes(List.of(request), tick, 4);
                assertTrue(navigation.lastSquadRouteWorkUnits() <= 3);
                for (TickInnerProfile.SquadRouteWork sample : profile.slowSquadRouteWork()) {
                    assertTrue(sample.sliceWorkUnits() <= 3);
                    reverseExpanded += sample.sliceReverseExpanded();
                }
                if (!pending(navigation, request)) {
                    ready = true;
                    break;
                }
            }
            assertTrue(ready);
            assertTrue(reverseExpanded > 0, "test must exercise reverse routing, not the singleton shortcut");
            assertExtracts(navigation, request, cell(1, 1));
            assertExtracts(navigation, request, cell(1, 3));
        }
    }

    @Test
    void oneRequestCannotConsumeAnotherRequestsSliceAndPendingPreflightDoesNoSearch() {
        try (NavigationService navigation = navigation()) {
            SquadRouteRequest request = request(1, 1, new Object(), 28, cell(1, 2), null);
            navigation.prepareSquadRoutes(List.of(request), 1, 4);
            assertTrue(navigation.lastSquadRouteWorkUnits() > 0);
            assertTrue(navigation.lastSquadRouteWorkUnits() <= 3, "one quantum per request per tick");
            assertTrue(pending(navigation, request));
            TickInnerProfile profile = new TickInnerProfile();
            TickInnerProfile.setCurrent(profile);
            for (int i = 0; i < 100; i++) assertTrue(pending(navigation, request));
            assertEquals(0, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
            assertEquals(0, profile.countOf(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_FALLBACK));
            assertEquals(0, profile.pathfindExpandedNodes());
        }
    }

    @Test
    void changedIdentityAndMissingProviderCancelOldWork() {
        try (NavigationService navigation = navigation()) {
            SquadRouteRequest old = request(1, 1, new Object(), 28, cell(1, 2), null);
            navigation.prepareSquadRoutes(List.of(old), 1, 4);
            assertTrue(pending(navigation, old));
            SquadRouteRequest changed = request(1, 2, new Object(), 27, cell(1, 2), null);
            navigation.prepareSquadRoutes(List.of(changed), 2, 4);
            assertFalse(pending(navigation, old));
            assertTrue(pending(navigation, changed));
            assertEquals(1, navigation.activeSquadRouteJobs());
            navigation.prepareSquadRoutes(List.of(), 3, 4);
            assertFalse(pending(navigation, changed));
            assertEquals(0, navigation.activeSquadRouteJobs());
            assertEquals(0, navigation.lastSquadRouteWorkUnits());
        }
    }

    @Test
    void topologyRebuildAndCloseRetireInFlightWork() {
        NavigationService navigation = navigation();
        try {
            SquadRouteRequest request = request(1, 1, new Object(), 28, cell(1, 2), null);
            navigation.prepareSquadRoutes(List.of(request), 1, 4);
            assertEquals(1, navigation.activeSquadRouteJobs());
            navigation.getGrid().setWalkable(31, 4, false);
            assertFalse(pending(navigation, request), "raw topology revision invalidates the pending view");
            navigation.rebuildDerivedNavigation();
            assertEquals(0, navigation.activeSquadRouteJobs());
            navigation.prepareSquadRoutes(List.of(request), 2, 4);
            assertTrue(pending(navigation, request));
            navigation.close();
            assertEquals(0, navigation.activeSquadRouteJobs());
            assertFalse(pending(navigation, request));
        } finally {
            navigation.close();
        }
    }

    @Test
    void publishingNewSoftCostSnapshotsDoesNotRestartEverySlice() {
        try (NavigationService navigation = navigation()) {
            Object token = new Object();
            SquadRouteRequest current = null;
            boolean ready = false;
            for (int tick = 1; tick <= 2000; tick++) {
                current = request(1, 1, token, 28, cell(1, 2), costs(1f + tick * 0.001f));
                navigation.prepareSquadRoutes(List.of(current), tick, 4);
                assertTrue(navigation.lastSquadRouteWorkUnits() <= 3);
                if (!pending(navigation, current)) {
                    ready = true;
                    break;
                }
            }
            assertTrue(ready, "immutable initial costs must survive newer soft-cost publications");
            assertExtracts(navigation, current, cell(1, 2));
        }
    }

    @Test
    void movedStartIsCoveredBeforeReadinessInsteadOfLeakingIntoMemberFallback() {
        try (NavigationService navigation = navigation()) {
            Object token = new Object();
            SquadRouteRequest initial = request(1, 1, token, 28, cell(1, 2), null);
            navigation.prepareSquadRoutes(List.of(initial), 1, 4);
            assertTrue(pending(navigation, initial));
            SquadRouteRequest moved = request(1, 1, token, 28, cell(1, 3), null);
            finish(navigation, moved, 2);
            assertExtracts(navigation, moved, cell(1, 3));
        }
    }

    @Test
    void totalLimitBacksOffWithoutFallbackAndRoutineEpochChurnCannotBypassIt() {
        System.setProperty(PER_TICK, "8");
        System.setProperty(PER_SLICE, "8");
        System.setProperty(PER_REQUEST, "16");
        try (NavigationService navigation = navigation()) {
            SquadRouteRequest request = request(1, 1, new Object(), 28, cell(1, 2), null);
            int backoffTick = awaitBackoff(navigation, request);
            for (int tick = backoffTick + 1; tick <= backoffTick + 10; tick++) {
                request = request(1, tick, new Object(), 28, cell(1, 2), costs(1.1f));
                navigation.prepareSquadRoutes(List.of(request), tick, 4);
                assertEquals(0, navigation.lastSquadRouteWorkUnits(), "epoch churn cannot renew exhausted allowance");
                assertTrue(pending(navigation, request), "backoff is waiting, not an unreachable result");
            }
            navigation.prepareSquadRoutes(List.of(request), backoffTick + 30, 4);
            assertTrue(navigation.lastSquadRouteWorkUnits() > 0, "retry resumes after the cooldown");
            assertTrue(pending(navigation, request));
        }
    }

    @Test
    void genuinelyChangedDestinationReleasesBackoff() {
        System.setProperty(PER_TICK, "8");
        System.setProperty(PER_SLICE, "8");
        System.setProperty(PER_REQUEST, "16");
        try (NavigationService navigation = navigation()) {
            SquadRouteRequest old = request(1, 1, new Object(), 28, cell(1, 2), null);
            int backoffTick = awaitBackoff(navigation, old);
            SquadRouteRequest changed = request(1, 2, new Object(), 20, cell(1, 2), null);
            navigation.prepareSquadRoutes(List.of(changed), backoffTick + 1, 4);
            assertTrue(navigation.lastSquadRouteWorkUnits() > 0);
            assertFalse(pending(navigation, old));
            assertTrue(pending(navigation, changed));
        }
    }

    @Test
    void compatibleFieldContinuesServingDuringBudgetedRefresh() {
        System.setProperty(PER_TICK, "10000");
        System.setProperty(PER_SLICE, "10000");
        try (NavigationService navigation = navigation()) {
            SquadRouteRequest old = request(1, 1, new Object(), 28, cell(1, 2), null);
            finish(navigation, old, 1);
            System.setProperty(PER_TICK, "1");
            System.setProperty(PER_SLICE, "1");
            SquadRouteRequest refresh = request(1, 2, new Object(), 28, cell(1, 2), costs(2f));
            navigation.prepareSquadRoutes(List.of(refresh), 100, 4);
            assertTrue(navigation.lastSquadRouteWorkUnits() <= 1);
            assertEquals(1, navigation.activeSquadRouteJobs());
            assertFalse(pending(navigation, refresh), "usable geometry need not wait for soft-cost improvement");
            assertExtracts(navigation, refresh, cell(1, 2));
        }
    }

    private static int awaitBackoff(NavigationService navigation, SquadRouteRequest request) {
        long work = 0;
        for (int tick = 1; tick <= 20; tick++) {
            navigation.prepareSquadRoutes(List.of(request), tick, 4);
            work += navigation.lastSquadRouteWorkUnits();
            assertTrue(work <= 16, "one attempt cannot exceed its lifetime work allowance");
            assertTrue(pending(navigation, request));
            if (navigation.lastSquadRouteWorkUnits() == 0) {
                assertTrue(work > 0);
                return tick;
            }
        }
        throw new AssertionError("exhausted request never entered backoff");
    }

    private static void finish(NavigationService navigation, SquadRouteRequest request, int firstTick) {
        int budget = Integer.parseInt(System.getProperty(PER_TICK));
        for (int tick = firstTick; tick < firstTick + 2000; tick++) {
            navigation.prepareSquadRoutes(List.of(request), tick, 4);
            assertTrue(navigation.lastSquadRouteWorkUnits() <= budget);
            if (!pending(navigation, request)) return;
        }
        throw new AssertionError("bounded request never became ready");
    }

    private static void assertExtracts(NavigationService navigation, SquadRouteRequest request, int start) {
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        int[] path = navigation.findSquadPathToGoal(request.squadId(), request.routingEpoch(),
                request.routeToken(), start % WIDTH, start / WIDTH,
                request.goalX(), request.goalY(), request.cost());
        assertFalse(Paths.isEmpty(path));
        assertEquals(start % WIDTH, path[0]);
        assertEquals(start / WIDTH, path[1]);
        assertEquals(request.goalX(), Paths.destX(path));
        assertEquals(request.goalY(), Paths.destY(path));
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_EXTRACT));
        assertEquals(0, profile.countOf(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_FALLBACK));
    }

    private static NavigationService navigation() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) grid.setWalkableFloor(x, y);
        }
        return new NavigationService(grid, new CellTopology(WIDTH, HEIGHT));
    }

    private static SquadRouteRequest request(int id, long epoch, Object token,
                                             int goalX, int start, RouteCostField cost) {
        return new SquadRouteRequest(id, epoch, token, goalX, 2, new int[]{start}, cost, "TestMove");
    }

    private static boolean pending(NavigationService navigation, SquadRouteRequest request) {
        return navigation.isSquadRoutePending(request.squadId(), request.routingEpoch(),
                request.routeToken(), request.goalX(), request.goalY());
    }

    private static RouteCostField costs(float multiplier) {
        float[] cells = new float[WIDTH * HEIGHT];
        Arrays.fill(cells, multiplier);
        return new RouteCostField(cells, RouteCostField.nextRevision());
    }

    private static int cell(int x, int y) { return y * WIDTH + x; }

    private void configure(String key, String value) {
        previousProperties.put(key, System.getProperty(key));
        System.setProperty(key, value);
    }
}
