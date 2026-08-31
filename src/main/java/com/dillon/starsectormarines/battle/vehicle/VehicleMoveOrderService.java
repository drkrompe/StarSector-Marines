package com.dillon.starsectormarines.battle.vehicle;

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

        PendingOrder(long vehicleId, int cellX, int cellY) {
            this.vehicleId = vehicleId;
            this.cellX = cellX;
            this.cellY = cellY;
        }
    }

    /**
     * Accepted move intent. The requested cell is what the player clicked; the
     * route is what the vehicle will actually drive, and its last waypoint is
     * the nearest drivable ground to the request — which may not be the same
     * cell, and the interface is expected to say so.
     */
    public record ActiveOrder(int requestedX, int requestedY,
                              float[] routeXs, float[] routeYs) {
        /** Where the vehicle is actually going. */
        public float destinationX() { return routeXs[routeXs.length - 1]; }
        public float destinationY() { return routeYs[routeYs.length - 1]; }
    }

    /** A refused or abandoned order, kept for the interface to report. */
    public record RefusedOrder(int requestedX, int requestedY, Refusal reason) { }

    private final List<PendingOrder> pending = new ArrayList<>();
    private final Map<Long, ActiveOrder> active = new ConcurrentHashMap<>();
    private final Map<Long, RefusedOrder> refused = new ConcurrentHashMap<>();

    /** Queues a one-shot move request for resolution on the next fixed tick. */
    public void requestMove(long vehicleId, int cellX, int cellY) {
        pending.add(new PendingOrder(vehicleId, cellX, cellY));
    }

    /** Current accepted order for one exact vehicle, or {@code null}. */
    public ActiveOrder activeOrder(long vehicleId) {
        return active.get(vehicleId);
    }

    /** The most recent refusal for one vehicle, or {@code null}. Cleared when a new order is accepted. */
    public RefusedOrder refusal(long vehicleId) {
        return refused.get(vehicleId);
    }

    List<PendingOrder> drainPending() {
        if (pending.isEmpty()) return Collections.emptyList();
        List<PendingOrder> drained = new ArrayList<>(pending);
        pending.clear();
        return drained;
    }

    void activate(long vehicleId, ActiveOrder order) {
        active.put(vehicleId, order);
        refused.remove(vehicleId);
    }

    /** Completed successfully — the vehicle arrived, so there is nothing to report. */
    void complete(long vehicleId) {
        active.remove(vehicleId);
    }

    /** Gave up or was never viable. The reason survives so the interface can show it. */
    void refuse(long vehicleId, int requestedX, int requestedY, Refusal reason) {
        active.remove(vehicleId);
        refused.put(vehicleId, new RefusedOrder(requestedX, requestedY, reason));
    }

    /** Drops all state for a vehicle that no longer exists. */
    public void forget(long vehicleId) {
        active.remove(vehicleId);
        refused.remove(vehicleId);
    }

    Set<Map.Entry<Long, ActiveOrder>> activeEntries() {
        return active.entrySet();
    }
}
