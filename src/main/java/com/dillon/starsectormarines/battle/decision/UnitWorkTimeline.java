package com.dillon.starsectormarines.battle.decision;

import java.util.Arrays;
import java.util.Objects;

/** Worker-owned diagnostic intervals, read by the host only after worker join. */
final class UnitWorkTimeline {
    private long[] entityIds = new long[16];
    private long[] starts = new long[16];
    private long[] ends = new long[16];
    private long[] cpuTimes = new long[16];
    private String[] actions = new String[16];
    private int size;

    void record(long entityId, long startNanos, long endNanos, long cpuNanos, String action) {
        if (size == entityIds.length) {
            int capacity = entityIds.length * 2;
            entityIds = Arrays.copyOf(entityIds, capacity);
            starts = Arrays.copyOf(starts, capacity);
            ends = Arrays.copyOf(ends, capacity);
            cpuTimes = Arrays.copyOf(cpuTimes, capacity);
            actions = Arrays.copyOf(actions, capacity);
        }
        entityIds[size] = entityId;
        starts[size] = startNanos;
        ends[size] = endNanos;
        cpuTimes[size] = cpuNanos;
        actions[size] = action;
        size++;
    }

    /** Retains backing storage; the next dispatch overwrites earlier entries. */
    void reset() {
        Arrays.fill(actions, 0, size, null);
        size = 0;
    }

    int size() { return size; }
    long entityId(int index) { return entityIds[Objects.checkIndex(index, size)]; }
    long startNanos(int index) { return starts[Objects.checkIndex(index, size)]; }
    long endNanos(int index) { return ends[Objects.checkIndex(index, size)]; }
    /** Includes unavailable/invalid CPU-time sentinels exactly as recorded. */
    long cpuNanos(int index) { return cpuTimes[Objects.checkIndex(index, size)]; }
    String action(int index) { return actions[Objects.checkIndex(index, size)]; }
}
