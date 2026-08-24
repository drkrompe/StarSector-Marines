package com.dillon.starsectormarines.battle.unit;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Arrays;

/**
 * Entity-id to dense-roster-slot index. Ordinary monotonic ids use direct
 * array addressing; unusually large ids fall back to sparse storage so one
 * id cannot force a giant allocation.
 */
final class RosterSlotIndex {

    static final int MAX_DENSE_ID = 1 << 20;
    private static final int MISSING = UnitRosterService.INVALID_INDEX;
    private static final int INITIAL_CAPACITY = 16;

    private int[] dense = new int[INITIAL_CAPACITY];
    private final Long2IntOpenHashMap sparse = new Long2IntOpenHashMap();

    RosterSlotIndex() {
        Arrays.fill(dense, MISSING);
        sparse.defaultReturnValue(MISSING);
    }

    int get(long entityId) {
        if (entityId <= 0L) return MISSING;
        if (entityId < dense.length) return dense[(int) entityId];
        if (entityId <= MAX_DENSE_ID) return MISSING;
        return sparse.get(entityId);
    }

    void put(long entityId, int slot) {
        if (entityId <= 0L) {
            throw new IllegalArgumentException("entity id must be positive: " + entityId);
        }
        if (entityId <= MAX_DENSE_ID) {
            ensureDenseCapacity((int) entityId + 1);
            dense[(int) entityId] = slot;
            return;
        }
        sparse.put(entityId, slot);
    }

    int remove(long entityId) {
        if (entityId <= 0L) return MISSING;
        if (entityId < dense.length) {
            int index = (int) entityId;
            int previous = dense[index];
            dense[index] = MISSING;
            return previous;
        }
        if (entityId <= MAX_DENSE_ID) return MISSING;
        return sparse.remove(entityId);
    }

    private void ensureDenseCapacity(int required) {
        if (required <= dense.length) return;
        int oldCapacity = dense.length;
        int newCapacity = oldCapacity;
        while (newCapacity < required) {
            newCapacity = Math.min(MAX_DENSE_ID + 1, newCapacity << 1);
        }
        dense = Arrays.copyOf(dense, newCapacity);
        Arrays.fill(dense, oldCapacity, newCapacity, MISSING);
    }
}
