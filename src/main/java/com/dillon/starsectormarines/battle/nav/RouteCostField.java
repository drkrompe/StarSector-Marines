package com.dillon.starsectormarines.battle.nav;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A per-cell traversal multiplier published as one immutable snapshot, carrying
 * the revision that identifies it.
 *
 * <p>The bare {@code float[]} the pathfinders already accept is enough to bend
 * a route. It is not enough to <em>cache</em> one: {@link SharedGoalPathfinder}
 * keeps a reverse tree per goal and reuses it across snapshots, so a field
 * whose contents changed underneath it would keep serving routes computed
 * against costs that no longer exist. The revision is what lets that cache tell
 * two costings apart, which is why it travels with the array instead of beside
 * it.
 *
 * <p>Distinct producers must not share a revision sequence: the marine and
 * defender loss costings are different fields for the same map and would
 * otherwise collide in the cache. {@link #nextRevision()} hands out values
 * unique across every field in the process, which makes that impossible to get
 * wrong by accident.
 *
 * <p><b>Baseline is 1.0 and values only rise from there.</b> A multiplier below
 * one would break the octile heuristic's admissibility in every A* that reads
 * this, so a producer discourages ground rather than encouraging it.
 */
public final class RouteCostField {

    private static final AtomicLong REVISIONS = new AtomicLong(1L);

    private final float[] cells;
    private final long revision;

    public RouteCostField(float[] cells, long revision) {
        this.cells = cells;
        this.revision = revision;
    }

    /** A revision distinct from every other one handed out in this process. */
    public static long nextRevision() {
        return REVISIONS.getAndIncrement();
    }

    /**
     * The multiplier per cell, indexed by {@link NavigationGrid#index}. Handed
     * out by reference because it is read once per expansion in the pathfinder
     * inner loops; treat it as immutable, since retained reverse trees are
     * keyed on the belief that it does not change.
     */
    public float[] cells() { return cells; }

    public long revision() { return revision; }
}
