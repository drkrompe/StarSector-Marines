package com.dillon.starsectormarines.engine.ecs;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import java.util.Arrays;

/**
 * Entity-id to packed-row index. Ordinary monotonic ids use direct array
 * addressing; unusually large externally adopted ids fall back to sparse
 * storage so one integration id cannot force a giant allocation.
 */
final class EntityLocationIndex {

    static final int MAX_DENSE_ID = 1 << 20;
    private static final long MISSING = -1L;
    private static final int INITIAL_CAPACITY = 16;

    private long[] dense = new long[INITIAL_CAPACITY];
    private final Long2LongOpenHashMap sparse = new Long2LongOpenHashMap();
    private int size;

    EntityLocationIndex() {
        Arrays.fill(dense, MISSING);
        sparse.defaultReturnValue(MISSING);
    }

    long get(long entityId) {
        if (entityId <= 0L) return MISSING;
        if (entityId < dense.length) return dense[(int) entityId];
        if (entityId <= MAX_DENSE_ID) return MISSING;
        return sparse.get(entityId);
    }

    void put(long entityId, long location) {
        if (entityId <= MAX_DENSE_ID) {
            ensureDenseCapacity((int) entityId + 1);
            int index = (int) entityId;
            if (dense[index] == MISSING) size++;
            dense[index] = location;
            return;
        }
        if (sparse.put(entityId, location) == MISSING) size++;
    }

    long remove(long entityId) {
        if (entityId <= 0L) return MISSING;
        if (entityId < dense.length) {
            int index = (int) entityId;
            long previous = dense[index];
            if (previous != MISSING) {
                dense[index] = MISSING;
                size--;
            }
            return previous;
        }
        if (entityId <= MAX_DENSE_ID) return MISSING;
        long previous = sparse.remove(entityId);
        if (previous != MISSING) size--;
        return previous;
    }

    int size() {
        return size;
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
