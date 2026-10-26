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
        enum Kind { MOVE, DEFEND_AREA, LINE }

        final int squadId;
        final int cellX;
        final int cellY;
        final Kind kind;
        /** For a {@link Kind#LINE}: where the drag ended; ({@link #cellX}, {@link #cellY}) is where it began. */
        final int endX;
        final int endY;

        PendingOrder(int squadId, int cellX, int cellY, Kind kind) {
            this(squadId, cellX, cellY, kind, cellX, cellY);
        }

        /** For a {@link Kind#LINE}: how many attached mechs stand within it. */
        int machines;

        PendingOrder(int squadId, int cellX, int cellY, Kind kind, int endX, int endY) {
            this.squadId = squadId;
            this.cellX = cellX;
            this.cellY = cellY;
            this.kind = kind;
            this.endX = endX;
            this.endY = endY;
        }
    }

    /** Accepted contextual order projected for execution and UI feedback. */
    public sealed interface ActiveOrder
            permits ActiveMoveOrder, ActiveCaptureOrder, ActiveDefendAreaOrder,
                    ActiveMountOrder, ActiveHoldPlaceOrder {
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
     * Hold a building: garrison the held place the click landed in, entered at its interior
     * anchor. Like a defense area it stands until superseded.
     */
    public record ActiveHoldPlaceOrder(int requestedX, int requestedY,
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

    /**
     * Persistent circular player defense area centered on reachable ground.
     *
     * <p>{@code occupyRunId} is the fighting-position run the click landed on when this
     * is an <b>occupy</b> order, or {@link OccupyPositionDecision#NONE} for an ordinary
     * defense area. Execution is identical; the run only narrows which position cells
     * the squad's marines are given.
     *
     * <p>{@code line} is the frontage the player drew when this is a <b>line</b> order, or
     * null. The circle then encloses the line's band, and the squad spreads along the line
     * rather than around the circle's centre.
     */
    public record ActiveDefendAreaOrder(int requestedX, int requestedY,
                                        int destinationX, int destinationY,
                                        int radiusCells, int occupyRunId, FrontageLine line)
            implements ActiveOrder {

        public ActiveDefendAreaOrder(int requestedX, int requestedY,
                                     int destinationX, int destinationY,
                                     int radiusCells) {
            this(requestedX, requestedY, destinationX, destinationY, radiusCells,
                    OccupyPositionDecision.NONE);
        }

        public ActiveDefendAreaOrder(int requestedX, int requestedY,
                                     int destinationX, int destinationY,
                                     int radiusCells, int occupyRunId) {
            this(requestedX, requestedY, destinationX, destinationY, radiusCells, occupyRunId, null);
        }

        /** Whether this area was placed by clicking a fighting position. */
        public boolean isOccupy() {
            return occupyRunId >= 0;
        }
    }

    private final List<PendingOrder> pending = new ArrayList<>();
    private final Map<Integer, ActiveOrder> active = new ConcurrentHashMap<>();

    /** Queues a squad-scoped world request for command-phase contextual resolution. */
    public void requestMove(int squadId, int cellX, int cellY) {
        pending.add(new PendingOrder(squadId, cellX, cellY,
                PendingOrder.Kind.MOVE));
    }

    /** Queues a line for the squad to hold, drawn from ({@code x0},{@code y0}) to ({@code x1},{@code y1}). */
    public void requestLine(int squadId, int x0, int y0, int x1, int y1) {
        requestLine(squadId, x0, y0, x1, y1, 0);
    }

    /** As above, with {@code machines} attached mechs standing within the line among the marines. */
    public void requestLine(int squadId, int x0, int y0, int x1, int y1, int machines) {
        PendingOrder order = new PendingOrder(squadId, x0, y0, PendingOrder.Kind.LINE, x1, y1);
        order.machines = machines;
        pending.add(order);
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
