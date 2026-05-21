package com.dillon.starsectormarines.battle.profile;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * Per-tick line-of-sight result cache. Memoizes
 * {@link com.dillon.starsectormarines.battle.nav.NavigationGrid#hasLineOfSight}
 * results across all callers within one sim tick — multiple firing-position
 * pickers scoring rings around the same target, target-search loops walking
 * the same candidates, alert-detection LoS scans rescanning the same pairs.
 *
 * <p>Key is symmetric: {@code los(a, b) == los(b, a)} since the underlying
 * Bresenham trace is endpoint-agnostic. We pack the two endpoints lexicographically
 * (smaller in the high half) so both directions hit the same slot, doubling
 * the effective hit rate over a directional key.
 *
 * <p>Coverage is the pure-grid path only — the air-LoS variant
 * ({@link com.dillon.starsectormarines.battle.ai.TurretAim#airLosVisible}) parameterizes
 * on shooter+target air radii that vary per caller, so caching it would
 * either bloat the key (rare path) or risk wrong answers (sharing a slot
 * across different radii). Drones/shuttles flow through uncached; the JFR
 * showed ground-vs-ground is the 99% case.
 *
 * <p>Access pattern mirrors {@link TickInnerProfile}: static
 * {@link #current()} slot the sim sets at tick begin and clears at tick
 * end. {@link com.dillon.starsectormarines.battle.nav.NavigationGrid#hasLineOfSight}
 * null-checks the slot and falls through to the live Bresenham when off-tick
 * (tests, mid-frame UI hooks).
 *
 * <p>Encoding: each endpoint packed as {@code (x << 12) | (y & 0xFFF)},
 * supporting grids up to 4096 cells per axis (ours are &lt; 200). The
 * symmetric pair packs into a long: {@code (max(s,t) << 32) | min(s,t)}.
 * Map values use an int sentinel so {@code Long2IntOpenHashMap.get} is a
 * single primitive lookup that returns -1 on miss, 0/1 on hit.
 * ({@code fastutil-core} doesn't ship {@code Long2Byte}/{@code Long2Boolean};
 * Long2Int is the closest primitive map available without bloating the
 * shaded fat-jar with the full fastutil distribution.)
 */
public final class LosCache {

    /** Sim sets this at the top of {@code tick()}. Single-threaded — no synchronization. */
    private static LosCache current;
    public static LosCache current() { return current; }
    public static void setCurrent(LosCache c) { current = c; }

    private static final int UNSET = -1;
    private static final int FALSE = 0;
    private static final int TRUE  = 1;

    private final Long2IntOpenHashMap cache = new Long2IntOpenHashMap();

    public LosCache() {
        cache.defaultReturnValue(UNSET);
    }

    /** Drop all cached pairs. Called once per tick. */
    public void clear() {
        cache.clear();
    }

    /**
     * Returns the cached visibility for the pair, or {@code -1} if not
     * present. Single primitive lookup (the default-return-value handles
     * the miss case); consumers compare {@code >= 0} for "cache hit."
     */
    public int tryGet(int sx, int sy, int tx, int ty) {
        return cache.get(packSymmetric(sx, sy, tx, ty));
    }

    public void put(int sx, int sy, int tx, int ty, boolean visible) {
        cache.put(packSymmetric(sx, sy, tx, ty), visible ? TRUE : FALSE);
    }

    /** Number of cached pairs. Diagnostic; not on the hot path. */
    public int size() { return cache.size(); }

    /**
     * Pairs the two endpoints into a single long key, normalizing by
     * sorting so {@code (a, b)} and {@code (b, a)} hash to the same slot.
     * Each endpoint is a 24-bit point (12 bits x, 12 bits y); the smaller
     * goes in the low 32 bits, the larger in the high 32.
     */
    private static long packSymmetric(int sx, int sy, int tx, int ty) {
        int s = (sx << 12) | (sy & 0xFFF);
        int t = (tx << 12) | (ty & 0xFFF);
        int lo, hi;
        if (s < t) { lo = s; hi = t; }
        else       { lo = t; hi = s; }
        return ((long) hi << 32) | (lo & 0xFFFFFFFFL);
    }
}
