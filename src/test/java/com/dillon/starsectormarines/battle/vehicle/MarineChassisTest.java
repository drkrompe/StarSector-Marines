package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
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
 * A chassis the player owns: put on the field with no errand, holding until it
 * is told to go somewhere — and only if it is theirs.
 */
class MarineChassisTest {

    private static final int W = 120;
    private static final int H = 60;
    private static final float DT = 1f / 30f;

    private record Rig(ConvoyService convoy, VehicleMoveOrderService orders,
                       VehicleMoveOrderSystem system, GroundBody body) {}

    private static NavigationGrid openField() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 20; y <= 40; y++) {
            for (int x = 5; x <= 110; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    /** A deployed chassis of the given faction at (20.5, 30.5) facing east. */
    private static Rig deployed(Faction faction) {
        NavigationGrid grid = openField();
        NavigationService navigation = new NavigationService(grid, new CellTopology(W, H));
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(W, H), null);
        ConvoyService convoy = roster.convoy();
        long id = convoy.spawn(VehicleType.HEAVY_APC, faction,
                VehicleMission.deployed(20.5f, 30.5f));
        GroundBody body = convoy.body(id);
        body.teleport(20.5f, 30.5f, -90f);
        body.speed = 0f;

        VehicleMoveOrderService orders = new VehicleMoveOrderService();
        VehicleMoveOrderSystem system = new VehicleMoveOrderSystem(orders, convoy, navigation,
                new VehicleControlSystem(convoy, navigation),
                new VehicleTransportService(roster, convoy,
                        roster.entityWorld(), roster.components(), navigation));
        return new Rig(convoy, orders, system, body);
    }

    @Test
    void aDeployedChassisIsOnTheFieldWithNothingToDo() {
        Rig rig = deployed(Faction.MARINE);
        long id = only(rig.convoy());

        assertEquals(VehicleState.DEPLOYED, rig.convoy().mission(id).state);
        assertTrue(rig.convoy().mission(id).isVisible(),
                "a chassis standing on the field is on the field");
        assertTrue(rig.convoy().isTargetable(id),
                "and can be shot at like any other vehicle");
    }

    @Test
    void aDeployedChassisHoldsPositionUntilItIsOrdered() {
        Rig rig = deployed(Faction.MARINE);
        long id = only(rig.convoy());

        for (int i = 0; i < 300; i++) {
            rig.system().tickPending();
            rig.system().executeIfActive(id, DT);
        }

        assertEquals(20.5f, rig.body().x, 0.001f,
                "with no errand and no order there is nowhere for it to be going");
        assertEquals(30.5f, rig.body().y, 0.001f);
    }

    @Test
    void aDeployedChassisGoesWhereItIsSent() {
        Rig rig = deployed(Faction.MARINE);
        long id = only(rig.convoy());
        rig.orders().requestMove(id, 80, 30);

        for (int i = 0; i < 60 * 30; i++) {
            rig.system().tickPending();
            rig.system().executeIfActive(id, DT);
        }

        assertTrue(rig.body().distanceTo(80.5f, 30.5f) < 3f,
                "ordered to (80,30), ended at " + rig.body().x + "," + rig.body().y);
    }

    @Test
    void anEnemyChassisTakesNoOrders() {
        // The defender's APC is a target, not a unit. Orders are queued by id,
        // so this is the boundary that actually has to hold.
        Rig rig = deployed(Faction.DEFENDER);
        long id = only(rig.convoy());
        rig.orders().requestMove(id, 80, 30);

        rig.system().tickPending();

        assertNull(rig.orders().activeOrder(id),
                "somebody else's vehicle must not accept an order");
        VehicleMoveOrderService.RefusedOrder refused = rig.orders().refusal(id);
        assertNotNull(refused, "and the refusal says why");
        assertEquals(Refusal.NOT_COMMANDABLE, refused.reason());
    }

    private static long only(ConvoyService convoy) {
        return convoy.vehicleAt(0);
    }
}
