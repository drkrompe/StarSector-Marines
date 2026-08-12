package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.sim.World;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Bucketed spatial index over alive units. Rebuilt once per sim tick so AI
 * queries that need "all units near (x, y)" — exposure scoring,
 * threat-density, allies-near-for-spread — can replace O(N) list scans with
 * O(small) bucket walks.
 *
 * <p><b>Bucket sizing.</b> {@link #BUCKET} is set to the order of average
 * weapon line-of-sight (rifles 18, mechs 30, LRMs 40) so a radius-R query
 * touches roughly {@code (R/BUCKET)²} buckets. With R=20 and BUCKET=16 that
 * is 4–9 buckets — bounded regardless of total unit count.
 *
 * <p><b>Snapshot positions.</b> Each entry denormalizes the unit's TRUE
 * (continuous) position at insert time into a {@link Bucket}'s parallel
 * {@code posX}/{@code posY} arrays, so {@link #gather}'s distance filter reads
 * a stored float rather than the unit's live position. This (a) avoids a
 * per-candidate by-id probe during gather — no registry lookup per distance
 * candidate — and (b) makes the index self-consistent: bucketing <em>and</em>
 * the distance test both use the same rebuild-time position (bucketing bins
 * on the floored cell of that position; the distance test compares the
 * unfloored float). Queries therefore see positions as of the last
 * {@link #rebuild}, which is exactly the per-tick-snapshot contract.
 *
 * <p><b>Allocation.</b> {@link Bucket}s are recycled into {@link #pool} between
 * rebuilds and their backing arrays grow-and-stay, so steady-state allocation
 * is zero. Callers passing an output {@link LongBucket} to {@link #gather} pay
 * nothing per call past clearing the buffer.
 *
 * <p><b>Threading.</b> Single-threaded against the sim today. The squad-GOAP
 * replan loop is the next likely parallel surface — when that lands, this
 * class's reads are safe (no mutation), but each parallel worker needs its
 * own output buffer for {@link #gather}.
 */
public final class UnitSpatialIndex {

    /**
     * Cell-side of each bucket. Picked at the order of average effective
     * weapon LoS so a typical query covers ≤ 4 buckets per axis. Higher
     * values inflate per-query work; lower values inflate the bucket-array
     * itself. 16 is the sweet spot for current maps (60–120 cells across).
     */
    public static final int BUCKET = 16;

    /**
     * One spatial bucket: parallel arrays of unit ids and their rebuild-time
     * snapshot TRUE position, grown on demand and recycled across rebuilds so
     * steady-state allocation stays zero. The snapshot position is what lets
     * {@link #gather} filter by distance without reading the position back off
     * the unit (no SoA indirection, no registry probe per candidate).
     */
    private static final class Bucket {
        long[] ids = new long[8];
        float[] posX = new float[8];
        float[] posY = new float[8];
        int size;

        void add(long id, float x, float y) {
            if (size == ids.length) {
                int cap = size << 1;
                ids = Arrays.copyOf(ids, cap);
                posX = Arrays.copyOf(posX, cap);
                posY = Arrays.copyOf(posY, cap);
            }
            ids[size] = id;
            posX[size] = x;
            posY[size] = y;
            size++;
        }

        /** Clears for reuse. Ids are primitives, so there's no reference to null out — a released unit isn't pinned (the bucket holds no object). */
        void clear() {
            size = 0;
        }
    }

    private final int bucketsX;
    private final int bucketsY;
    private final Bucket[] buckets;
    private final ArrayList<Bucket> pool = new ArrayList<>();
    /**
     * The registry the buckets were populated from, stashed by {@link #rebuild}
     * / {@link #add} so {@link #gather} can drop units released since the last
     * rebuild ({@code isAliveById}) without taking a registry on its hot
     * signature (it has many callers). The registry instance is stable for the
     * battle, so caching the reference is safe; it's only dereferenced inside
     * the bucket loop, which never runs until a populate path has set it.
     */
    private UnitRosterService roster;

    public UnitSpatialIndex(int gridWidth, int gridHeight) {
        this.bucketsX = Math.max(1, (gridWidth + BUCKET - 1) / BUCKET);
        this.bucketsY = Math.max(1, (gridHeight + BUCKET - 1) / BUCKET);
        this.buckets = new Bucket[bucketsX * bucketsY];
    }

    /**
     * Discards the previous bucket contents and re-bins every alive unit by
     * its current cell. Called once per sim tick before per-unit updates.
     *
     * <p>Iterates the {@link UnitRosterService}'s dense {@code [0, liveCount())}
     * range directly — released slots are excluded by the roster, so no
     * per-call {@code isAlive()} branch in the inner loop. TRUE positions
     * are read via the world POSITION columns by-id adapters
     * ({@link World#x(long)} / {@link World#y(long)}), binned into a bucket
     * by their floored cell, then stored (unfloored) alongside the id so
     * {@link #gather} never has to read them back.
     */
    public void rebuild(UnitRosterService roster) {
        this.roster = roster;
        World world = roster.world();
        for (int i = 0; i < buckets.length; i++) {
            Bucket b = buckets[i];
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
            float x = world.x(id);
            float y = world.y(id);
            Bucket bucket = bucketAt((int) Math.floor(x), (int) Math.floor(y));
            if (bucket != null) bucket.add(id, x, y);
        }
    }

    /**
     * Inserts {@code u} at its current cell. Used for incremental updates
     * between full {@link #rebuild} calls — primarily so test fixtures that
     * skip the tick loop still see units they just added. Dead units are
     * skipped (the index never holds them). A unit appearing twice in the
     * same bucket would double-count; callers must guarantee a unit isn't
     * already in the index when calling this. {@code addUnit} on
     * {@link UnitRosterService} is the only caller and is the sole add-path
     * for live units, so the contract holds in practice.
     *
     * <p>Takes the registry to resolve the unit's position once (by entity id
     * via the world POSITION column adapters) — the position is denormalized
     * into the bucket, mirroring {@link #rebuild}.
     */
    public void add(UnitRosterService roster, long id) {
        this.roster = roster;
        if (!roster.isAliveById(id)) return;
        World world = roster.world();
        float x = world.x(id);
        float y = world.y(id);
        Bucket bucket = bucketAt((int) Math.floor(x), (int) Math.floor(y));
        if (bucket != null) bucket.add(id, x, y);
    }

    /**
     * Returns the bucket covering ({@code cellX}, {@code cellY}), allocating
     * one from the pool on first use, or {@code null} if the cell is off-grid.
     */
    private Bucket bucketAt(int cellX, int cellY) {
        int bx = cellX / BUCKET;
        int by = cellY / BUCKET;
        if (bx < 0 || bx >= bucketsX || by < 0 || by >= bucketsY) return null;
        int idx = by * bucketsX + bx;
        Bucket bucket = buckets[idx];
        if (bucket == null) {
            bucket = pool.isEmpty() ? new Bucket() : pool.remove(pool.size() - 1);
            buckets[idx] = bucket;
        }
        return bucket;
    }

    /**
     * Appends every alive unit within {@code radius} cells (Euclidean) of
     * the continuous point ({@code cx}, {@code cy}) into {@code out}. Clears
     * {@code out} first. The radius check uses squared-distance for cost; the
     * bucket bounds are the floored cells of the query circle's bounding box,
     * converted to bucket indices with a floor division (not truncating
     * integer division — the query point or radius can put the box's low
     * edge below 0).
     *
     * <p>Returns nothing — callers iterate {@code out}. Filtering by faction,
     * combatant flag, or per-unit attack range is left to the caller: the
     * index is a primitive over <em>all</em> alive units, not a slice.
     */
    public void gather(float cx, float cy, float radius, LongBucket out) {
        out.clear();
        if (radius <= 0f) return;
        int loX = (int) Math.floor(cx - radius);
        int hiX = (int) Math.floor(cx + radius);
        int loY = (int) Math.floor(cy - radius);
        int hiY = (int) Math.floor(cy + radius);
        int x0 = Math.max(0, Math.floorDiv(loX, BUCKET));
        int x1 = Math.min(bucketsX - 1, Math.floorDiv(hiX, BUCKET));
        int y0 = Math.max(0, Math.floorDiv(loY, BUCKET));
        int y1 = Math.min(bucketsY - 1, Math.floorDiv(hiY, BUCKET));
        float r2 = radius * radius;
        for (int by = y0; by <= y1; by++) {
            for (int bx = x0; bx <= x1; bx++) {
                Bucket bucket = buckets[by * bucketsX + bx];
                if (bucket == null) continue;
                long[] ids = bucket.ids;
                float[] bpx = bucket.posX;
                float[] bpy = bucket.posY;
                for (int i = 0, n = bucket.size; i < n; i++) {
                    long id = ids[i];
                    // Skip units released since the last rebuild — the index is a
                    // per-tick snapshot, so a unit killed (and registry-released)
                    // mid-tick lingers in its old bucket until then. The snapshot
                    // position below is a stored float (no fail-loud read), but the
                    // "alive units only" contract still requires the skip so dead
                    // units aren't handed back. (Callers also filter, but gather
                    // owns the contract.)
                    if (!roster.isAliveById(id)) continue;
                    float dx = bpx[i] - cx;
                    float dy = bpy[i] - cy;
                    if (dx * dx + dy * dy <= r2) out.add(id);
                }
            }
        }
    }
}
