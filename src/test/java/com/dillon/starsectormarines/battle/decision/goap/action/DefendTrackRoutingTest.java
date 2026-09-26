package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.nav.AsyncDefendTrackRoutes;
import com.dillon.starsectormarines.battle.nav.ControlledDefendRoutes;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefendTrackRoutingTest {
    @Test
    void onlyTrackAndSiteUseAsyncAndSiteHasIndependentControl() {
        String before = System.getProperty(AsyncDefendTrackRoutes.SITE_ENABLED_PROPERTY);
        try {
            System.clearProperty(AsyncDefendTrackRoutes.SITE_ENABLED_PROPERTY);
            assertTrue(action(AssignmentKind.DEFEND_TRACK).usesAsyncRoutes());
            assertTrue(action(AssignmentKind.DEFEND_SITE).usesAsyncRoutes());
            for (AssignmentKind kind : new AssignmentKind[]{AssignmentKind.ADVANCE_TRACK,
                    AssignmentKind.DEFEND_AREA, AssignmentKind.RUSH_OBJECTIVE,
                    AssignmentKind.WITHDRAW}) {
                assertFalse(action(kind).usesAsyncRoutes(), kind.name());
            }
            System.setProperty(AsyncDefendTrackRoutes.SITE_ENABLED_PROPERTY, "false");
            assertFalse(action(AssignmentKind.DEFEND_SITE).usesAsyncRoutes());
            assertTrue(action(AssignmentKind.DEFEND_TRACK).usesAsyncRoutes());
            assertEquals("DefendSite", action(AssignmentKind.DEFEND_SITE).profilingName());
            assertEquals("DefendTrack", action(AssignmentKind.DEFEND_TRACK).profilingName());
            assertEquals("AdvanceTrack", action(AssignmentKind.ADVANCE_TRACK).profilingName());
        } finally {
            if (before == null) System.clearProperty(AsyncDefendTrackRoutes.SITE_ENABLED_PROPERTY);
            else System.setProperty(AsyncDefendTrackRoutes.SITE_ENABLED_PROPERTY, before);
        }
    }

    @Test
    void pendingAndRejectedSiteRoutesNeverSearchInlineAndDeliverWhenReady()
            throws Exception {
        NavigationGrid grid = grid();
        byte[] occupancy = new byte[100];
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        try (AsyncDefendTrackRoutes routes = ControlledDefendRoutes.blocked(started, proceed)) {
            var first = request(1L, 8, 8);
            var queued = request(2L, 8, 8);
            var rejected = request(3L, 8, 8);
            assertFalse(DefendTrack.acquireRoute(routes, first, 1, grid, occupancy, false).ready());
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertFalse(DefendTrack.acquireRoute(routes, first, 1, grid, occupancy, false).ready());
            assertFalse(DefendTrack.acquireRoute(routes, queued, 1, grid, occupancy, false).ready());
            assertFalse(DefendTrack.acquireRoute(routes, rejected, 1, grid, occupancy, false).ready());
            assertEquals(2, routes.metrics().submitted());
            assertEquals(1, routes.metrics().rejected());
            assertEquals(0, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
            proceed.countDown();
            assertArrayEquals(new int[]{1, 1, 8, 8}, awaitRoute(routes, first, grid, occupancy));
            assertArrayEquals(new int[]{1, 1, 8, 8}, awaitRoute(routes, queued, grid, occupancy));
            assertArrayEquals(new int[]{1, 1, 8, 8}, awaitRoute(routes, rejected, grid, occupancy));
            assertEquals(0, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
        } finally {
            proceed.countDown();
            TickInnerProfile.setCurrent(null);
        }
    }

    @Test
    void siteRetargetDiscardsPendingOldDestination() throws Exception {
        NavigationGrid grid = grid();
        byte[] occupancy = new byte[100];
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        try (AsyncDefendTrackRoutes routes = ControlledDefendRoutes.blocked(started, proceed)) {
            var previous = request(1L, 8, 8);
            var current = request(1L, 7, 7);
            assertFalse(DefendTrack.acquireRoute(routes, previous, 1, grid, occupancy, false).ready());
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertFalse(DefendTrack.acquireRoute(routes, current, 2, grid, occupancy, false).ready());
            assertEquals(1, routes.metrics().canceled());
            proceed.countDown();
            assertArrayEquals(new int[]{1, 1, 7, 7}, awaitRoute(routes, current, grid, occupancy));
        } finally {
            proceed.countDown();
        }
    }

    @Test
    void absentServiceAndAudibleBearingKeepImmediateRouting() {
        NavigationGrid grid = grid();
        byte[] occupancy = new byte[100];
        var request = request(1L, 8, 8);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        try (AsyncDefendTrackRoutes routes = ControlledDefendRoutes.blocked(
                new CountDownLatch(1), new CountDownLatch(0))) {
            var synchronous = DefendTrack.acquireRoute(null, request, 1, grid, occupancy, false);
            var audible = DefendTrack.acquireRoute(routes, request, 1, grid, occupancy, true);
            assertTrue(synchronous.ready());
            assertTrue(audible.ready());
            assertFalse(Paths.isEmpty(synchronous.path()));
            assertArrayEquals(synchronous.path(), audible.path());
            assertEquals(0, routes.metrics().submitted());
            assertEquals(2, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
            assertEquals(Set.of("assignment-formation", "audible-formation"),
                    profile.slowPathSearches().stream()
                            .map(TickInnerProfile.PathSearch::routeReason)
                            .collect(Collectors.toSet()));
            profile.recordPathSearch(1L, 2, 2, 3, 3, false, 2, 1);
            assertEquals("", profile.slowPathSearches().stream()
                    .filter(search -> search.startX() == 2).findFirst()
                    .orElseThrow().routeReason(), "route reason must not leak to later work");
        } finally {
            TickInnerProfile.setCurrent(null);
        }
    }

    @Test
    void synchronousAnchorFallbackIsAttributedSeparately() {
        NavigationGrid grid = grid();
        grid.setWalkable(8, 8, false);
        var request = new AsyncDefendTrackRoutes.Request(1L, 1, 1L,
                action(AssignmentKind.DEFEND_SITE), 7, 7,
                1, 1, 8, 8, 7, 7, false);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        try {
            var result = DefendTrack.acquireRoute(null, request, 1,
                    grid, new byte[100], false);
            assertTrue(result.ready());
            assertEquals(7, Paths.destX(result.path()));
            assertEquals(7, Paths.destY(result.path()));
            assertEquals(Set.of("assignment-formation", "assignment-anchor"),
                    profile.slowPathSearches().stream()
                            .map(TickInnerProfile.PathSearch::routeReason)
                            .collect(Collectors.toSet()));
        } finally {
            TickInnerProfile.setCurrent(null);
        }
    }

    private static int[] awaitRoute(AsyncDefendTrackRoutes routes,
            AsyncDefendTrackRoutes.Request request, NavigationGrid grid,
            byte[] occupancy) throws Exception {
        for (int i = 0; i < 1000; i++) {
            var result = DefendTrack.acquireRoute(routes, request, 3, grid, occupancy, false);
            if (result.ready()) return result.path();
            Thread.sleep(1);
        }
        throw new AssertionError("route did not finish");
    }

    private static AsyncDefendTrackRoutes.Request request(long member, int x, int y) {
        return new AsyncDefendTrackRoutes.Request(member, 1, 1L,
                new DefendTrack(AssignmentKind.DEFEND_SITE, x, y), x, y,
                1, 1, x, y, x, y, false);
    }

    private static DefendTrack action(AssignmentKind kind) {
        return new DefendTrack(kind, 8, 8);
    }

    private static NavigationGrid grid() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
