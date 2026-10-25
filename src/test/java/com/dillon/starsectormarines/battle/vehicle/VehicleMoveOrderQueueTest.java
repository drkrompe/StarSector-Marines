package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService.PendingOrder;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The move-order queue: a tick plans only its allowance of routes, the player's hulls before enlisted allied ones and
 * otherwise oldest first, and what does not fit waits at the head of the queue rather than being dropped.
 */
class VehicleMoveOrderQueueTest {

    private static final int W = 120;
    private static final int H = 60;

    @Test
    void aTickPlansItsAllowancePlayerHullsFirstAndTheRestNextTick() {
        assumeTrue(VehicleMoveOrderSystem.PLANS_PER_TICK == 2, "written for the default allowance");
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 20; y <= 40; y++) {
            for (int x = 5; x <= 110; x++) grid.setWalkableFloor(x, y);
        }
        NavigationService navigation = new NavigationService(grid, new CellTopology(W, H));
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(W, H), null);
        ConvoyService convoy = roster.convoy();
        VehicleMoveOrderService orders = new VehicleMoveOrderService();
        VehicleMoveOrderSystem system = new VehicleMoveOrderSystem(orders, convoy, navigation,
                new VehicleControlSystem(convoy, navigation),
                new VehicleTransportService(roster, convoy, roster.entityWorld(), roster.components(), navigation));
        long ally = parked(convoy, Faction.ALLY, 20, 24);
        long first = parked(convoy, Faction.MARINE, 20, 30);
        long second = parked(convoy, Faction.MARINE, 20, 36);
        long third = parked(convoy, Faction.MARINE, 45, 30);
        orders.enlist(ally);

        orders.requestMove(ally, 80, 24);
        orders.requestMove(first, 80, 30);
        orders.requestMove(second, 80, 36);
        orders.requestMove(third, 90, 30);
        system.tickPending();

        assertNotNull(orders.activeOrder(first));
        assertNotNull(orders.activeOrder(second));
        assertNull(orders.activeOrder(third));
        assertNull(orders.activeOrder(ally), "an allied order waits behind the player's, though it was asked first");
        assertTrue(orders.pending(third));
        assertTrue(orders.pending(ally));

        system.tickPending();

        assertNotNull(orders.activeOrder(third));
        assertNotNull(orders.activeOrder(ally));
        assertEquals(0, orders.pendingCount());
    }

    @Test
    void askingAgainKeepsTheQueuePlaceWithTheNewerDestination() {
        VehicleMoveOrderService orders = new VehicleMoveOrderService();
        orders.requestMove(1L, 10, 10);
        orders.requestMove(2L, 20, 20);
        orders.requestMove(1L, 11, 11);

        List<PendingOrder> queue = orders.drainPending();

        assertEquals(2, queue.size());
        assertEquals(1L, queue.get(0).vehicleId);
        assertEquals(11, queue.get(0).cellX);
        assertEquals(2L, queue.get(1).vehicleId);
    }

    @Test
    void aDeferredOrderGoesBackAheadOfLaterOnesAndTakesANewerDestination() {
        VehicleMoveOrderService orders = new VehicleMoveOrderService();
        orders.requestMove(1L, 10, 10);
        orders.requestMove(2L, 20, 20);
        List<PendingOrder> unplanned = orders.drainPending();
        orders.requestMove(3L, 30, 30);
        orders.requestMove(2L, 22, 22);

        orders.defer(unplanned);
        List<PendingOrder> queue = orders.drainPending();

        assertEquals(List.of(1L, 2L, 3L), queue.stream().map(order -> order.vehicleId).toList());
        assertEquals(22, queue.get(1).cellX);
        assertFalse(orders.pending(2L));
    }

    private static long parked(ConvoyService convoy, Faction faction, int x, int y) {
        VehicleMission mission = new VehicleMission(
                new float[]{x - 10f, x + .5f}, new float[]{y + .5f, y + .5f},
                new float[]{x + .5f, x - 10f}, new float[]{y + .5f, y + .5f}, 0f, 4);
        long id = convoy.spawn(VehicleType.HEAVY_APC, faction, mission);
        mission.state = VehicleState.LANDED;
        GroundBody body = convoy.body(id);
        body.teleport(x + .5f, y + .5f, -90f);
        body.speed = 0f;
        return id;
    }
}
