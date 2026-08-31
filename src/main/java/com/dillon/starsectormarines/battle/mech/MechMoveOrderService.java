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

        PendingOrder(long mechId, int cellX, int cellY) {
            this.mechId = mechId;
            this.cellX = cellX;
            this.cellY = cellY;
        }
    }

    /**
     * Accepted move intent. Requested coordinates retain what the player
     * clicked; destination coordinates name the nearest reachable cell the
     * mech will actually occupy.
     */
    public record ActiveOrder(int requestedX, int requestedY,
                              int destinationX, int destinationY) { }

    private final List<PendingOrder> pending = new ArrayList<>();
    private final Map<Long, ActiveOrder> active = new ConcurrentHashMap<>();

    /** Queues a one-shot move request for validation on the next fixed tick. */
    public void requestMove(long mechId, int cellX, int cellY) {
        pending.add(new PendingOrder(mechId, cellX, cellY));
    }

    /** Current accepted order for one exact mech, or {@code null}. */
    public ActiveOrder activeOrder(long mechId) {
        return active.get(mechId);
    }

    List<PendingOrder> drainPending() {
        if (pending.isEmpty()) return Collections.emptyList();
        List<PendingOrder> drained = new ArrayList<>(pending);
        pending.clear();
        return drained;
    }

    void activate(long mechId, ActiveOrder order) {
        active.put(mechId, order);
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
