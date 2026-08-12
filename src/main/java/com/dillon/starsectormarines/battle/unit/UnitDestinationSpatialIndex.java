package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.World;
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
 * <p><b>Id-native storage (identity-collapse Phase D).</b> Buckets hold bare
 * {@code long} entity ids in pooled {@link LongBucket}s, not {@code Entity}
 * refs — the ECS storage core carries ids, and callers resolve to whatever
 * they need by id. Bucket sizing + threading mirror {@link UnitSpatialIndex}
 * (same {@link UnitSpatialIndex#BUCKET} cell-side, same recycled-bucket pool,
 * same gather-over-caller-owned-buffer contract); read {@link UnitSpatialIndex}'s
 * class doc for the full rationale.
 *
 * <p>Incremental updates are routed through {@link #addDestination} and
 * {@link #removeDestination}, called from {@link com.dillon.starsectormarines.battle.nav.NavigationService#setPath}
 * alongside the existing occupancy-map maintenance. Keeps the index live
 * across mid-tick path changes; without this, mid-tick callers (tests
 * and behaviors that re-query right after a {@code setPath}) would see
 * stale buckets until the next {@link #rebuild}.
 */
public final class UnitDestinationSpatialIndex {

    private final int bucketsX;
    private final int bucketsY;
    private final LongBucket[] buckets;
    private final ArrayList<LongBucket> pool = new ArrayList<>();

    public UnitDestinationSpatialIndex(int gridWidth, int gridHeight) {
        this.bucketsX = Math.max(1, (gridWidth + UnitSpatialIndex.BUCKET - 1) / UnitSpatialIndex.BUCKET);
        this.bucketsY = Math.max(1, (gridHeight + UnitSpatialIndex.BUCKET - 1) / UnitSpatialIndex.BUCKET);
        this.buckets = new LongBucket[bucketsX * bucketsY];
    }

    /**
     * Discards previous bucket contents and re-bins every alive unit by
     * its <em>destination</em> cell. Units with no path, or whose path
     * destination equals their current cell, are skipped — they're already
     * accounted for by the current-cell index.
     *
     * <p>Dense iteration over {@code [0, liveCount())} excludes released slots.
     * Everything the bin needs — MOVEMENT presence, the path array, the current
     * cell — is read by id off the world columns; only the id is stored.
     */
    public void rebuild(UnitRosterService roster) {
        World world = roster.world();
        for (int i = 0; i < buckets.length; i++) {
            LongBucket b = buckets[i];
            if (b != null) {
                b.clear();
                pool.add(b);
                buckets[i] = null;
            }
        }
        long[] dense = roster.denseArray();
        int liveCount = roster.liveCount();
        for (int i = 0; i < liveCount; i++) {
            long id = dense[i];
            // Static emplacements (turrets, hubs) have no MOVEMENT component and
            // never path — skip before the fail-loud path read (an empty path
            // would be filtered by the cells<=0 check below anyway).
            if (!world.hasMovement(id)) continue;
            int[] path = world.path(id);
            int cells = Paths.cellCount(path);
            if (cells <= 0) continue;
            int destX = Paths.cellX(path, cells - 1);
            int destY = Paths.cellY(path, cells - 1);
            // Cell compare, not an arrival test: this is an occupancy-style
            // claim ("the unit's path already terminates on the cell it
            // currently occupies," so there's nothing left to index) — not a
            // check of whether the unit has arrived at a continuous position.
            if (destX == world.cellX(id) && destY == world.cellY(id)) continue;
            int bx = destX / UnitSpatialIndex.BUCKET;
            int by = destY / UnitSpatialIndex.BUCKET;
            if (bx < 0 || bx >= bucketsX || by < 0 || by >= bucketsY) continue;
            bucketAt(by * bucketsX + bx).add(id);
        }
    }

    /**
     * Inserts {@code id} into the bucket for ({@code destX}, {@code destY}).
     * Called from {@link com.dillon.starsectormarines.battle.nav.NavigationService#setPath}
     * after a new path is installed whose destination differs from the
     * unit's current cell. No-op for dead units or out-of-bounds buckets.
     * Caller is responsible for ensuring the unit isn't already present
     * at any bucket — pair with {@link #removeDestination} when overwriting
     * an existing path.
     */
    public void addDestination(UnitRosterService roster, long id, int destX, int destY) {
        if (!roster.isAliveById(id)) return;
        int bx = destX / UnitSpatialIndex.BUCKET;
        int by = destY / UnitSpatialIndex.BUCKET;
        if (bx < 0 || bx >= bucketsX || by < 0 || by >= bucketsY) return;
        bucketAt(by * bucketsX + bx).add(id);
    }

    /**
     * Removes {@code id} from the bucket for ({@code destX}, {@code destY}).
     * Called from {@link com.dillon.starsectormarines.battle.nav.NavigationService#setPath}
     * before a path overwrite (using the old destination) and also when a path
     * is cleared. Removal is by id value ({@link LongBucket#removeValue}); silent
     * no-op if the unit isn't present.
     */
    public void removeDestination(long id, int destX, int destY) {
        int bx = destX / UnitSpatialIndex.BUCKET;
        int by = destY / UnitSpatialIndex.BUCKET;
        if (bx < 0 || bx >= bucketsX || by < 0 || by >= bucketsY) return;
        int idx = by * bucketsX + bx;
        LongBucket bucket = buckets[idx];
        if (bucket == null) return;
        bucket.removeValue(id);
    }

    /** Lazily materializes (from the pool) the bucket at flat index {@code idx}. */
    private LongBucket bucketAt(int idx) {
        LongBucket bucket = buckets[idx];
        if (bucket == null) {
            bucket = pool.isEmpty() ? new LongBucket() : pool.remove(pool.size() - 1);
            buckets[idx] = bucket;
        }
        return bucket;
    }

    /**
     * Appends the id of every <em>alive</em> unit whose <em>destination</em>
     * cell sits within {@code radius} cells (Euclidean) of the continuous
     * point ({@code cx}, {@code cy}) into {@code out}. Clears {@code out} first.
     *
     * <p>Same gather contract and bucket-sweep math as
     * {@link UnitSpatialIndex#gather}; the difference is twofold: the radius
     * check is against each unit's path destination rather than its current
     * position, and the destination itself is a genuine grid cell (paths are
     * still cell-quantized) — so the distance test compares the destination
     * <em>cell's center</em> ({@code dest + 0.5}) against the float query
     * point, keeping the comparison apples-to-apples with a continuous-space
     * radius. Ids released since the last {@link #rebuild} are skipped — a
     * released id's by-id reads are unsafe under dense slot reuse, and a dead
     * unit is not a live destination occupant. Further filtering (faction,
     * combatant, ally exclusion) is the caller's job, matching the primary
     * index's "primitive over all alive units" semantics.
     */
    public void gather(UnitRosterService roster, float cx, float cy, float radius, LongBucket out) {
        out.clear();
        if (radius <= 0f) return;
        World world = roster.world();
        int loX = (int) Math.floor(cx - radius);
        int hiX = (int) Math.floor(cx + radius);
        int loY = (int) Math.floor(cy - radius);
        int hiY = (int) Math.floor(cy + radius);
        int x0 = Math.max(0, Math.floorDiv(loX, UnitSpatialIndex.BUCKET));
        int x1 = Math.min(bucketsX - 1, Math.floorDiv(hiX, UnitSpatialIndex.BUCKET));
        int y0 = Math.max(0, Math.floorDiv(loY, UnitSpatialIndex.BUCKET));
        int y1 = Math.min(bucketsY - 1, Math.floorDiv(hiY, UnitSpatialIndex.BUCKET));
        float r2 = radius * radius;
        for (int by = y0; by <= y1; by++) {
            for (int bx = x0; bx <= x1; bx++) {
                LongBucket bucket = buckets[by * bucketsX + bx];
                if (bucket == null) continue;
                for (int i = 0, n = bucket.size; i < n; i++) {
                    long id = bucket.ids[i];
                    if (!roster.isAliveById(id)) continue;
                    // Fetch-once: snapshot the path array from the world to a
                    // local — under the parallel UPDATE_UNITS dispatch another
                    // worker may call setPath on this unit. One load → consistent
                    // view for the length + index accesses below.
                    int[] p = world.path(id);
                    int cells = Paths.cellCount(p);
                    if (cells <= 0) continue;
                    float dx = (Paths.cellX(p, cells - 1) + 0.5f) - cx;
                    float dy = (Paths.cellY(p, cells - 1) + 0.5f) - cy;
                    if (dx * dx + dy * dy <= r2) out.add(id);
                }
            }
        }
    }
}
