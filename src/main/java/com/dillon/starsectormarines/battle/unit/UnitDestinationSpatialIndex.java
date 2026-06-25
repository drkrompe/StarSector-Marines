package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.nav.Paths;
import java.util.ArrayList;

/**
 * Sister index to {@link UnitSpatialIndex}, but keyed on each unit's
 * <em>path destination</em> cell instead of its current cell. Lets AI
 * scoring loops that need "units whose destination is near (cx, cy)" —
 * the spread-out portion of {@link com.dillon.starsectormarines.battle.decision.TacticalScoring#alliesNearForSpread}
 * — drop the residual O(N) walk over every alive unit.
 *
 * <p><b>Inclusion rule.</b> Only units that have a non-empty path AND
 * whose destination cell differs from their current cell are indexed.
 * Anyone standing still is already covered by the current-cell index;
 * including them here would force callers to dedupe. Callers should
 * still skip dest-index hits whose current cell falls inside the same
 * query radius — that's the "already counted by Pass 1" case for
 * alliesNearForSpread.
 *
 * <p><b>Bucket sizing + threading + pooling</b> mirror {@link UnitSpatialIndex}.
 * Same {@link UnitSpatialIndex#BUCKET} cell-side, same pool of recycled
 * {@code ArrayList<Entity>} buckets, same gather contract over an output
 * buffer the caller owns. Read {@link UnitSpatialIndex}'s class doc for
 * the full rationale.
 *
 * <p>Incremental updates are routed through {@link #addDestination} and
 * {@link #removeDestination}, called from {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#setPath}
 * alongside the existing occupancy-map maintenance. Keeps the index live
 * across mid-tick path changes; without this, mid-tick callers (tests
 * and behaviors that re-query right after a {@code setPath}) would see
 * stale buckets until the next {@link #rebuild}.
 */
public final class UnitDestinationSpatialIndex {

    private final int bucketsX;
    private final int bucketsY;
    private final ArrayList<Entity>[] buckets;
    private final ArrayList<ArrayList<Entity>> pool = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public UnitDestinationSpatialIndex(int gridWidth, int gridHeight) {
        this.bucketsX = Math.max(1, (gridWidth + UnitSpatialIndex.BUCKET - 1) / UnitSpatialIndex.BUCKET);
        this.bucketsY = Math.max(1, (gridHeight + UnitSpatialIndex.BUCKET - 1) / UnitSpatialIndex.BUCKET);
        this.buckets = (ArrayList<Entity>[]) new ArrayList[bucketsX * bucketsY];
    }

    /**
     * Discards previous bucket contents and re-bins every alive unit by
     * its <em>destination</em> cell. Units with no path, or whose path
     * destination equals their current cell, are skipped — they're already
     * accounted for by the current-cell index.
     *
     * <p>Dense iteration over {@code [0, liveCount())} excludes released slots.
     * The stand-still check reads current cellX/cellY via the world POSITION
     * column by-id adapters ({@code cellXById} / {@code cellYById}).
     * The path data is still per-Entity (no component storage for path arrays),
     * so the destination read goes through the Entity ref.
     */
    public void rebuild(UnitRegistry registry) {
        for (int i = 0; i < buckets.length; i++) {
            ArrayList<Entity> b = buckets[i];
            if (b != null) {
                b.clear();
                pool.add(b);
                buckets[i] = null;
            }
        }
        Entity[] dense = registry.denseArray();
        int liveCount = registry.liveCount();
        for (int i = 0; i < liveCount; i++) {
            Entity u = dense[i];
            // Static emplacements (turrets, hubs) have no MOVEMENT component and
            // never path — skip before the fail-loud path read (an empty path
            // would be filtered by the cells<=0 check below anyway).
            if (!registry.hasMovement(u.entityId)) continue;
            int[] path = registry.pathById(u.entityId);
            int cells = Paths.cellCount(path);
            if (cells <= 0) continue;
            int destX = Paths.cellX(path, cells - 1);
            int destY = Paths.cellY(path, cells - 1);
            if (destX == registry.cellXById(u.entityId) && destY == registry.cellYById(u.entityId)) continue;
            int bx = destX / UnitSpatialIndex.BUCKET;
            int by = destY / UnitSpatialIndex.BUCKET;
            if (bx < 0 || bx >= bucketsX || by < 0 || by >= bucketsY) continue;
            int idx = by * bucketsX + bx;
            ArrayList<Entity> bucket = buckets[idx];
            if (bucket == null) {
                bucket = pool.isEmpty()
                        ? new ArrayList<>(8)
                        : pool.remove(pool.size() - 1);
                buckets[idx] = bucket;
            }
            bucket.add(u);
        }
    }

