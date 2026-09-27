package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import java.util.ArrayList;
import java.util.Arrays;

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
    /**
     * Grow-and-stay rebuild scratch keyed by roster-dense index. The column
     * pass gathers eligible movement destinations in archetype-row order;
     * the insertion pass then walks roster-dense order so bucket entry order
     * remains exactly the same as the original by-id rebuild.
     */
    private long[] scratchIds = new long[64];
    private int[] scratchDestX = new int[64];
    private int[] scratchDestY = new int[64];

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
     * <p>A column walk gathers movement paths and current positions into
     * grow-and-stay scratch keyed by roster-dense index. The insertion pass
     * then walks {@code [0, liveCount())}, preserving the roster-dense bucket
     * order while avoiding repeated by-id location probes. Only the id is
     * stored in the destination bucket.
     */
    public void rebuild(UnitRosterService roster) {
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
        ensureScratchCapacity(liveCount);
        Arrays.fill(scratchIds, 0, liveCount, 0L);

        EntityWorld world = roster.entityWorld();
        BattleComponents components = roster.components();
        for (ArchetypeTable table : world.matched(components.gridOccupants)) {
            // Static emplacements carry POSITION but no MOVEMENT. Partition
            // them once per table instead of probing component presence for
            // every roster id.
            if (!table.has(components.MOVEMENT)) continue;
            Object[] paths = table.objects(components.MOVEMENT,
                    BattleComponents.MOVEMENT_PATH).array();
            float[] posX = table.floats(components.POSITION,
                    BattleComponents.POSITION_X).array();
            float[] posY = table.floats(components.POSITION,
                    BattleComponents.POSITION_Y).array();
            for (int row = 0, rows = table.rowCount(); row < rows; row++) {
                long id = table.entityAt(row);
                int denseIndex = roster.indexOf(id);
                if (denseIndex == UnitRosterService.INVALID_INDEX) continue;
                int[] path = (int[]) paths[row];
                int cells = Paths.cellCount(path);
                if (cells <= 0) continue;
                int destX = Paths.cellX(path, cells - 1);
                int destY = Paths.cellY(path, cells - 1);
                // Cell compare, not an arrival test: this is an
                // occupancy-style claim, so floor the continuous POSITION
                // columns exactly as World.cellX/cellY did before.
                if (destX == (int) Math.floor(posX[row])
                        && destY == (int) Math.floor(posY[row])) continue;
                scratchIds[denseIndex] = id;
                scratchDestX[denseIndex] = destX;
                scratchDestY[denseIndex] = destY;
            }
        }

        for (int i = 0; i < liveCount; i++) {
            long id = dense[i];
            if (scratchIds[i] != id) continue;
            int destX = scratchDestX[i];
            int destY = scratchDestY[i];
            int bx = destX / UnitSpatialIndex.BUCKET;
            int by = destY / UnitSpatialIndex.BUCKET;
            if (bx < 0 || bx >= bucketsX || by < 0 || by >= bucketsY) continue;
            bucketAt(by * bucketsX + bx).add(id);
        }
    }

    private void ensureScratchCapacity(int need) {
        if (need <= scratchIds.length) return;
        int capacity = Math.max(need, scratchIds.length << 1);
        scratchIds = Arrays.copyOf(scratchIds, capacity);
        scratchDestX = Arrays.copyOf(scratchDestX, capacity);
        scratchDestY = Arrays.copyOf(scratchDestY, capacity);
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

    /** Captures each live endpoint once and retains its index bucket for exact
     * subquery filtering when paths have changed since the host index rebuild. */
    public void gather(UnitRosterService roster, float cx, float cy, float radius,
                       UnitSpatialIndex.PointBuffer out) {
        out.clear();
        if (radius <= 0f) return;
        World world = roster.world();
        int x0 = Math.max(0, Math.floorDiv((int) Math.floor(cx - radius), UnitSpatialIndex.BUCKET));
        int x1 = Math.min(bucketsX - 1, Math.floorDiv((int) Math.floor(cx + radius), UnitSpatialIndex.BUCKET));
        int y0 = Math.max(0, Math.floorDiv((int) Math.floor(cy - radius), UnitSpatialIndex.BUCKET));
        int y1 = Math.min(bucketsY - 1, Math.floorDiv((int) Math.floor(cy + radius), UnitSpatialIndex.BUCKET));
        float r2 = radius * radius;
        for (int by = y0; by <= y1; by++) for (int bx = x0; bx <= x1; bx++) {
            LongBucket bucket = buckets[by * bucketsX + bx];
            if (bucket == null) continue;
            for (int i = 0; i < bucket.size; i++) {
                long id = bucket.ids[i];
                if (!roster.isAliveById(id)) continue;
                int[] path = world.path(id);
                int cells = Paths.cellCount(path);
                if (cells <= 0) continue;
                float x = Paths.cellX(path, cells - 1) + 0.5f;
                float y = Paths.cellY(path, cells - 1) + 0.5f;
                float dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy <= r2) out.add(id, x, y, bx, by);
            }
        }
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
