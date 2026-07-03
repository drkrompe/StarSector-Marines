package com.dillon.starsectormarines.battle.unit;

import java.util.Arrays;

/**
 * Minimal growable primitive-{@code long} list — the id-native storage element
 * for the spatial-index buckets (identity-collapse Phase D), replacing the
 * {@code ArrayList<Entity>} buckets so the ECS storage core holds bare entity
 * ids with no {@code Long}/{@code Entity} boxing.
 *
 * <p><b>Order-agnostic.</b> {@link #removeValue} swaps the removed slot with the
 * last element — spatial buckets and gather scratch don't care about element
 * order, so removal skips the O(n) element shift a mid-list {@code ArrayList.remove}
 * would do (the linear find to locate the value remains, but there's no compaction).
 *
 * <p>{@link #ids} and {@link #size} are public for direct hot-loop iteration,
 * matching the SoA column-array style used elsewhere in the battle tier — read
 * {@code ids[0..size)}. Never read past {@code size}; stale slots are not cleared
 * on {@link #clear}.
 */
public final class LongBucket {

    /** Backing store; valid entries are {@code ids[0..size)}. May be reallocated by {@link #add}. */
    public long[] ids = new long[8];

    /** Live element count. */
    public int size;

    /** Appends {@code id}, growing the backing array if full. */
    public void add(long id) {
        if (size == ids.length) ids = Arrays.copyOf(ids, size << 1);
        ids[size++] = id;
    }

    /**
     * Removes the first occurrence of {@code id} by swapping it with the last
     * element (order-agnostic). Returns {@code true} if found. No-op + {@code false}
     * if absent — matches the old {@code ArrayList.remove(Object)} silent-miss contract.
     */
    public boolean removeValue(long id) {
        for (int i = 0; i < size; i++) {
            if (ids[i] == id) {
                ids[i] = ids[--size];
                ids[size] = 0L;
                return true;
            }
        }
        return false;
    }

    /** Empties the list (does not shrink the backing array). */
    public void clear() {
        size = 0;
    }
}