    /**
     * Inserts {@code u} into the bucket for ({@code destX}, {@code destY}).
     * Called from {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#setPath}
     * after a new path is installed whose destination differs from the
     * unit's current cell. No-op for dead units or out-of-bounds buckets.
     * Caller is responsible for ensuring the unit isn't already present
     * at any bucket — pair with {@link #removeDestination} when overwriting
     * an existing path.
     */
    public void addDestination(UnitRegistry registry, Entity u, int destX, int destY) {
        if (!registry.isAliveById(u.entityId)) return;
        int bx = destX / UnitSpatialIndex.BUCKET;
        int by = destY / UnitSpatialIndex.BUCKET;
        if (bx < 0 || bx >= bucketsX || by < 0 || by >= bucketsY) return;
        int idx = by * bucketsX + bx;
        ArrayList<Entity> bucket = buckets[idx];
        if (bucket == null) {
            bucket = pool.isEmpty()
                    ? new ArrayList<>(8)
                    : pool.remove(pool.size() - 1);
            buckets[idx] = bucket;
        }
        bucket.add(u);
    }

    /**
     * Removes {@code u} from the bucket for ({@code destX}, {@code destY}).
     * Called from {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#setPath}
     * before a path overwrite (using the old destination) and also when
     * a path is cleared. Entity identity is by reference — {@link Entity}
     * doesn't override {@code equals}, so {@code ArrayList.remove(Object)}
     * does the right thing. Silent no-op if the unit isn't present.
     */
    public void removeDestination(Entity u, int destX, int destY) {
        int bx = destX / UnitSpatialIndex.BUCKET;
        int by = destY / UnitSpatialIndex.BUCKET;
        if (bx < 0 || bx >= bucketsX || by < 0 || by >= bucketsY) return;
        int idx = by * bucketsX + bx;
        ArrayList<Entity> bucket = buckets[idx];
        if (bucket == null) return;
        bucket.remove(u);
    }

    /**
     * Appends every alive unit whose <em>destination</em> cell sits within
     * {@code radius} cells (Euclidean) of ({@code cx}, {@code cy}) into
     * {@code out}. Clears {@code out} first.
     *
     * <p>Same gather contract and bucket-sweep math as
     * {@link UnitSpatialIndex#gather}; the only difference is that the
     * radius check is against each unit's path destination rather than
     * its current cell. Filtering (faction, combatant, ally exclusion)
     * is the caller's job, matching the primary index's "primitive over
     * all alive units" semantics.
     */
    public void gather(UnitRegistry registry, int cx, int cy, float radius, ArrayList<Entity> out) {
        out.clear();
        if (radius <= 0f) return;
        int r = (int) Math.ceil(radius);
        int x0 = Math.max(0, (cx - r) / UnitSpatialIndex.BUCKET);
        int x1 = Math.min(bucketsX - 1, (cx + r) / UnitSpatialIndex.BUCKET);
        int y0 = Math.max(0, (cy - r) / UnitSpatialIndex.BUCKET);
        int y1 = Math.min(bucketsY - 1, (cy + r) / UnitSpatialIndex.BUCKET);
        float r2 = radius * radius;
        for (int by = y0; by <= y1; by++) {
            for (int bx = x0; bx <= x1; bx++) {
                ArrayList<Entity> bucket = buckets[by * bucketsX + bx];
                if (bucket == null) continue;
                for (int i = 0, n = bucket.size(); i < n; i++) {
                    Entity u = bucket.get(i);
                    // Fetch-once: snapshot the path array from the registry
                    // to a local — under the parallel UPDATE_UNITS dispatch,
                    // another worker may call setPath on this unit. One load
                    // → consistent view for the length + index accesses below.
                    int[] p = registry.pathById(u.entityId);
                    int cells = Paths.cellCount(p);
                    if (cells <= 0) continue;
                    int dx = Paths.cellX(p, cells - 1) - cx;
                    int dy = Paths.cellY(p, cells - 1) - cy;
                    if (dx * dx + dy * dy <= r2) out.add(u);
                }
            }
        }
    }
}
