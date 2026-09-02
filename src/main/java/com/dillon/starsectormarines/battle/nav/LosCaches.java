package com.dillon.starsectormarines.battle.nav;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The set of per-thread {@link LosCache}es belonging to one
 * {@link NavigationGrid}, and the tick window during which they are live.
 *
 * <p>One holder per grid, held by the grid itself. That ownership is the whole
 * point: the grid is what invalidates a cached sight line, so the caches a
 * topology change sweeps are exactly the ones that read the topology it
 * changed. Everything here was process-global once — a static enable flag, a
 * static {@link ThreadLocal}, a static instance list — and two simulations in
 * one JVM emptied each other's caches mid-tick and switched each other's
 * caching off. See {@link LosCache} for what that cost.
 *
 * <p>The {@link ThreadLocal} is an instance field, so a cache is keyed by
 * thread <em>and</em> holder. A worker thread that ticks one battle and later
 * another therefore holds a separate cache per grid rather than carrying one
 * grid's answers into the next.
 *
 * <p>Not thread-safe to enable or disable concurrently with a tick — those are
 * the owning simulation's own tick boundaries, called from its sim thread.
 * {@link #current()} and {@link #clearAll()} are safe from the worker threads
 * that dispatch inside a tick.
 */
public final class LosCaches {

    /**
     * Every per-thread cache handed out under this grid, so {@link #clearAll()}
     * can sweep them in one pass — the parallel UPDATE_UNITS dispatch creates
     * per-worker caches lazily, and every one of them has to be dropped before
     * the next tick reads it.
     */
    private final List<LosCache> instances = new CopyOnWriteArrayList<>();
    private final ThreadLocal<LosCache> current = new ThreadLocal<>();

    /**
     * Whether the owning simulation is inside a tick. Outside that window
     * {@link #current()} returns {@code null} and
     * {@link NavigationGrid#hasLineOfSight} falls through to the live Bresenham
     * trace, so off-tick callers (tests, mid-frame UI hooks) cannot populate a
     * cache that would then hold stale entries across a topology change.
     */
    private volatile boolean enabled = false;

    /** Signals "this grid's simulation is inside a tick" — caching active, {@link #current()} auto-inits per thread. */
    public void enable() { enabled = true; }

    /** Signals "this grid's simulation is between ticks" — {@link #current()} returns {@code null}. */
    public void disable() { enabled = false; }

    /** Whether caching is on. Diagnostic; the read sites go through {@link #current()}. */
    public boolean isEnabled() { return enabled; }

    /** This thread's cache for this grid, or {@code null} off-tick. Lazily created and registered for the sweep. */
    public LosCache current() {
        if (!enabled) return null;
        LosCache cache = current.get();
        if (cache == null) {
            cache = new LosCache();
            current.set(cache);
            instances.add(cache);
        }
        return cache;
    }

    /**
     * Removes the calling thread's cache for this grid from the sweep. Called
     * when a battle-owned update worker terminates and when the simulation
     * that owns the grid closes.
     */
    public void releaseCurrentThread() {
        LosCache cache = current.get();
        if (cache != null) instances.remove(cache);
        current.remove();
    }

    /**
     * Drops every per-thread cache for this grid in one sweep. Called at the
     * top of each of the owning simulation's ticks, and by the grid itself on
     * any change to what blocks sight, so a cached pair cannot outlive the
     * wall it was traced through.
     */
    public void clearAll() {
        for (LosCache cache : instances) cache.clear();
    }

    /** How many per-thread caches this grid is tracking. Diagnostic; not on the hot path. */
    public int trackedWorkerCount() {
        return instances.size();
    }
}
