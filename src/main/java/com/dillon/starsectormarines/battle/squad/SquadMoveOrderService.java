package com.dillon.starsectormarines.battle.squad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Battle-local request mailbox and accepted state for infantry squad moves. */
public final class SquadMoveOrderService {

    static final class PendingOrder {
        final int squadId;
        final int cellX;
        final int cellY;

        PendingOrder(int squadId, int cellX, int cellY) {
            this.squadId = squadId;
            this.cellX = cellX;
            this.cellY = cellY;
        }
    }

    /** Raw click and the connected walkable destination that will be used. */
    public record ActiveOrder(int requestedX, int requestedY,
                              int destinationX, int destinationY) { }

    private final List<PendingOrder> pending = new ArrayList<>();
    private final Map<Integer, ActiveOrder> active = new ConcurrentHashMap<>();

    /** Queues a squad-scoped request for command-phase validation. */
    public void requestMove(int squadId, int cellX, int cellY) {
        pending.add(new PendingOrder(squadId, cellX, cellY));
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
