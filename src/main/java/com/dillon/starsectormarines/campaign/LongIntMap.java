package com.dillon.starsectormarines.campaign;

import java.io.Serializable;

/**
 * Open-addressed {@code long → int} hash map with linear probing. Append-only:
 * keys can be put and read, never removed. That matches the
 * <code>roadmap/campaign/architecture.md</code> §1 soft-delete invariant —
 * SoA rows are tombstoned by status, not removed, so id→index mappings never
 * need to invalidate.
 *
 * <p>Why hand-rolled rather than {@code HashMap<Long, Integer>}: avoids per-lookup
 * boxing, keeps the SoA hot path free of {@code Long}/{@code Integer} allocations.
 * Persists via xstream (only two primitive arrays + an int — small).
 *
 * <p>Reserved sentinels:
 * <ul>
 *   <li>Key {@code 0} → "empty slot" marker. The campaign sequence counters
 *       start at {@code 1}, so {@code 0} is never used as a real key.</li>
 *   <li>{@link #get(long)} returns {@link #NOT_FOUND} ({@code -1}) for missing
 *       keys.</li>
 * </ul>
 */
public final class LongIntMap implements Serializable {

    public static final int NOT_FOUND = -1;

    private long[] keys;
    private int[] values;
    private int size;
    private int mask; // capacity - 1; capacity is power of two

    public LongIntMap() {
        this(16);
    }

    public LongIntMap(int initialCapacity) {
        int cap = nextPowerOfTwo(Math.max(4, initialCapacity));
        this.keys = new long[cap];
        this.values = new int[cap];
        this.mask = cap - 1;
        this.size = 0;
    }

    public int size() {
        return size;
    }

    /** Inserts or overwrites. Returns the previous value, or {@link #NOT_FOUND}. */
    public int put(long key, int value) {
        if (key == 0L) throw new IllegalArgumentException("key 0 is reserved");
        if ((size + 1) * 2 > keys.length) grow();
        int i = index(key);
        while (keys[i] != 0L) {
            if (keys[i] == key) {
                int prev = values[i];
                values[i] = value;
                return prev;
            }
            i = (i + 1) & mask;
        }
        keys[i] = key;
        values[i] = value;
        size++;
        return NOT_FOUND;
    }

    public int get(long key) {
        if (key == 0L) return NOT_FOUND;
        int i = index(key);
        while (keys[i] != 0L) {
            if (keys[i] == key) return values[i];
            i = (i + 1) & mask;
        }
        return NOT_FOUND;
    }

    public boolean containsKey(long key) {
        return get(key) != NOT_FOUND;
    }

    /** Resets to empty without releasing the array. */
    public void clear() {
        java.util.Arrays.fill(keys, 0L);
        java.util.Arrays.fill(values, 0);
        size = 0;
    }

    private int index(long key) {
        // splitmix64-style mixer for better distribution than identity hash
        long h = key;
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b7L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        h = h ^ (h >>> 31);
        return ((int) h) & mask;
    }

    private void grow() {
        long[] oldKeys = keys;
        int[] oldValues = values;
        int newCap = keys.length * 2;
        keys = new long[newCap];
        values = new int[newCap];
        mask = newCap - 1;
        size = 0;
        for (int j = 0; j < oldKeys.length; j++) {
            if (oldKeys[j] != 0L) put(oldKeys[j], oldValues[j]);
        }
    }

    private static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }
}
