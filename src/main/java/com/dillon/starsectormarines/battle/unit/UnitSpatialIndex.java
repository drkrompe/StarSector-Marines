package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import com.dillon.starsectormarines.battle.unit.UnitRegistry;

import java.util.ArrayList;

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
 * <p><b>Allocation.</b> Bucket lists are recycled into {@link #pool} between
 * rebuilds, so steady-state allocation is zero. Callers passing an output
 * {@link ArrayList} to {@link #gather} pay nothing per call past clearing
 * the buffer.
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

    private final int bucketsX;
    private final int bucketsY;
    private final ArrayList<Unit>[] buckets;
    private final ArrayList<ArrayList<Unit>> pool = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public UnitSpatialIndex(int gridWidth, int gridHeight) {
        this.bucketsX = Math.max(1, (gridWidth + BUCKET - 1) / BUCKET);
        this.bucketsY = Math.max(1, (gridHeight + BUCKET - 1) / BUCKET);
        this.buckets = (ArrayList<Unit>[]) new ArrayList[bucketsX * bucketsY];
    }

    /**
     * Discards the previous bucket contents and re-bins every alive unit by
     * its current cell. Called once per sim tick before per-unit updates.
     *
     * <p>Iterates the {@link UnitRegistry}'s dense {@code [0, liveCount())}
     * range directly — released slots are excluded by the registry, so no
     * per-call {@code isAlive()} branch in the inner loop. Cell positions
     * are read from the SoA arrays ({@code cellXArray()} / {@code cellYArray()})
     * with no Unit-object indirection; the prefetcher streams both axes in
     * tandem under the sequential index walk. The {@code dense[]} ref is
     * still loaded per unit to populate the bucket, but the bucket payload
     * is itself the {@code Unit} reference — the SoA win lives on the
     * cell-read side.
     */
    public void rebuild(UnitRegistry registry) {
        for (int i = 0; i < buckets.length; i++) {
            ArrayList<Unit> b = buckets[i];
            if (b != null) {
                b.clear();
                pool.add(b);
                buckets[i] = null;
            }
        }
        Unit[] dense = registry.denseArray();
        int[] cellX = registry.cellXArray();
        int[] cellY = registry.cellYArray();
        int liveCount = registry.liveCount();
        for (int i = 0; i < liveCount; i++) {
            int bx = cellX[i] / BUCKET;
            int by = cellY[i] / BUCKET;
            if (bx < 0 || bx >= bucketsX || by < 0 || by >= bucketsY) continue;
            int idx = by * bucketsX + bx;
            ArrayList<Unit> bucket = buckets[idx];
            if (bucket == null) {
                bucket = pool.isEmpty()
                        ? new ArrayList<>(8)
                        : pool.remove(pool.size() - 1);
                buckets[idx] = bucket;
            }
            bucket.add(dense[i]);
        }
    }

    /**
     * Inserts {@code u} at its current cell. Used for incremental updates
     * between full {@link #rebuild} calls — primarily so test fixtures that
     * skip the tick loop still see units they just added. Dead units are
     * skipped (the index never holds them). A unit appearing twice in the
     * same bucket would double-count; callers must guarantee a unit isn't
     * already in the index when calling this. {@code addUnit} on
     * {@link com.dillon.starsectormarines.battle.sim.BattleSimulation} is the
     * only caller and is the sole add-path for live units, so the contract
     * holds in practice.
     */
    public void add(Unit u) {
        if (!u.isAlive()) return;
        int bx = u.getCellX() / BUCKET;
        int by = u.getCellY() / BUCKET;
        if (bx < 0 || bx >= bucketsX || by < 0 || by >= bucketsY) return;
        int idx = by * bucketsX + bx;
        ArrayList<Unit> bucket = buckets[idx];
        if (bucket == null) {
            bucket = pool.isEmpty()
                    ? new ArrayList<>(8)
                    : pool.remove(pool.size() - 1);
            buckets[idx] = bucket;
        }
        bucket.add(u);
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
    public void gather(int cx, int cy, float radius, ArrayList<Unit> out) {
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
                ArrayList<Unit> bucket = buckets[by * bucketsX + bx];
                if (bucket == null) continue;
                for (int i = 0, n = bucket.size(); i < n; i++) {
                    Unit u = bucket.get(i);
                    int dx = u.getCellX() - cx;
                    int dy = u.getCellY() - cy;
                    if (dx * dx + dy * dy <= r2) out.add(u);
                }
            }
        }
    }
}
