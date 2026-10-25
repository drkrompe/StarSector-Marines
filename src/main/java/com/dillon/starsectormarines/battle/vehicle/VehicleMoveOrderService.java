package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.assault.MachineAssault;
import com.dillon.starsectormarines.battle.assault.MachineGuard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Battle-local mailbox and state owner for exact-vehicle move orders.
 *
 * <p>The interface queues the raw clicked cell; {@link VehicleMoveOrderSystem}
 * resolves it to drivable ground during the fixed tick. The active order owns
 * only that chassis's locomotion — its turret, its payload, and any delivery
 * obligation are untouched, and releasing the order hands the vehicle straight
 * back to whatever its mission was doing.
 *
 * <p>Unlike a dispatched delivery, an ordered destination is not vetted before
 * it is chosen: a player can point at anywhere. So this mailbox carries a
 * {@link Refusal} as well, because an order that cannot be carried out has to
 * say so rather than leave a vehicle standing still with no explanation. See
 * {@code vehicle-as-commandable-unit.md}.
 */
public final class VehicleMoveOrderService {

    /** Why an order was refused, or abandoned after it had been accepted. */
    public enum Refusal {
        /** No clearance-valid, forward-drivable route from here to there for this chassis. */
        NO_ROUTE,
        /** Accepted, then the vehicle stopped converging and spent its recovery. */
        GAVE_UP,
        /** The entity is not a live, commandable vehicle. */
        NOT_COMMANDABLE
    }

    /** One raw request awaiting resolution on the next fixed tick. */
    static final class PendingOrder {
        final long vehicleId;
        final int cellX;
        final int cellY;
        final boolean destinationOnly;
        /** Heading to finish on, or {@code NaN} for whatever heading the drive arrives with. */
        final float facingDegrees;

        PendingOrder(long vehicleId, int cellX, int cellY, float facingDegrees) {
            this(vehicleId, cellX, cellY, false, facingDegrees);
        }

        PendingOrder(long vehicleId, int cellX, int cellY, boolean destinationOnly, float facingDegrees) {
            this.destinationOnly = destinationOnly;
            this.vehicleId = vehicleId;
            this.cellX = cellX;
            this.cellY = cellY;
            this.facingDegrees = facingDegrees;
        }
    }

    /**
     * Accepted move intent. The requested cell is what the player clicked; the
     * route is what the vehicle will actually drive, and its last waypoint is
     * the nearest drivable ground to the request — which may not be the same
     * cell, and the interface is expected to say so. {@code facingDegrees} is
     * the heading the player dragged out for the vehicle to finish on, or
     * {@code NaN} when it was a plain click and any arrival heading will do.
     */
    public record ActiveOrder(int requestedX, int requestedY,
                              float[] routeXs, float[] routeYs, float facingDegrees) {
        public ActiveOrder(int requestedX, int requestedY, float[] routeXs, float[] routeYs) {
            this(requestedX, requestedY, routeXs, routeYs, Float.NaN);
        }

        /** Whether the order names the heading to finish on. */
        public boolean hasFacing() { return !Float.isNaN(facingDegrees); }

        /** Where the vehicle is actually going. */
        public float destinationX() { return routeXs[routeXs.length - 1]; }
        public float destinationY() { return routeYs[routeYs.length - 1]; }
    }

    /** Route-free destination retained while direct control owns the hull. */
    public record RequestedOrder(int requestedX, int requestedY, float facingDegrees) {
        public RequestedOrder(int requestedX, int requestedY) {
            this(requestedX, requestedY, Float.NaN);
        }
    }

    /** A refused or abandoned order, kept for the interface to report. */
    public record RefusedOrder(int requestedX, int requestedY, Refusal reason) { }

