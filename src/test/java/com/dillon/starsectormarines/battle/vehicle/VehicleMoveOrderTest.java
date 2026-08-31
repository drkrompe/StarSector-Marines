package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService.Refusal;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A vehicle told to go somewhere. The interesting cases are the ones a
 * dispatched delivery never has to face, because a dispatcher only ever commits
 * to a route it has already proved: a destination with no drivable route to it,
 * and an order that turns out not to work after it was accepted.
 */
class VehicleMoveOrderTest {

    private static final int W = 120;
    private static final int H = 60;
    private static final float DT = 1f / 30f;

    private record Rig(ConvoyService convoy, VehicleMoveOrderService orders,
                       VehicleMoveOrderSystem system, long id, GroundBody body,
                       NavigationGrid grid) {}

    /** An APC parked at (20.5, 30.5) facing east, on whatever grid is given. */
    private static Rig apcOn(NavigationGrid grid, float facingDeg) {
        NavigationService navigation = new NavigationService(grid, new CellTopology(W, H));
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(W, H), null);
        ConvoyService convoy = roster.convoy();
        VehicleMission mission = new VehicleMission(
                new float[]{10.5f, 20.5f}, new float[]{30.5f, 30.5f},
                new float[]{20.5f, 10.5f}, new float[]{30.5f, 30.5f}, 0f, 4);
        long id = convoy.spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);
        mission.state = VehicleState.LANDED;
        GroundBody body = convoy.body(id);
        body.teleport(20.5f, 30.5f, facingDeg);
        body.speed = 0f;

        VehicleMoveOrderService orders = new VehicleMoveOrderService();
        VehicleMoveOrderSystem system = new VehicleMoveOrderSystem(orders, convoy, navigation,
                new VehicleControlSystem(convoy, navigation));
        return new Rig(convoy, orders, system, id, body, grid);
    }

    /** A wide open field the APC can drive anywhere in. */
    private static NavigationGrid openField() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 20; y <= 40; y++) {
            for (int x = 5; x <= 110; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static void run(Rig rig, float seconds) {
        int ticks = (int) (seconds * 30f);
        for (int i = 0; i < ticks; i++) {
            rig.system().tickPending();
            rig.system().executeIfActive(rig.id(), DT);
        }
    }

    @Test
    void anOrderedVehicleDrivesToWhereItWasSent() {
        Rig rig = apcOn(openField(), -90f);
        rig.orders().requestMove(rig.id(), 80, 30);

        run(rig, 60f);

        assertTrue(rig.body().distanceTo(80.5f, 30.5f) < 3f,
                "the APC should end up at the cell it was sent to (at "
                        + rig.body().x + "," + rig.body().y + ")");
        assertNull(rig.orders().activeOrder(rig.id()),
                "an order that completed is no longer active");
        assertNull(rig.orders().refusal(rig.id()), "and completing is not a refusal");
    }

    @Test
    void anOrderIsCarriedOutEvenPointingTheWrongWay() {
        // Facing east, sent west. Nothing about a move order guarantees the
        // vehicle starts pointing at its destination.
        Rig rig = apcOn(openField(), -90f);
        rig.orders().requestMove(rig.id(), 8, 30);

        run(rig, 60f);

        assertTrue(rig.body().x < 14f,
                "the APC should come round and drive west (ended at x=" + rig.body().x + ")");
    }

    @Test
    void aDestinationWithNoDrivableRouteIsRefusedOutright() {
        // A sealed pocket on the far side of the map: reachable for nothing.
        NavigationGrid grid = openField();
        for (int y = 45; y <= 47; y++) {
            for (int x = 100; x <= 102; x++) grid.setWalkableFloor(x, y);
        }
        Rig rig = apcOn(grid, -90f);
        rig.orders().requestMove(rig.id(), 101, 46);

        rig.system().tickPending();

        assertNull(rig.orders().activeOrder(rig.id()),
                "an unreachable destination must not become an active order");
        VehicleMoveOrderService.RefusedOrder refused = rig.orders().refusal(rig.id());
        assertNotNull(refused, "and the refusal has to be reported, not swallowed");
        assertEquals(Refusal.NO_ROUTE, refused.reason());
        assertEquals(101, refused.requestedX());
        assertEquals(46, refused.requestedY());
    }

    @Test
    void aDestinationTooTightForTheChassisBecomesTheNearestOneThatFits() {
        // A one-cell slot a marine could stand in and an APC's footprint cannot
        // occupy. The order is still worth carrying out — the vehicle goes as
        // near as it fits — but where it is actually going must stay
        // distinguishable from what was clicked, or the interface has no way to
        // say "here, not quite there."
        NavigationGrid grid = openField();
        grid.setWalkableFloor(60, 45);
        Rig rig = apcOn(grid, -90f);
        rig.orders().requestMove(rig.id(), 60, 45);

        rig.system().tickPending();

        VehicleMoveOrderService.ActiveOrder order = rig.orders().activeOrder(rig.id());
        assertNotNull(order, "a click near drivable ground is still an order");
        assertEquals(60, order.requestedX(), "the request records what was clicked");
        assertEquals(45, order.requestedY());
        assertTrue(Math.abs(order.destinationY() - 45f) > 1f,
                "and the destination is not that cell, because the chassis does not fit in it"
                        + " (destination y=" + order.destinationY() + ")");
        assertTrue(rig.grid().isWalkable((int) Math.floor(order.destinationX()),
                        (int) Math.floor(order.destinationY())),
                "the destination it picked instead is drivable ground");
    }

    @Test
    void anAcceptedOrderThatStopsConvergingIsGivenUpRatherThanParked() {
        // Accept an ordinary order, then seal the vehicle in so no forward plan
        // exists. The recovery ladder's own terminal state is to hold position
        // for the rest of the battle; an order must not do that silently.
        NavigationGrid grid = openField();
        Rig rig = apcOn(grid, -90f);
        rig.orders().requestMove(rig.id(), 80, 30);
        rig.system().tickPending();
        assertNotNull(rig.orders().activeOrder(rig.id()), "fixture check: the order was accepted");

        // Seal it in where it stands. Nothing forward is drivable any more, so
        // the controller has no plan and never will — the real shape of the
        // permanent hold, rather than a poked field.
        wallBoxAround(rig.grid(), 20, 30, 4);
        run(rig, VehicleMoveOrderSystem.GIVE_UP_SECONDS + 6f);

        assertNull(rig.orders().activeOrder(rig.id()),
                "an order that stopped converging must be released, not held forever");
        VehicleMoveOrderService.RefusedOrder refused = rig.orders().refusal(rig.id());
        assertNotNull(refused, "and it has to say it gave up");
        assertEquals(Refusal.GAVE_UP, refused.reason());
    }

    @Test
    void aWreckedVehicleDropsItsOrder() {
        Rig rig = apcOn(openField(), -90f);
        rig.orders().requestMove(rig.id(), 80, 30);
        rig.system().tickPending();
        assertNotNull(rig.orders().activeOrder(rig.id()));

        rig.convoy().mission(rig.id()).state = VehicleState.WRECKED;
        rig.system().tickPending();

        assertNull(rig.orders().activeOrder(rig.id()),
                "a wreck is not under orders");
    }

    /** Closes a ring of wall {@code radius} cells out from a centre cell. */
    private static void wallBoxAround(NavigationGrid grid, int cx, int cy, int radius) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                boolean onRing = Math.abs(x - cx) == radius || Math.abs(y - cy) == radius;
                if (onRing && grid.inBounds(x, y)) {
                    grid.setTag(x, y, CellTag.WALKABLE, false);
                }
            }
        }
    }
}
