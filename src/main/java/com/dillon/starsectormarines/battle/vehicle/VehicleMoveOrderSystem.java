package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService.PendingOrder;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService.Refusal;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves exact-vehicle move requests and drives the accepted ones.
 *
 * <p>The order is a {@link VehicleLeg#MOVE_ORDER} route handed to the ordinary
 * {@link VehicleControlSystem}: the same corridor tracking, the same recovery
 * ladder, the same turnaround. Nothing about how a vehicle moves is special
 * here — the only new thing is where the route comes from and what happens
 * when it cannot be finished.
 *
 * <p><b>Refusal is the point.</b> A dispatched route is proven feasible before
 * dispatch commits to it; a clicked destination is not, so this is where
 * feasibility actually gets decided. A request with no drivable route is
 * refused outright, and an accepted order that stops converging is abandoned
 * with a reason rather than leaving the vehicle stationary for the rest of the
 * battle — which is what the recovery ladder's terminal state does on its own.
 */
public final class VehicleMoveOrderSystem {

    /**
     * How long an accepted order tolerates the controller having no forward
     * plan before it is abandoned. Long enough to cover a turnaround and a
     * re-route, short enough that a player is not watching a stationary vehicle
     * wonder about it.
     */
    static final float GIVE_UP_SECONDS = 8f;

    /** How far the resolver may look for drivable ground near the clicked cell. */
    private static final int SNAP_RADIUS = 6;

    private final VehicleMoveOrderService service;
    private final ConvoyService convoy;
    private final NavigationService navigation;
    private final VehicleControlSystem controlSystem;
    private final VehicleTransportService transport;

    public VehicleMoveOrderSystem(VehicleMoveOrderService service, ConvoyService convoy,
                                  NavigationService navigation,
                                  VehicleControlSystem controlSystem,
                                  VehicleTransportService transport) {
        this.service = service;
        this.convoy = convoy;
        this.navigation = navigation;
        this.controlSystem = controlSystem;
        this.transport = transport;
    }

    /** Resolves every queued request. Call once per tick, before driving. */
    public void tickPending() {
        for (Map.Entry<Long, ActiveOrder> entry : new ArrayList<>(service.activeEntries())) {
            if (!commandable(entry.getKey())) service.forget(entry.getKey());
        }
        for (PendingOrder request : service.drainPending()) {
            // Right-clicking a loaded transport on itself is "everybody out",
            // the way Red Alert 2 read it: the vehicle is the target, and what
            // it does depends on whether anybody is in it. Resolved before the
            // move, because a click on your own hull is not a destination.
            if (commandable(request.vehicleId)
                    && transport.pointsAt(request.vehicleId, request.cellX, request.cellY)
                    && !transport.manifest(request.vehicleId).isEmpty()) {
                transport.dismountAll(request.vehicleId);
                service.complete(request.vehicleId);
                continue;
            }
            if (!commandable(request.vehicleId)) {
                service.refuse(request.vehicleId, request.cellX, request.cellY,
                        Refusal.NOT_COMMANDABLE);
                continue;
            }
            float[][] route = planRoute(request.vehicleId, request.cellX, request.cellY);
            if (route == null) {
                service.refuse(request.vehicleId, request.cellX, request.cellY,
                        Refusal.NO_ROUTE);
                continue;
            }
            service.activate(request.vehicleId,
                    new ActiveOrder(request.cellX, request.cellY, route[0], route[1]));
        }
    }

    /**
     * Drives vehicle {@code id} for one tick if it is under orders.
     *
     * @return true when the order consumed this vehicle's tick, so the delivery
     *         state machine should not also drive it
     */
    public boolean executeIfActive(long id, float dt) {
        ActiveOrder order = service.activeOrder(id);
        if (order == null) return false;
        if (!commandable(id)) {
            service.forget(id);
            return false;
        }

        controlSystem.tick(id, dt, order.routeXs(), order.routeYs(), VehicleLeg.MOVE_ORDER);
        if (controlSystem.consumeArrived(id)) {
            service.complete(id);
            return true;
        }

        // The recovery ladder's own terminal state is to hold position
        // indefinitely. That is a defect nobody sees on a dispatched convoy and
        // the whole experience on an ordered one, so an order that has stopped
        // converging is given up rather than parked.
        VehicleControlComponent s = convoy.control(id);
        if (s.localPlanFailureTime >= GIVE_UP_SECONDS) {
            service.refuse(id, order.requestedX(), order.requestedY(), Refusal.GAVE_UP);
        }
        return true;
    }

    /**
     * Whether {@code id} is still a live vehicle of the player's that can be
     * given orders.
     *
     * <p>The faction check is not belt-and-braces on the picker: it is the
     * authority boundary. Orders are queued by id from the interface, and an
     * id is all it takes — so the system that acts on them is where "this one
     * is not yours" has to be decided.
     */
    private boolean commandable(long id) {
        if (!convoy.isVehicle(id)) return false;
        if (convoy.faction(id) != Faction.MARINE) return false;
        VehicleMission mission = convoy.mission(id);
        return mission != null
                && mission.state != VehicleState.WRECKED
                && mission.state != VehicleState.GONE;
    }

    /**
     * Resolves a clicked cell to a route this chassis can actually drive, or
     * {@code null} when there is none.
     *
     * <p>Deliberately not the infantry reachability answer. A vehicle's
     * destination has to admit its footprint and its turning circle, so the
     * question is asked with the clearance mask its width erodes and the
     * drivable-bend search its wheelbase implies — the same pair a dispatched
     * route is built from.
     */
    private float[][] planRoute(long id, int cellX, int cellY) {
        VehicleType type = convoy.vehicleType(id);
        GroundBody body = convoy.body(id);
        NavigationGrid grid = navigation.getGrid();

        VehicleClearance clearance = VehicleClearance.erode(grid,
                VehicleClearance.radiusForWidth(type.visualWidthCells));
        TerrainCostField cost = TerrainCostField.from(navigation.getTopology());

        int[] from = VehicleRoutePlanner.snapToMask(clearance,
                (int) Math.floor(body.x), (int) Math.floor(body.y), SNAP_RADIUS);
        int[] to = VehicleRoutePlanner.snapToMask(clearance, cellX, cellY, SNAP_RADIUS);
        if (from == null || to == null) return null;
        if (from[0] == to[0] && from[1] == to[1]) return null;   // already there

        return VehicleRoutePlanner.routeDrivable(from[0], from[1], to[0], to[1],
                grid, cost, clearance, type);
    }

    /** Live orders, for the highlight overlay. */
    public List<Map.Entry<Long, ActiveOrder>> activeOrders() {
        return new ArrayList<>(service.activeEntries());
    }
}