    private final List<PendingOrder> pending = new ArrayList<>();
    private final Map<Long, ActiveOrder> active = new ConcurrentHashMap<>();
    private final Map<Long, RefusedOrder> refused = new ConcurrentHashMap<>();
    private final Set<Long> suspended = ConcurrentHashMap.newKeySet();
    private final Map<Long, RequestedOrder> retained = new ConcurrentHashMap<>();
    /**
     * Standing assaults on a garrisonable ({@code MachineAssaults}). An assault outlives the move
     * that drove the hull to its stand-off: it holds the hull there and directs its turret until
     * the side believes nobody is left inside, or another order supersedes it.
     */
    private final Map<Long, MachineAssault> assaults = new ConcurrentHashMap<>();
    /**
     * Standing guards over a garrisonable the hull's own side mans ({@code MachineGuards}). Like an assault, a guard
     * outlives the move that drove the hull to its cell: it holds the hull there, beside the garrison, until the
     * garrison has gone or another order supersedes it. It names no target; the turret keeps its own pick.
     */
    private final Map<Long, MachineGuard> guards = new ConcurrentHashMap<>();
    /** Running tallies for evidence: orders accepted, arrived, and refused or abandoned by reason. */
    private int acceptedCount, arrivedCount;
    private final int[] refusalCounts = new int[Refusal.values().length];
    /**
     * Allied hulls a mission's allied command has enlisted ({@code allied-command.md}). An enlisted
     * {@code ALLY} hull takes orders through this mailbox as the player's own hulls do, and between
     * orders fights as an autonomous one; an allied hull nobody enlisted stays its script's. The
     * player never reaches one: the picker selects only the player's own hulls.
     */
    private final Set<Long> enlisted = ConcurrentHashMap.newKeySet();

    /** Queues a one-shot move request for resolution on the next fixed tick. */
    public void requestMove(long vehicleId, int cellX, int cellY) {
        requestMove(vehicleId, cellX, cellY, Float.NaN);
    }

    /**
     * Queues a move that finishes on {@code facingDegrees} (0 = +Y, positive
     * counterclockwise, the {@link GroundBody} convention), or on whatever
     * heading the drive arrives with when it is {@code NaN}.
     */
    public void requestMove(long vehicleId, int cellX, int cellY, float facingDegrees) {
        enqueue(new PendingOrder(vehicleId, cellX, cellY, facingDegrees));
    }

