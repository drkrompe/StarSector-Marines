package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.assault.MachineAssault;
import com.dillon.starsectormarines.battle.assault.MachineGuard;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService.PendingOrder;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService.Refusal;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;

import java.util.ArrayList;
import java.util.Comparator;
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
     * Answers whether a click is an assault on a garrisonable rather than a move, and if so where
     * the hull fights from. Supplied by the simulation, which holds the side's beliefs this system
     * does not; null means every click is a move.
     */
    public interface AssaultPlanner {
        MachineAssault plan(long vehicleId, int cellX, int cellY);
    }

    private AssaultPlanner assaultPlanner;

    /**
     * Answers whether a click is a guard over a garrisonable the hull's own side mans, and if so where the hull
     * stands beside it ({@code MachineGuards}). Supplied by the simulation; null means no click is a guard.
     */
    public interface GuardPlanner {
        MachineGuard plan(long vehicleId, int cellX, int cellY);
    }

    private GuardPlanner guardPlanner;

    /** Installs the planner the simulation answers guard questions with. */
    public void setGuardPlanner(GuardPlanner planner) {
        this.guardPlanner = planner;
    }

    /** Installs the planner the simulation answers assault questions with. */
    public void setAssaultPlanner(AssaultPlanner planner) {
        this.assaultPlanner = planner;
    }

    /**
     * How long an accepted order tolerates the controller having no forward
     * plan before it is abandoned. Long enough to cover a turnaround and a
     * re-route, short enough that a player is not watching a stationary vehicle
     * wonder about it.
     */
    static final float GIVE_UP_SECONDS = 8f;

    /**
     * Whether an order's route is proved from where the hull stands, its ends judged as an order's
     * ({@link VehicleRoutePlanner#routeDrivableFrom}). {@code -Dbattle.vehicle.orderEnds=false} is the control, both
     * ends held to the sampling margin as dispatch's are: remove it with the losing path once The Burn's
     * vehicle-order evidence has been read against it.
     */
    static final boolean ORDER_ENDS = Boolean.parseBoolean(System.getProperty("battle.vehicle.orderEnds", "true"));

    /** How far the resolver may look for drivable ground near the clicked cell. */
    private static final int SNAP_RADIUS = 6;
    /** Cells around an ordered cell searched for the nearest one the hull can reach ({@link #goal}). */
    static final int REACHABLE_SNAP_RADIUS = 24;

    /**
     * Route proofs {@link #tickPending} makes in one tick; zero plans every queued order at once, the control for an
     * A/B against the budget. A tuning lever ({@code switches.md}): fold it into this constant once the convoy and
     * story-mission evidence has been read against it.
     */
    static final int PLANS_PER_TICK = Integer.getInteger("battle.vehicle.orderPlansPerTick", 2);

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

    /** Transfers a deployed hull's locomotion without retaining its old route or moving its body. */
    public boolean suspendForManual(long id) {
        if (!manuallyCommandable(id)) return false;
        if (!service.isSuspended(id)) {
            service.suspend(id);
            controlSystem.clearRoute(id);
        }
        return true;
    }

    /** Requeue the latest destination; the next fixed tick proves it from the actual handback pose. */
    public void resumeFromManual(long id) {
        if (!service.isSuspended(id)) return;
        if (!manuallyCommandable(id)) {
            service.forget(id);
            return;
        }
        controlSystem.clearRoute(id);
        service.resume(id);
    }

    /**
     * Resolves queued requests, planning at most {@link #PLANS_PER_TICK} routes. Call once per tick, before driving.
     *
     * <p>A route proof is the expensive part of an order, and orders come in blocks: a restocked lane hands every
     * fresh hull its post on one tick, and their refusals and pull-backs then follow in step. Planning them all at
     * once was a frame hitch every time. The queue is planned in a fixed order instead, the player's own hulls first
     * and then oldest first, and what does not fit waits at the head of the queue for the next tick; a hull already
     * under orders keeps driving them meanwhile. The allowance counts plans rather than time, so a replay plans the
     * same orders on the same ticks.
     */
    public void tickPending() {
        for (long id : new ArrayList<>(service.suspendedIds())) {
            if (!manuallyCommandable(id)) service.forget(id);
        }
        for (Map.Entry<Long, ActiveOrder> entry : new ArrayList<>(service.activeEntries())) {
            if (!commandable(entry.getKey())) service.forget(entry.getKey());
        }
        List<PendingOrder> queue = new ArrayList<>(service.drainPending());
        // Stable, so each group keeps its queue order: a player's click never waits behind allied traffic.
        queue.sort(Comparator.comparing(order -> service.isEnlisted(order.vehicleId)));
        int planned = 0;
        for (int i = 0; i < queue.size(); i++) {
            PendingOrder request = queue.get(i);
            if (service.isSuspended(request.vehicleId)) {
                service.retain(request.vehicleId, request.cellX, request.cellY, request.facingDegrees);
                continue;
            }
            // Right-clicking a loaded transport on itself is "everybody out",
            // the way Red Alert 2 read it: the vehicle is the target, and what
            // it does depends on whether anybody is in it. Resolved before the
            // move, because a click on your own hull is not a destination.
            if (!request.destinationOnly && commandable(request.vehicleId)
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
            if (PLANS_PER_TICK > 0 && planned >= PLANS_PER_TICK) {
                service.defer(queue.subList(i, queue.size()));
                break;
            }
            planned++;
            // Any new order supersedes a standing assault or guard, including another of either.
            service.endAssault(request.vehicleId);
            service.endGuard(request.vehicleId);
            // A click on a garrisonable the side believes is enemy-held is an assault by fire: the
            // hull drives to a stand-off rather than to the click, and keeps it once there.
            MachineAssault assault = request.destinationOnly || assaultPlanner == null ? null
                    : assaultPlanner.plan(request.vehicleId, request.cellX, request.cellY);
            // A click on one the side mans is a guard: the hull drives to a cell beside it, off the garrison's lanes.
            MachineGuard guard = assault != null || request.destinationOnly || guardPlanner == null ? null
                    : guardPlanner.plan(request.vehicleId, request.cellX, request.cellY);
            int goalX = assault != null ? assault.standoffX() : guard != null ? guard.guardX() : request.cellX;
            int goalY = assault != null ? assault.standoffY() : guard != null ? guard.guardY() : request.cellY;
            float[][] route = planRoute(request.vehicleId, goalX, goalY);
            if (route == null) {
                service.refuse(request.vehicleId, request.cellX, request.cellY,
                        Refusal.NO_ROUTE);
                continue;
            }
            if (assault != null) service.beginAssault(request.vehicleId, assault);
            if (guard != null) service.beginGuard(request.vehicleId, guard);
            if (route[0].length == 0) {
                // Already there. A plain click has nothing left to do; a dragged facing still has the turn to make,
                // which the docking maneuver makes on the spot from a route that ends where the hull stands.
                if (Float.isNaN(request.facingDegrees)) {
                    service.complete(request.vehicleId);
                    continue;
                }
                GroundBody body = convoy.body(request.vehicleId);
                route = new float[][]{{body.x, body.x}, {body.y, body.y}};
            }
            service.activate(request.vehicleId, new ActiveOrder(request.cellX, request.cellY,
                    route[0], route[1], request.facingDegrees));
        }
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        if (profile != null) {
            profile.recordCount(TickInnerProfile.Bucket.VEHICLE_ORDER_PLANNED, planned);
            profile.recordCount(TickInnerProfile.Bucket.VEHICLE_ORDER_WAITING, service.pendingCount());
        }
    }

    /**
     * Drives vehicle {@code id} for one tick if it is under orders.
     *
     * @return true when the order consumed this vehicle's tick, so the delivery
     *         state machine should not also drive it
     */
    public boolean executeIfActive(long id, float dt) {
        if (service.isSuspended(id)) {
            if (manuallyCommandable(id)) return true;
            service.forget(id);
            return false;
        }
        ActiveOrder order = service.activeOrder(id);
        if (order == null) return holdAssault(id, dt);
        if (!commandable(id)) {
            service.forget(id);
            return false;
        }

        controlSystem.tick(id, dt, order.routeXs(), order.routeYs(), VehicleLeg.MOVE_ORDER,
                order.facingDegrees());
        if (controlSystem.consumeArrived(id)) {
            // A move order does not snap onto its endpoint, so the distance gate can call arrival with the body
            // still rolling. Nothing integrates a deployed hull once its order is done, so that speed would never
            // decay: the hull stands still while reporting itself under way to everything that reads it (a supply
            // truck stays shut, fire takes a moving penalty). Arriving is stopping.
            convoy.body(id).speed = 0f;
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
     * A hull at its stand-off under a standing assault, or at its cell under a guard, holds there: the order owns
     * its locomotion so the duel-band engagement does not drive it off, or into the place it is firing on or
     * screening. Its turret is directed by the assault, or keeps its own pick under a guard, not here.
     */
    private boolean holdAssault(long id, float dt) {
        if (!service.holdsStation(id)) return false;
        if (!manuallyCommandable(id)) {
            service.endAssault(id);
            service.endGuard(id);
            return false;
        }
        controlSystem.tickManual(id, dt, 0f, 0f);
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
    private boolean manuallyCommandable(long id) {
        return commandable(id) && convoy.structure(id) > 0f
                && convoy.mission(id).state == VehicleState.DEPLOYED;
    }

    private boolean commandable(long id) {
        if (!convoy.isVehicle(id)) return false;
        Faction faction = convoy.faction(id);
        if (faction != Faction.MARINE && !(faction == Faction.ALLY && service.isEnlisted(id))) return false;
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
        // A bladed hull proves its route over the ground it can open, and a hover hull over the water it floats on.
        NavigationGrid grid = controlSystem.planningGrid(type);

        VehicleClearance clearance = convoy.clearances().of(grid, type);
        TerrainCostField cost = controlSystem.costField(type);

        int[] from = VehicleRoutePlanner.snapToMask(clearance,
                (int) Math.floor(body.x), (int) Math.floor(body.y), SNAP_RADIUS);
        if (from == null) return null;
        int[] to = goal(grid, type, clearance, from, cellX, cellY);
        if (to == null) return null;
        if (from[0] == to[0] && from[1] == to[1]) {
            // Manual driving may already have fulfilled a retained destination.
            // A distant snap is not arrival: the real body must be near the resolved point.
            return body.distanceTo(to[0] + 0.5f, to[1] + 0.5f)
                    <= VehicleLeg.MOVE_ORDER.arrivalFloorCells()
                    ? new float[][]{new float[0], new float[0]} : null;
        }

        if (!ORDER_ENDS) {
            return VehicleRoutePlanner.routeDrivable(from[0], from[1], to[0], to[1], grid, cost, clearance, type);
        }
        return VehicleRoutePlanner.routeDrivableFrom(new Pose(body.x, body.y, body.facingDegrees),
                from[0], from[1], to[0], to[1], grid, cost, clearance, type);
    }

    /**
     * The cell an order to ({@code cellX}, {@code cellY}) drives to: the nearest one, within
     * {@link #REACHABLE_SNAP_RADIUS}, this chassis can get to from {@code from}, where the grid's components are held.
     *
     * <p>An order into a walled yard, onto a building or across water used to snap to the nearest cell the footprint
     * fits in, which is often inside the same yard: the order was refused, or proved by flooding everything the hull
     * could reach first, and a hull its command ordered there again stood parked. The nearest reachable cell is the
     * closest the hull can actually come. A planning view rebuilt each time the map moves (a blade's, a skirt's) would
     * be labelled afresh, so it keeps the plain snap; the tracks' crush view is kept and caught up like the live grid.
     */
    private int[] goal(NavigationGrid grid, VehicleType type, VehicleClearance clearance, int[] from,
                       int cellX, int cellY) {
        if (grid == navigation.getGrid() || controlSystem.isKeptView(grid)) {
            int[] reachable = VehicleRoutePlanner.snapToComponent(clearance,
                    convoy.clearances().components(grid, type), from[0], from[1], cellX, cellY, REACHABLE_SNAP_RADIUS);
            if (reachable != null) return reachable;
        }
        return VehicleRoutePlanner.snapToMask(clearance, cellX, cellY, SNAP_RADIUS);
    }

    /** Live orders, for the highlight overlay. */
    public List<Map.Entry<Long, ActiveOrder>> activeOrders() {
        return new ArrayList<>(service.activeEntries());
    }
}
