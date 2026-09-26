package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadReplanSystem;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncDefendTrackRoutesTest {

    @Test
    void productionDefaultsOnAndExplicitSyncControlStartsNoService() {
        String before = System.getProperty(AsyncDefendTrackRoutes.ENABLED_PROPERTY);
        try {
            System.clearProperty(AsyncDefendTrackRoutes.ENABLED_PROPERTY);
            try (BattleSimulation sim = new BattleSimulation(openGrid(),
                    new CellTopology(10, 10))) {
                assertNotNull(sim.asyncDefendTrackRoutes());
            }
            System.setProperty(AsyncDefendTrackRoutes.ENABLED_PROPERTY, "false");
            try (BattleSimulation sim = new BattleSimulation(openGrid(),
                    new CellTopology(10, 10))) {
                assertNull(sim.asyncDefendTrackRoutes());
            }
        } finally {
            if (before == null) System.clearProperty(
                    AsyncDefendTrackRoutes.ENABLED_PROPERTY);
            else System.setProperty(AsyncDefendTrackRoutes.ENABLED_PROPERTY, before);
        }
    }

    @Test
    void coalescesSameMemberAndDropsObsoleteOrderResult() throws Exception {
        NavigationGrid grid = openGrid();
        byte[] occupancy = new byte[100];
        Object action = new Object();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        AsyncDefendTrackRoutes.Request first = request(1L, action, 7, 7);
        AsyncDefendTrackRoutes.Request second = request(1L, action, 8, 8);
        try (AsyncDefendTrackRoutes routes = new AsyncDefendTrackRoutes(
                1, 4, (snapshot, costs, request) -> {
                    started.countDown();
                    try { proceed.await(1, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return GridPathfinder.EMPTY_PATH;
                    }
                    return new int[]{request.startX(), request.startY(),
                            request.goalX(), request.goalY()};
                })) {
            assertFalse(routes.pollOrSubmit(first, 1, grid, occupancy).ready());
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertFalse(routes.pollOrSubmit(first, 1, grid, occupancy).ready());
            assertEquals(1, routes.metrics().submitted());
            routes.pollOrSubmit(second, 2, grid, occupancy);
            proceed.countDown();
            assertArrayEquals(new int[]{1, 1, 8, 8},
                    awaitReady(routes, second, grid, occupancy, 3));
            assertEquals(2, routes.metrics().submitted());
        }
    }

    @Test
    void changedStartCellCannotDeliverRouteFromOldPosition() throws Exception {
        NavigationGrid grid = openGrid();
        byte[] occupancy = new byte[100];
        Object action = new Object();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        AsyncDefendTrackRoutes.Request oldStart = request(4L, action, 8, 8);
        AsyncDefendTrackRoutes.Request newStart = new AsyncDefendTrackRoutes.Request(
                4L, 4, 1L, action, 8, 8, 3, 2, 8, 8, 8, 8, false);
        try (AsyncDefendTrackRoutes routes = new AsyncDefendTrackRoutes(
                1, 4, (snapshot, costs, request) -> {
                    started.countDown();
                    try { proceed.await(1, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return GridPathfinder.EMPTY_PATH;
                    }
                    return new int[]{request.startX(), request.startY(),
                            request.goalX(), request.goalY()};
                })) {
            assertFalse(routes.pollOrSubmit(oldStart, 1, grid, occupancy).ready());
            assertTrue(started.await(1, TimeUnit.SECONDS));
            // A unit moved by another action while its search was pending.
            // Polling from the new cell must retire the old result before
            // any caller can install it.
            assertFalse(routes.pollOrSubmit(newStart, 2, grid, occupancy).ready());
            proceed.countDown();
            assertArrayEquals(new int[]{3, 2, 8, 8},
                    awaitReady(routes, newStart, grid, occupancy, 3));
            assertEquals(2, routes.metrics().submitted());
            assertTrue(routes.metrics().canceled() >= 1);
        }
    }

    @Test
    void cancelOnTacticalInterruptRetiresPendingMember() throws Exception {
        NavigationGrid grid = openGrid();
        byte[] occupancy = new byte[100];
        AsyncDefendTrackRoutes.Request request = request(5L, new Object(), 8, 8);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        try (AsyncDefendTrackRoutes routes = new AsyncDefendTrackRoutes(
                1, 4, (snapshot, costs, ignored) -> {
                    started.countDown();
                    try { proceed.await(1, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return new int[]{1, 1, 8, 8};
                })) {
            routes.pollOrSubmit(request, 1, grid, occupancy);
            assertTrue(started.await(1, TimeUnit.SECONDS));
            routes.cancel(request.member());
            proceed.countDown();
            assertEquals(0, routes.metrics().pendingMembers());
            assertTrue(routes.metrics().canceled() >= 1);
        }
    }

    @Test
    void squadReplanCancelsOldActionRequestBeforeUnitUpdate() {
        String before = System.getProperty(AsyncDefendTrackRoutes.ENABLED_PROPERTY);
        System.setProperty(AsyncDefendTrackRoutes.ENABLED_PROPERTY, "true");
        try (BattleSimulation sim = new BattleSimulation(openGrid(),
                new CellTopology(10, 10))) {
            int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
            long member = sim.spawn(new EntitySpec("defender", Faction.DEFENDER,
                    UnitType.MILITIA, 1, 1).squad(squadId));
            Squad squad = sim.getSquad(squadId);
            squad.aliveMembers = 1;
            squad.assignedObjective = ObjectiveAssignment.defendTrack(
                    squadId, 8, 8);
            AsyncDefendTrackRoutes routes = sim.asyncDefendTrackRoutes();
            routes.pollOrSubmit(new AsyncDefendTrackRoutes.Request(member,
                    squadId, squad.routingEpoch, new Object(), 8, 8,
                    1, 1, 8, 8, 8, 8, false), 1,
                    sim.getGrid(), sim.getOccupancyMap());
            assertEquals(1, routes.metrics().pendingMembers());
            new SquadReplanSystem(sim.getRoster()).tick(sim);
            assertEquals(0, routes.metrics().pendingMembers());
        } finally {
            if (before == null) System.clearProperty(
                    AsyncDefendTrackRoutes.ENABLED_PROPERTY);
            else System.setProperty(AsyncDefendTrackRoutes.ENABLED_PROPERTY, before);
        }
    }

    @Test
    void workerReadsFrozenTopologyAndOccupancyAndNewRevisionReplacesJob()
            throws Exception {
        NavigationGrid grid = openGrid();
        byte[] occupancy = new byte[100];
        occupancy[22] = 4;
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        AtomicInteger observed = new AtomicInteger(-1);
        AsyncDefendTrackRoutes.Request request = request(2L, new Object(), 8, 8);
        try (AsyncDefendTrackRoutes routes = new AsyncDefendTrackRoutes(
                1, 4, (snapshot, costs, ignored) -> {
                    started.countDown();
                    try { proceed.await(1, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return GridPathfinder.EMPTY_PATH;
                    }
                    observed.set((snapshot.isWalkable(2, 2) ? 100 : 0)
                            + (costs[22] & 0xFF));
                    return new int[]{snapshot.isWalkable(2, 2) ? 1 : 0,
                            costs[22] & 0xFF};
                })) {
            routes.pollOrSubmit(request, 1, grid, occupancy);
            assertTrue(started.await(1, TimeUnit.SECONDS));
            grid.setWalkable(2, 2, false);
            occupancy[22] = 9;
            proceed.countDown();
            awaitCompleted(routes);
            assertEquals(104, observed.get());
            // A later request sees the new structural revision, never the
            // original snapshot retained by the first job.
            assertArrayEquals(new int[]{0, 9},
                    awaitReady(routes, request, grid, occupancy, 2));
        }
    }

    @Test
    void unreachableFormationCellFallsBackToAnchorOnWorker() throws Exception {
        NavigationGrid grid = openGrid();
        // Keep the requested formation cell walkable but seal all approaches.
        for (int y = 6; y <= 8; y++) {
            for (int x = 6; x <= 8; x++) {
                if (x != 7 || y != 7) grid.setWalkable(x, y, false);
            }
        }
        AsyncDefendTrackRoutes.Request request = new AsyncDefendTrackRoutes.Request(
                1L, 4, 1L, new Object(), 4, 4,
                1, 1, 7, 7, 4, 4, false);
        try (AsyncDefendTrackRoutes routes = new AsyncDefendTrackRoutes()) {
            int[] path = awaitReady(routes, request, grid, new byte[100], 1);
            assertEquals(4, Paths.destX(path));
            assertEquals(4, Paths.destY(path));
            assertEquals(1, routes.metrics().submitted());
        }
    }

    @Test
    void emptyResultBacksOffAndCloseCancelsUnclaimedWork() throws Exception {
        NavigationGrid grid = openGrid();
        byte[] occupancy = new byte[100];
        AsyncDefendTrackRoutes.Request request = request(3L, new Object(), 8, 8);
        AsyncDefendTrackRoutes routes = new AsyncDefendTrackRoutes(
                1, 4, (snapshot, costs, ignored) -> GridPathfinder.EMPTY_PATH);
        try {
            routes.pollOrSubmit(request, 1, grid, occupancy);
            for (int attempt = 0; attempt < 1000
                    && routes.metrics().noPath() == 0; attempt++) {
                assertFalse(routes.pollOrSubmit(request, 2, grid, occupancy).ready());
                Thread.sleep(1);
            }
            assertEquals(1, routes.metrics().noPath());
            routes.pollOrSubmit(request, 31, grid, occupancy);
            assertEquals(1, routes.metrics().submitted());
            routes.pollOrSubmit(request, 32, grid, occupancy);
            assertEquals(2, routes.metrics().submitted());
            routes.close();
            assertEquals(0, routes.metrics().pendingMembers());
            assertFalse(routes.pollOrSubmit(request, 33, grid, occupancy).ready());
        } finally {
            routes.close();
        }
    }

    private static int[] awaitReady(AsyncDefendTrackRoutes routes,
                                    AsyncDefendTrackRoutes.Request request,
                                    NavigationGrid grid, byte[] occupancy,
                                    int tick) throws Exception {
        for (int attempt = 0; attempt < 1000; attempt++) {
            AsyncDefendTrackRoutes.Result result = routes.pollOrSubmit(
                    request, tick, grid, occupancy);
            if (result.ready()) return result.path();
            Thread.sleep(1);
        }
        throw new AssertionError("route did not complete");
    }

    private static void awaitCompleted(AsyncDefendTrackRoutes routes)
            throws Exception {
        for (int attempt = 0; attempt < 1000; attempt++) {
            if (routes.metrics().completed() > 0) return;
            Thread.sleep(1);
        }
        throw new AssertionError("search did not complete");
    }

    private static AsyncDefendTrackRoutes.Request request(long member,
            Object action, int goalX, int goalY) {
        return new AsyncDefendTrackRoutes.Request(member, 4, 1L, action,
                goalX, goalY, 1, 1, goalX, goalY, goalX, goalY, false);
    }

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
