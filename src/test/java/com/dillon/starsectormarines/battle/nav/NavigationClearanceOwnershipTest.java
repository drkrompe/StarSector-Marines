package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Direct route ownership and monitor isolation; no battle, renderer, or world generation. */
class NavigationClearanceOwnershipTest {
    @Test
    void infantryPathChangesCompleteWhileAnotherThreadOwnsTheClearanceMonitor() throws Exception {
        NavigationGrid grid = floor();
        var routes = new AsyncClearanceRoutes(false);
        try (var navigation = new NavigationService(grid, new CellTopology(12, 8), routes)) {
            UnitRosterService roster = roster(navigation);
            long marine = roster.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 2, 4));
            var workers = Executors.newFixedThreadPool(2);
            var locked = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            try {
                var holder = workers.submit(() -> {
                    synchronized (routes) {
                        locked.countDown();
                        release.await();
                    }
                    return null;
                });
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var changes = workers.submit(() -> {
                    int[] path = {2, 4, 3, 4, 4, 4};
                    navigation.setPath(marine, path);
                    assertArrayEquals(path, roster.world().path(marine));
                    assertTrue(navigation.isCellOccupied(4, 4));
                    navigation.clearPath(marine);
                    assertTrue(Paths.isEmpty(roster.world().path(marine)));
                    assertFalse(navigation.isCellOccupied(4, 4));
                    assertEquals(PathRequestStatus.READY, navigation.requestPath(marine, 9, 4));
                    assertTrue(roster.movement().pathTargetsCell(marine, 9, 4));
                    return null;
                });
                // This is a lock-dependency assertion, not a performance limit:
                // the coordinator cannot be released until the mutations finish.
                changes.get(5, TimeUnit.SECONDS);
                assertFalse(holder.isDone());
                release.countDown();
                holder.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                workers.shutdown();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void clearingAMechDropsItsUnpublishedProofBeforeAReplacementRequest() {
        NavigationGrid grid = floor();
        var routes = new AsyncClearanceRoutes(false);
        try (var navigation = new NavigationService(grid, new CellTopology(12, 8), routes)) {
            UnitRosterService roster = roster(navigation);
            long mech = roster.spawn(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH, 2, 4));
            // Identity, not optional loadout presence, owns clearance routing.
            assertFalse(roster.world().hasMechLoadout(mech));
            float radius = roster.radius(mech);
            var old = routes.pollOrSubmit(mech,
                    new AsyncClearanceRoutes.Request(2.5f, 4.5f, 9, 4, radius), grid);
            assertEquals(PathRequestStatus.READY, old.status());
            assertNull(roster.movement().continuousRoute(mech), "proof has not been published to movement");

            navigation.clearPath(mech);
            roster.world().setPos(mech, 3.5f, 4.5f);
            var replacement = routes.pollOrSubmit(mech,
                    new AsyncClearanceRoutes.Request(3.5f, 4.5f, 9, 4, radius), grid);
            assertEquals(PathRequestStatus.READY, replacement.status());
            assertNotSame(old.proof(), replacement.proof(),
                    "without cancellation a ready proof retains its original start despite body drift");
            assertEquals(3.5f, replacement.proof().waypoints().get(0).x());
            assertEquals(PathRequestStatus.READY, navigation.requestPath(mech, 9, 4));
            assertEquals(3.5f, roster.movement().continuousRoute(mech).x(0));
        }
    }

    private static UnitRosterService roster(NavigationService navigation) {
        var roster = new UnitRosterService(navigation.getUnitIndex(), null);
        roster.setNavigationGrid(navigation.getGrid());
        navigation.setRoster(roster);
        navigation.setOccupancyDeltaSink(navigation::applyOccupancyDeltaInline);
        return roster;
    }

    private static NavigationGrid floor() {
        var grid = new NavigationGrid(12, 8);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
