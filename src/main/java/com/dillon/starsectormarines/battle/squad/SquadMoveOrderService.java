package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.decision.TacticalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Battle-local mailbox for infantry orders and shared infantry/Mech area defence. */
public final class SquadMoveOrderService {

    /** A 40-by-40-cell circular footprint: twenty cells from center to edge. */
    public static final int DEFEND_AREA_RADIUS_CELLS = 20;

    static final class PendingOrder {
        enum Kind { MOVE, DEFEND_AREA }

        final int squadId;
        final int cellX;
        final int cellY;
        final Kind kind;

        PendingOrder(int squadId, int cellX, int cellY, Kind kind) {
            this.squadId = squadId;
            this.cellX = cellX;
            this.cellY = cellY;
            this.kind = kind;
        }
    }

    /** Accepted contextual order projected for execution and UI feedback. */
    public sealed interface ActiveOrder
            permits ActiveMoveOrder, ActiveCaptureOrder, ActiveDefendAreaOrder,
                    ActiveMountOrder {
        int requestedX();
        int requestedY();
        int destinationX();
        int destinationY();
    }

    /** Raw ground click and the connected walkable destination that will be used. */
    public record ActiveMoveOrder(int requestedX, int requestedY,
                                  int destinationX, int destinationY)
            implements ActiveOrder { }

    /** Uncaptured compound selected by the click and its authoritative capture cell. */
    public record ActiveCaptureOrder(int requestedX, int requestedY,
                                     int destinationX, int destinationY,
                                     TacticalNode targetNode)
            implements ActiveOrder { }

    /**
     * Walk to a friendly vehicle and get in it.
     *
     * <p>The destination is where the vehicle was when the order was last
     * refreshed rather than where it was clicked, because a vehicle is not a
     * cell — it can drive off while the squad is still walking, and an order
     * aimed at the ground it used to be standing on would march the squad to an
     * empty patch of road.
     */
    public record ActiveMountOrder(int requestedX, int requestedY,
                                   int destinationX, int destinationY,
                                   long vehicleId)
            implements ActiveOrder { }

    /** Persistent circular player defense area centered on reachable ground. */
    public record ActiveDefendAreaOrder(int requestedX, int requestedY,
                                        int destinationX, int destinationY,
                                        int radiusCells)
            implements ActiveOrder { }

    private final List<PendingOrder> pending = new ArrayList<>();
    private final Map<Integer, ActiveOrder> active = new ConcurrentHashMap<>();

    /** Queues a squad-scoped world request for command-phase contextual resolution. */
    public void requestMove(int squadId, int cellX, int cellY) {
        pending.add(new PendingOrder(squadId, cellX, cellY,
                PendingOrder.Kind.MOVE));
    }

    /** Queues placement of a persistent 40-cell-diameter defense area. */
    public void requestDefendArea(int squadId, int cellX, int cellY) {
        pending.add(new PendingOrder(squadId, cellX, cellY,
                PendingOrder.Kind.DEFEND_AREA));
    }

    public ActiveOrder activeOrder(int squadId) {
        return active.get(squadId);
    }

    List<PendingOrder> drainPending() {
        if (pending.isEmpty()) return Collections.emptyList();
        List<PendingOrder> drained = new ArrayList<>(pending);
        pending.clear();
        return drained;
    }

    void activate(int squadId, ActiveOrder order) {
        active.put(squadId, order);
    }

    void release(int squadId, ActiveOrder order) {
        active.remove(squadId, order);
    }

    Iterable<Map.Entry<Integer, ActiveOrder>> activeEntries() {
        return active.entrySet();
    }
}
