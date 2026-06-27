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
 * <p><b>Snapshot positions.</b> Each entry denormalizes the unit's cell at
 * insert time into a {@link Bucket}'s parallel {@code cellX}/{@code cellY}
 * arrays, so {@link #gather}'s distance filter reads a stored int rather than
 * the unit's live cell. This (a) avoids a per-candidate by-id probe during gather
 * — no registry lookup per distance candidate — and (b) makes
 * the index self-consistent: bucketing <em>and</em> the distance test both use
 * the same rebuild-time position. Queries therefore see positions as of the
 * last {@link #rebuild}, which is exactly the per-tick-snapshot contract.
 *
 * <p><b>Allocation.</b> {@link Bucket}s are recycled into {@link #pool} between
 * rebuilds and their backing arrays grow-and-stay, so steady-state allocation
 * is zero. Callers passing an output {@link ArrayList} to {@link #gather} pay
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
     * One spatial bucket: parallel arrays of unit refs and their rebuild-time
     * snapshot cell, grown on demand and recycled across rebuilds so
     * steady-state allocation stays zero. The snapshot cell is what lets
     * {@link #gather} filter by distance without reading the position back off
     * the unit (no SoA indirection, no registry probe per candidate).
     */
    private static final class Bucket {
        Entity[] units = new Entity[8];
        int[] cellX = new int[8];
        int[] cellY = new int[8];
        int size;

        void add(Entity u, int x, int y) {
            if (size == units.length) {
                int cap = size << 1;
                units = Arrays.copyOf(units, cap);
                cellX = Arrays.copyOf(cellX, cap);
                cellY = Arrays.copyOf(cellY, cap);
            }
            units[size] = u;
            cellX[size] = x;
            cellY[size] = y;
            size++;
        }

        /** Clears for reuse, nulling unit slots so a released unit isn't pinned alive between rebuilds. */
        void clear() {
            for (int i = 0; i < size; i++) units[i] = null;
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
     * per-call {@code isAlive()} branch in the inner loop. Cell positions
     * are read via the world POSITION columns by-id adapters
     * ({@code cellXById} / {@code cellYById}), then stored alongside the
     * {@code Entity} ref in the bucket so {@link #gather} never has to read
     * them back.
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
        Entity[] dense = roster.denseArray();
        int liveCount = roster.liveCount();
        for (int i = 0; i < liveCount; i++) {
            Entity u = dense[i];
            int x = world.cellX(u.entityId);
            int y = world.cellY(u.entityId);
            Bucket bucket = bucketAt(x, y);
            if (bucket != null) bucket.add(u, x, y);
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
     * <p>Takes the registry to resolve the unit's cell once (by entity id via
     * the world POSITION column adapters) — the cell is denormalized into the
     * bucket, mirroring {@link #rebuild}.
     */
    public void add(UnitRosterService roster, Entity u) {
        this.roster = roster;
        if (!roster.isAliveById(u.entityId)) return;
        World world = roster.world();
        int x = world.cellX(u.entityId);
        int y = world.cellY(u.entityId);
        Bucket bucket = bucketAt(x, y);
        if (bucket != null) bucket.add(u, x, y);
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
     * ({@code cx}, {@code cy}) into {@code out}. Clears {@code out} first.
     * The radius check uses squared-distance for cost; the bucket bounds use
     * Manhattan ceiling so the bucket sweep stays a fixed-size grid.
     *
     * <p>Returns nothing — callers iterate {@code out}. Filtering by faction,
     * combatant flag, or per-unit attack range is left to the caller: the
     * index is a primitive over <em>all</em> alive units, not a slice.
     */
    public void gather(int cx, int cy, float radius, ArrayList<Entity> out) {
        out.clear();
        if (radius <= 0f) return;
        int r = (int) Math.ceil(radius);
        int x0 = Math.max(0, (cx - r) / BUCKET);
        int x1 = Math.min(bucketsX - 1, (cx + r) / BUCKET);
        int y0 = Math.max(0, (cy - r) / BUCKET);
        int y1 = Math.min(bucketsY - 1, (cy + r) / BUCKET);
        // Avoid Math.ceil rounding when (cx - r) goes negative — integer
        // division rounds toward zero, so we'd shift the lower bound into
        // a too-low bucket index. Clamp at 0 above.
        float r2 = radius * radius;
        for (int by = y0; by <= y1; by++) {
            for (int bx = x0; bx <= x1; bx++) {
                Bucket bucket = buckets[by * bucketsX + bx];
                if (bucket == null) continue;
                Entity[] units = bucket.units;
                int[] bcx = bucket.cellX;
                int[] bcy = bucket.cellY;
                for (int i = 0, n = bucket.size; i < n; i++) {
                    Entity u = units[i];
                    // Skip units released since the last rebuild — the index is a
                    // per-tick snapshot, so a unit killed (and registry-released)
                    // mid-tick lingers in its old bucket until then. The snapshot
                    // cell below is a stored int (no fail-loud read), but the
                    // "alive units only" contract still requires the skip so dead
                    // units aren't handed back. (Callers also filter, but gather
                    // owns the contract.)
                    if (!roster.isAliveById(u.entityId)) continue;
                    int dx = bcx[i] - cx;
                    int dy = bcy[i] - cy;
                    if (dx * dx + dy * dy <= r2) out.add(u);
                }
            }
        }
    }
}