    /**
     * Queues an order. A hull already waiting keeps its place in the queue and takes the newer destination, so asking
     * again while the planner is busy neither costs a second plan nor sends it to the back.
     */
    private void enqueue(PendingOrder order) {
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i).vehicleId == order.vehicleId) {
                pending.set(i, order);
                return;
            }
        }
        pending.add(order);
    }

    /** Puts an allied hull under allied command: it takes orders here and fights between them. */
    public void enlist(long vehicleId) { enlisted.add(vehicleId); }

    /** Hands an allied hull back to its script. */
    public void discharge(long vehicleId) {
        enlisted.remove(vehicleId);
        forget(vehicleId);
    }

    /** Whether a request for this vehicle is queued and not yet resolved. */
    public boolean pending(long vehicleId) {
        for (PendingOrder order : pending) if (order.vehicleId == vehicleId) return true;
        return false;
    }

    /** Whether an allied hull is under allied command. */
    public boolean isEnlisted(long vehicleId) { return enlisted.contains(vehicleId); }

    /** Current accepted order for one exact vehicle, or {@code null}. */
    public ActiveOrder activeOrder(long vehicleId) {
        return active.get(vehicleId);
    }

    /** The most recent refusal for one vehicle, or {@code null}. Cleared when a new order is accepted. */
    public RefusedOrder refusal(long vehicleId) {
        return refused.get(vehicleId);
    }

    public boolean isSuspended(long vehicleId) { return suspended.contains(vehicleId); }

    /** The hull's standing assault, or {@code null}. */
    public MachineAssault assault(long vehicleId) { return assaults.get(vehicleId); }

    /** Ends a standing assault; the hull is idle where it stands. */
    public void endAssault(long vehicleId) { assaults.remove(vehicleId); }

    /** Whether any hull stands under an assault, so the per-tick pass with none allocates nothing. */
    public boolean hasAssaults() { return !assaults.isEmpty(); }

    /** Standing assaults, snapshotted, for the serial pass that directs their turrets. */
    public List<Map.Entry<Long, MachineAssault>> assaultEntries() {
        return new ArrayList<>(assaults.entrySet());
    }

    void beginAssault(long vehicleId, MachineAssault assault) { assaults.put(vehicleId, assault); }

    /** The hull's standing guard, or {@code null}. */
    public MachineGuard guard(long vehicleId) { return guards.get(vehicleId); }

    /** Ends a standing guard; the hull is idle where it stands. */
    public void endGuard(long vehicleId) { guards.remove(vehicleId); }

    /** Whether any hull stands guard, so the per-tick pass with none allocates nothing. */
    public boolean hasGuards() { return !guards.isEmpty(); }

    /** Standing guards, snapshotted, for the serial pass that keeps their axes current. */
    public List<Map.Entry<Long, MachineGuard>> guardEntries() {
        return new ArrayList<>(guards.entrySet());
    }

    void beginGuard(long vehicleId, MachineGuard guard) { guards.put(vehicleId, guard); }

    /** Whether the hull stands under an assault or a guard: an order that holds it at a cell of its own choosing. */
    boolean holdsStation(long vehicleId) { return assaults.containsKey(vehicleId) || guards.containsKey(vehicleId); }

    /** Current requested destination awaiting handback, without a stale route proof. */
    public RequestedOrder suspendedOrder(long vehicleId) { return retained.get(vehicleId); }

    void suspend(long vehicleId) {
        if (!suspended.add(vehicleId)) return;
        // Direct control is a new owner for the hull; the assault it was under does not survive it.
        assaults.remove(vehicleId);
        guards.remove(vehicleId);
        ActiveOrder order = active.remove(vehicleId);
        if (order != null) retain(vehicleId, order.requestedX(), order.requestedY(), order.facingDegrees());
        retainLatestPending(vehicleId);
    }

    void retain(long vehicleId, int x, int y, float facingDegrees) {
        retained.put(vehicleId, new RequestedOrder(x, y, facingDegrees));
        refused.remove(vehicleId);
    }

    void resume(long vehicleId) {
        if (!suspended.remove(vehicleId)) return;
        retainLatestPending(vehicleId);
        RequestedOrder order = retained.remove(vehicleId);
        if (order != null) enqueue(new PendingOrder(vehicleId,
                order.requestedX(), order.requestedY(), true, order.facingDegrees()));
    }

    private void retainLatestPending(long vehicleId) {
        for (PendingOrder order : pending) {
            if (order.vehicleId == vehicleId) retain(vehicleId, order.cellX, order.cellY, order.facingDegrees);
        }
        pending.removeIf(order -> order.vehicleId == vehicleId);
    }

    Set<Long> suspendedIds() { return suspended; }

    List<PendingOrder> drainPending() {
        if (pending.isEmpty()) return Collections.emptyList();
        List<PendingOrder> drained = new ArrayList<>(pending);
        pending.clear();
        return drained;
    }

    /**
     * Puts orders the planner had no room for this tick back at the head of the queue, in the order given, ahead of
     * anything asked for since. A hull asked again in the meantime keeps the deferred order's place with the newer
     * destination.
     */
    void defer(List<PendingOrder> unplanned) {
        if (unplanned.isEmpty()) return;
        List<PendingOrder> since = new ArrayList<>(pending);
        pending.clear();
        pending.addAll(unplanned);
        for (PendingOrder order : since) enqueue(order);
    }

    /** Orders accepted with a proven route, over the battle so far. */
    public int acceptedCount() { return acceptedCount; }

    /** Accepted orders that ended with the hull arriving, over the battle so far. */
    public int arrivedCount() { return arrivedCount; }

    /** Orders refused or abandoned for {@code reason}, over the battle so far. */
    public int refusalCount(Refusal reason) { return refusalCounts[reason.ordinal()]; }

    /** How many orders wait for the planner. */
    public int pendingCount() { return pending.size(); }

    void activate(long vehicleId, ActiveOrder order) {
        acceptedCount++;
        active.put(vehicleId, order);
        refused.remove(vehicleId);
    }

    /** Completed successfully — the vehicle arrived, so there is nothing to report. */
    void complete(long vehicleId) {
        if (active.remove(vehicleId) != null) arrivedCount++;
        refused.remove(vehicleId);
    }

    /** Gave up or was never viable. The reason survives so the interface can show it. */
    void refuse(long vehicleId, int requestedX, int requestedY, Refusal reason) {
        refusalCounts[reason.ordinal()]++;
        active.remove(vehicleId);
        refused.put(vehicleId, new RefusedOrder(requestedX, requestedY, reason));
    }

    /** Drops all state for a vehicle that no longer exists. */
    public void forget(long vehicleId) {
        active.remove(vehicleId);
        refused.remove(vehicleId);
        suspended.remove(vehicleId);
        retained.remove(vehicleId);
        assaults.remove(vehicleId);
        guards.remove(vehicleId);
        pending.removeIf(order -> order.vehicleId == vehicleId);
    }

    Set<Map.Entry<Long, ActiveOrder>> activeEntries() {
        return active.entrySet();
    }
}
