package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService.RequestedOrder;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent.Recovery;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Manual ownership over the ordinary mailbox and controller, without a full battle. */
class VehicleManualOrderTest {
    private static final int WIDTH = 80, HEIGHT = 50;
    private final NavigationGrid grid = field();
    private final NavigationService navigation = new NavigationService(grid, new CellTopology(WIDTH, HEIGHT));
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(WIDTH, HEIGHT), null);
    private final ConvoyService convoy = roster.convoy();
    private final VehicleTransportService transport = new VehicleTransportService(roster, convoy,
            roster.entityWorld(), roster.components(), navigation);
    private final VehicleMoveOrderService orders = new VehicleMoveOrderService();
    private final VehicleControlSystem control = new VehicleControlSystem(convoy, navigation);
    private final VehicleMoveOrderSystem system = new VehicleMoveOrderSystem(
            orders, convoy, navigation, control, transport);
    private final long vehicle = spawn(Faction.MARINE);
    private final GroundBody body = convoy.body(vehicle);

    @Test
    void takeoverDiscardsRouteAndRecoveryWithoutMovingOrStoppingTheHull() {
        orders.requestMove(vehicle, 65, 25);
        system.tickPending();
        ActiveOrder old = orders.activeOrder(vehicle);
        assertNotNull(old);
        system.executeIfActive(vehicle, 1f / 30f);
        var state = convoy.control(vehicle);
        assertNotNull(state.corridor);
        state.recovery = Recovery.REVERSING;
        state.localPlanFailureTime = 7f;
        body.speed = 2f;
        float x = body.x, y = body.y, heading = body.facingDegrees;

        assertTrue(system.suspendForManual(vehicle));

        assertTrue(orders.isSuspended(vehicle));
        assertNull(orders.activeOrder(vehicle));
        assertEquals(new RequestedOrder(65, 25), orders.suspendedOrder(vehicle));
        assertNull(state.routeXs);
        assertNull(state.corridor);
        assertNull(state.trajectory);
        assertEquals(0f, state.localPlanFailureTime);
        assertEquals(x, body.x);
        assertEquals(y, body.y);
        assertEquals(heading, body.facingDegrees);
        assertEquals(2f, body.speed);
        assertTrue(system.executeIfActive(vehicle, 1f / 30f));
        assertEquals(x, body.x, "suspended order consumes its slot without driving");
    }

    @Test
    void latestRequestSurvivesHandbackAndReplansFromActualPose() {
        orders.requestMove(vehicle, 65, 25);
        system.tickPending();
        ActiveOrder old = orders.activeOrder(vehicle);
        assertTrue(system.suspendForManual(vehicle));
        orders.requestMove(vehicle, 60, 20);
        system.tickPending();
        assertEquals(new RequestedOrder(60, 20), orders.suspendedOrder(vehicle));
        assertNull(convoy.control(vehicle).routeXs);
        body.teleport(35.5f, 25.5f, -90f);
        orders.requestMove(vehicle, 55, 25); // queued immediately before exit, not yet drained

        system.resumeFromManual(vehicle);
        system.resumeFromManual(vehicle); // release is idempotent
        assertFalse(orders.isSuspended(vehicle));
        assertNull(orders.activeOrder(vehicle), "exit does not perform a synchronous route proof");
        assertNull(orders.suspendedOrder(vehicle));
        system.tickPending();

        ActiveOrder resumed = orders.activeOrder(vehicle);
        assertNotNull(resumed);
        assertEquals(55, resumed.requestedX());
        assertEquals(25, resumed.requestedY());
        assertNotSame(old.routeXs(), resumed.routeXs());
        assertEquals(35.5f, resumed.routeXs()[0]);
        assertEquals(25.5f, resumed.routeYs()[0]);
        assertEquals(35.5f, body.x, "replanning cannot move the body");
    }

    @Test
    void unreachableRequestIsRetainedWithoutProofUntilHandback() {
        for (int y = 2; y < HEIGHT - 2; y++) grid.setWalkable(40, y, false);
        assertTrue(system.suspendForManual(vehicle));
        orders.requestMove(vehicle, 65, 25);
        system.tickPending();
        assertEquals(new RequestedOrder(65, 25), orders.suspendedOrder(vehicle));
        assertNull(orders.refusal(vehicle));
        assertTrue(system.executeIfActive(vehicle, 5f));
        assertEquals(20.5f, body.x);
        system.resumeFromManual(vehicle);
        system.tickPending();
        assertNull(orders.activeOrder(vehicle));
        assertEquals(VehicleMoveOrderService.Refusal.NO_ROUTE, orders.refusal(vehicle).reason());
    }

    @Test
    void loadedSelfClickDuringControlNeverBecomesAnUnloadOnHandback() {
        int squadId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
        long passenger = roster.spawn(new EntitySpec("passenger", Faction.MARINE,
                UnitType.MARINE, 21, 25).squad(squadId));
        assertEquals(1, transport.mountSquad(vehicle, squadId));
        assertTrue(system.suspendForManual(vehicle));
        orders.requestMove(vehicle, 20, 25);
        system.tickPending();
        assertEquals(List.of(passenger), transport.manifest(vehicle));
        system.resumeFromManual(vehicle);
        system.tickPending();
        assertEquals(List.of(passenger), transport.manifest(vehicle));
        assertNull(orders.activeOrder(vehicle), "already at the retained destination means deployed hold");
        assertNull(orders.refusal(vehicle));
    }

    @Test
    void noPriorOrderReturnsToDeployedHold() {
        assertTrue(system.suspendForManual(vehicle));
        assertNull(orders.suspendedOrder(vehicle));
        system.resumeFromManual(vehicle);
        system.tickPending();
        assertFalse(system.executeIfActive(vehicle, 1f / 30f));
        assertNull(orders.activeOrder(vehicle));
        assertNull(orders.refusal(vehicle));
        assertEquals(VehicleState.DEPLOYED, convoy.mission(vehicle).state);
        assertEquals(20.5f, body.x);
    }

    @Test
    void leavingDeployedStateForgetsRetainedAndQueuedIntent() {
        assertTrue(system.suspendForManual(vehicle));
        orders.requestMove(vehicle, 65, 25);
        system.tickPending();
        orders.requestMove(vehicle, 55, 25);
        convoy.mission(vehicle).state = VehicleState.WRECKED;
        system.tickPending();
        assertFalse(orders.isSuspended(vehicle));
        assertNull(orders.suspendedOrder(vehicle));
        assertNull(orders.activeOrder(vehicle));
        assertNull(orders.refusal(vehicle));
        system.resumeFromManual(vehicle);
        convoy.mission(vehicle).state = VehicleState.DEPLOYED;
        system.tickPending();
        assertNull(orders.activeOrder(vehicle), "invalidated ownership cannot resurrect a queued route");
    }

    @Test
    void onlyLiveFriendlyDeployedVehiclesCanSuspend() {
        long enemy = spawn(Faction.DEFENDER);
        assertFalse(system.suspendForManual(enemy));
        assertFalse(system.suspendForManual(Long.MAX_VALUE));
        convoy.mission(vehicle).state = VehicleState.LANDED;
        assertFalse(system.suspendForManual(vehicle));
        convoy.mission(vehicle).state = VehicleState.DEPLOYED;
        roster.world().setHp(vehicle, 0f);
        assertFalse(system.suspendForManual(vehicle));
    }

    private long spawn(Faction faction) {
        long id = convoy.spawn(VehicleType.HEAVY_APC, faction, VehicleMission.deployed(20.5f, 25.5f));
        convoy.body(id).teleport(20.5f, 25.5f, -90f);
        return id;
    }

    private static NavigationGrid field() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 2; y < HEIGHT - 2; y++) {
            for (int x = 2; x < WIDTH - 2; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
