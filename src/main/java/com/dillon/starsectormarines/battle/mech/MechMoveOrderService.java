package com.dillon.starsectormarines.battle.mech;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Battle-local mailbox and state owner for exact-mech move orders.
 *
 * <p>The UI queues the raw clicked cell. {@link MechMoveOrderSystem} validates
 * the selected entity and resolves that request to reachable ground during the
 * serial command phase. The active order is deliberately per chassis rather
 * than per squad: it temporarily owns only that mech's locomotion, leaving its
 * mission directive, squad plan, doctrine, target picture, and weapons intact.
 */
public final class MechMoveOrderService {

    /** One raw UI request awaiting command-phase validation. */
    static final class PendingOrder {
        final long mechId;
        final int cellX;
        final int cellY;
        /** Hip heading to hold on arrival, or {@code NaN} for none. */
        final float facingDegrees;

        PendingOrder(long mechId, int cellX, int cellY, float facingDegrees) {
            this.mechId = mechId;
            this.cellX = cellX;
            this.cellY = cellY;
            this.facingDegrees = facingDegrees;
        }
    }

    /**
     * Accepted move intent. Requested coordinates retain what the player
     * clicked; destination coordinates name the nearest reachable cell the
     * mech will actually occupy. {@code facingDegrees} is the hip heading the
     * player dragged out for it to stand on once there, or {@code NaN}.
     */
    public record ActiveOrder(int requestedX, int requestedY,
                              int destinationX, int destinationY, float facingDegrees) {
        public ActiveOrder(int requestedX, int requestedY, int destinationX, int destinationY) {
            this(requestedX, requestedY, destinationX, destinationY, Float.NaN);
        }
    }

    private final List<PendingOrder> pending = new ArrayList<>();
    private final Map<Long, ActiveOrder> active = new ConcurrentHashMap<>();
    private final Map<Long, Float> heldFacing = new ConcurrentHashMap<>();

    /** Queues a one-shot move request for validation on the next fixed tick. */
    public void requestMove(long mechId, int cellX, int cellY) {
        requestMove(mechId, cellX, cellY, Float.NaN);
    }

    /**
     * Queues a move whose mech then stands with its hips on {@code facingDegrees}
     * (the sprite convention: 0 = +Y, positive counterclockwise), or on its own
     * judgement when that is {@code NaN}.
     */
    public void requestMove(long mechId, int cellX, int cellY, float facingDegrees) {
        pending.add(new PendingOrder(mechId, cellX, cellY, facingDegrees));
    }

    /**
     * The hip heading an arrived order left this mech holding, or {@code NaN}.
     * It lasts while the mech stands: the next route it walks, a new order, or a
     * cancel ends it. The upper chassis keeps its own aim within its twist.
     */
    public float heldFacing(long mechId) {
        Float held = heldFacing.get(mechId);
        return held == null ? Float.NaN : held;
    }

    void holdFacing(long mechId, float facingDegrees) {
        if (Float.isNaN(facingDegrees)) heldFacing.remove(mechId);
        else heldFacing.put(mechId, facingDegrees);
    }

    void releaseHeldFacing(long mechId) {
        heldFacing.remove(mechId);
    }

    /** Current accepted order for one exact mech, or {@code null}. */
    public ActiveOrder activeOrder(long mechId) {
        return active.get(mechId);
    }

    /** Cancels both accepted and queued intent for a chassis during serial ownership transfer. */
    public void cancel(long mechId) {
        pending.removeIf(order -> order.mechId == mechId);
        active.remove(mechId);
        heldFacing.remove(mechId);
    }

    List<PendingOrder> drainPending() {
        if (pending.isEmpty()) return Collections.emptyList();
        List<PendingOrder> drained = new ArrayList<>(pending);
        pending.clear();
        return drained;
    }

    void activate(long mechId, ActiveOrder order) {
        active.put(mechId, order);
        heldFacing.remove(mechId);
    }

    void release(long mechId, ActiveOrder order) {
        active.remove(mechId, order);
    }

    void release(long mechId) {
        active.remove(mechId);
    }

    Iterable<Map.Entry<Long, ActiveOrder>> activeEntries() {
        return active.entrySet();
    }
}
