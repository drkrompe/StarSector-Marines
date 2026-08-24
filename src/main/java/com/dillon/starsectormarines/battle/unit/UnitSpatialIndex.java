package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.LongPredicate;

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
 * <p><b>Threading.</b> {@link #rebuild} and {@link #add} run single-threaded
 * (the per-tick setup phase). {@link #gather} and {@link #gatherAlongSegment}
 * are read-only against the post-rebuild bucket state and safe to call
 * concurrently — {@link #gatherAlongSegment} is already called this way, from
 * {@link com.dillon.starsectormarines.battle.combat.BallisticResolver#resolve}
 * on the parallel UPDATE_UNITS dispatch (turret/drone/infantry fire).
 * Both methods mutate nothing on the instance; each caller must still pass
 * its own output {@link LongBucket} — a shared buffer across concurrent
 * callers would race on the buffer itself, not the index.
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
        byte[] factionOrdinals = new byte[8];
        int size;

        void add(long id, float x, float y, byte factionOrdinal) {
            if (size == ids.length) {
                int cap = size << 1;
                ids = Arrays.copyOf(ids, cap);
                posX = Arrays.copyOf(posX, cap);
                posY = Arrays.copyOf(posY, cap);
                factionOrdinals = Arrays.copyOf(factionOrdinals, cap);
            }
            ids[size] = id;
            posX[size] = x;
            posY[size] = y;
            factionOrdinals[size] = factionOrdinal;
            size++;
        }

        /**
         * Removes {@code id} without disturbing the relative order of the
         * remaining snapshot entries. Releases are cold, serial lifecycle
         * work; paying one compact array shift here removes liveness-map
         * probes from every later candidate walk.
         */
        boolean removeStable(long id) {
            int index = 0;
            while (index < size && ids[index] != id) index++;
            if (index == size) return false;
            int moved = size - index - 1;
            if (moved > 0) {
                System.arraycopy(ids, index + 1, ids, index, moved);
                System.arraycopy(posX, index + 1, posX, index, moved);
                System.arraycopy(posY, index + 1, posY, index, moved);
                System.arraycopy(factionOrdinals, index + 1,
                        factionOrdinals, index, moved);
            }
            size--;
            return true;
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
     * Grow-and-stay rebuild scratch keyed by the roster's dense index. The
     * gather pass streams POSITION/IDENTITY columns in archetype-row order;
     * the insertion pass then walks roster-dense order so bucket entry order
     * remains exactly the same as the original by-id rebuild.
     */
    private long[] scratchIds = new long[64];
    private float[] scratchX = new float[64];
    private float[] scratchY = new float[64];
    private byte[] scratchFactionOrdinals = new byte[64];
    /**
     * The registry the buckets were populated from, stashed by {@link #rebuild}
     * / {@link #add} for faction-count short-circuiting. The registry instance
     * is stable for the battle. Released ids are physically removed from their
     * snapshot bucket at the serial roster-release seam, so candidate walks do
     * not need per-entry registry or entity-world liveness probes.
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
     * range directly for insertion — released slots are excluded by the
     * roster, so no per-call {@code isAlive()} branch in the inner loop.
     * Before insertion, one column walk gathers TRUE positions and immutable
     * factions into grow-and-stay scratch keyed by roster dense index. This
     * avoids three entity-location probes per unit while retaining roster-dense
     * bucket order. Positions are binned by their floored cell, then stored
     * (unfloored) alongside the id so {@link #gather} never reads them back.
     */
    public void rebuild(UnitRosterService roster) {
        this.roster = roster;
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
        ensureScratchCapacity(liveCount);
        Arrays.fill(scratchIds, 0, liveCount, 0L);

        EntityWorld world = roster.entityWorld();
        BattleComponents components = roster.components();
        for (ArchetypeTable table : world.matched(components.gridOccupants)) {
            // gridOccupants is intentionally the broad POSITION-minus-CORPSE
            // query shared with occupancy. Ignore any future position-only,
            // non-roster family before asking for ground-unit identity columns.
            if (!table.has(components.IDENTITY)) continue;
            float[] posX = table.floats(components.POSITION,
                    BattleComponents.POSITION_X).array();
            float[] posY = table.floats(components.POSITION,
                    BattleComponents.POSITION_Y).array();
            Object[] factions = table.objects(components.IDENTITY,
                    BattleComponents.IDENTITY_FACTION).array();
            for (int row = 0, rows = table.rowCount(); row < rows; row++) {
                long id = table.entityAt(row);
                int denseIndex = roster.indexOf(id);
                if (denseIndex == UnitRosterService.INVALID_INDEX) continue;
                scratchIds[denseIndex] = id;
                scratchX[denseIndex] = posX[row];
                scratchY[denseIndex] = posY[row];
                scratchFactionOrdinals[denseIndex] =
                        (byte) ((Faction) factions[row]).ordinal();
            }
        }

        for (int i = 0; i < liveCount; i++) {
            long id = dense[i];
            if (scratchIds[i] != id) {
                throw new IllegalStateException(
                        "live unit missing from gridOccupants query: " + id);
            }
            float x = scratchX[i];
            float y = scratchY[i];
            Bucket bucket = bucketAt((int) Math.floor(x), (int) Math.floor(y));
            if (bucket != null) {
                bucket.add(id, x, y, scratchFactionOrdinals[i]);
            }
        }
    }

    private void ensureScratchCapacity(int need) {
        if (need <= scratchIds.length) return;
        int capacity = Math.max(need, scratchIds.length << 1);
        scratchIds = Arrays.copyOf(scratchIds, capacity);
        scratchX = Arrays.copyOf(scratchX, capacity);
        scratchY = Arrays.copyOf(scratchY, capacity);
        scratchFactionOrdinals = Arrays.copyOf(scratchFactionOrdinals, capacity);
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
        if (bucket != null) {
            bucket.add(id, x, y,
                    (byte) roster.identity().faction(id).ordinal());
        }
    }

    /**
     * Removes a released id from the current snapshot. A unit occupies at most
     * one bucket, so stop at the first hit. Off-grid units have no entry and are
     * a legitimate no-op. Called only from the roster's serial release seam.
     */
    void remove(long id) {
        for (Bucket bucket : buckets) {
            if (bucket != null && bucket.removeStable(id)) return;
        }
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
        gather(cx, cy, radius, -1, out);
    }

    /**
     * Faction-filtered form of {@link #gather}. Faction is denormalized into
     * each spatial bucket at rebuild, so non-matching candidates are rejected
     * before any by-id liveness probe.
     */
    public void gatherFaction(float cx, float cy, float radius,
                              Faction faction, LongBucket out) {
        gather(cx, cy, radius, faction.ordinal(), out);
    }

    private void gather(float cx, float cy, float radius,
                        int factionOrdinal, LongBucket out) {
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
                byte[] factions = bucket.factionOrdinals;
                for (int i = 0, n = bucket.size; i < n; i++) {
                    if (factionOrdinal >= 0 && factions[i] != factionOrdinal) continue;
                    float dx = bpx[i] - cx;
                    float dy = bpy[i] - cy;
                    if (dx * dx + dy * dy <= r2) out.add(ids[i]);
                }
            }
        }
    }

    /**
     * Nearest live unit of {@code faction} to the query point, or {@code 0L}
     * when that faction has no indexed unit. Bucket rings expand from the
     * query and stop once the best squared distance is inside the nearest
     * possible unvisited bucket boundary. Equal-distance ties resolve by id.
     */
    public long nearestFaction(float cx, float cy, Faction faction) {
        return nearestFaction(cx, cy, faction, null);
    }

    /**
     * Filtered nearest-faction query. The primitive predicate is evaluated
     * only for live, faction-matching bucket entries, allowing callers with a
     * narrow eligibility rule to keep expanding spatially instead of falling
     * back to a full faction scan.
     */
    public long nearestFaction(float cx, float cy, Faction faction,
                               LongPredicate eligibility) {
        if (roster.factionLiveCount(faction) == 0) return 0L;
        int centerBx = Math.max(0, Math.min(bucketsX - 1,
                Math.floorDiv((int) Math.floor(cx), BUCKET)));
        int centerBy = Math.max(0, Math.min(bucketsY - 1,
                Math.floorDiv((int) Math.floor(cy), BUCKET)));
        int maxRing = Math.max(Math.max(centerBx, bucketsX - 1 - centerBx),
                Math.max(centerBy, bucketsY - 1 - centerBy));
        int factionOrdinal = faction.ordinal();
        long best = 0L;
        float bestDistanceSquared = Float.MAX_VALUE;

        for (int ring = 0; ring <= maxRing; ring++) {
            int x0 = Math.max(0, centerBx - ring);
            int x1 = Math.min(bucketsX - 1, centerBx + ring);
            int y0 = Math.max(0, centerBy - ring);
            int y1 = Math.min(bucketsY - 1, centerBy + ring);
            for (int by = y0; by <= y1; by++) {
                for (int bx = x0; bx <= x1; bx++) {
                    if (Math.max(Math.abs(bx - centerBx),
                            Math.abs(by - centerBy)) != ring) continue;
                    Bucket bucket = buckets[by * bucketsX + bx];
                    if (bucket == null) continue;
                    for (int i = 0, n = bucket.size; i < n; i++) {
                        if (bucket.factionOrdinals[i] != factionOrdinal) continue;
                        long id = bucket.ids[i];
                        if (eligibility != null && !eligibility.test(id)) continue;
                        float dx = bucket.posX[i] - cx;
                        float dy = bucket.posY[i] - cy;
                        float distanceSquared = dx * dx + dy * dy;
                        if (distanceSquared < bestDistanceSquared
                                || (distanceSquared == bestDistanceSquared
                                && (best == 0L || id < best))) {
                            best = id;
                            bestDistanceSquared = distanceSquared;
                        }
                    }
                }
            }
            if (best != 0L && bestDistanceSquared < nearestOutsideDistanceSquared(
                    cx, cy, centerBx, centerBy, ring)) break;
        }
        return best;
    }

    private float nearestOutsideDistanceSquared(float cx, float cy,
                                                 int centerBx, int centerBy,
                                                 int ring) {
        float nearest = Float.MAX_VALUE;
        int left = centerBx - ring;
        int right = centerBx + ring;
        int top = centerBy - ring;
        int bottom = centerBy + ring;
        if (left > 0) nearest = Math.min(nearest, cx - left * BUCKET);
        if (right < bucketsX - 1) nearest = Math.min(nearest,
                (right + 1) * BUCKET - cx);
        if (top > 0) nearest = Math.min(nearest, cy - top * BUCKET);
        if (bottom < bucketsY - 1) nearest = Math.min(nearest,
                (bottom + 1) * BUCKET - cy);
        return nearest * nearest;
    }

    /**
     * Appends every alive unit whose snapshot position lies within
     * {@code margin} cells (point-to-segment Euclidean distance) of segment
     * ({@code x0}, {@code y0})–({@code x1}, {@code y1}) into {@code out}.
     * Same output-buffer contract as {@link #gather} (clears {@code out}
     * first; no-op no-alloc past buffer growth).
     *
     * <p>A ballistic ray is long and narrow, so scanning its full bounding
     * box (as {@link #gather} does for a circle) would touch every bucket
     * between the endpoints even when the ray only grazes a thin sliver of
     * each. Instead this walks the segment in bucket-sized steps and, at
     * each sampled point, visits that point's bucket plus every neighbor
     * within {@code margin}. The +1-bucket pad on the neighbor search
     * (beyond {@code ceil(margin / BUCKET)}) absorbs the along-segment gap
     * between samples (up to {@code BUCKET} cells apart) so no unit within
     * {@code margin} of the true segment is skipped.
     *
     * <p><b>Dedupe without shared state.</b> The sample point advances
     * monotonically along the segment, so each sample's bucket-neighborhood
     * rectangle moves monotonically in bucket space, independently per axis.
     * That means a bucket appearing in sample {@code i}'s rectangle that also
     * appeared in any <em>earlier</em> sample's rectangle must appear in
     * sample {@code i-1}'s rectangle too — the visited set never "skips
     * backward" between one sample and the next. So dedup only needs the
     * immediately preceding sample's rectangle bounds, kept in locals; no
     * per-instance visited-stamp array, no shared mutable state. This makes
     * the method safe for concurrent callers (each has its own locals and
     * output buffer) — see the class Javadoc's Threading note.
     */
    public void gatherAlongSegment(float x0, float y0, float x1, float y1, float margin, LongBucket out) {
        out.clear();
        if (margin <= 0f) return;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        int marginBuckets = (int) Math.ceil(margin / BUCKET) + 1;
        int steps = Math.max(1, (int) Math.ceil(len / BUCKET));
        // Previous sample's visited rectangle. Starts empty (bx1 < bx0) so
        // the first sample's whole rectangle is visited.
        int prevBx0 = 0, prevBx1 = -1, prevBy0 = 0, prevBy1 = -1;
        for (int s = 0; s <= steps; s++) {
            float t = (float) s / steps;
            float px = x0 + dx * t;
            float py = y0 + dy * t;
            int cbx = Math.floorDiv((int) Math.floor(px), BUCKET);
            int cby = Math.floorDiv((int) Math.floor(py), BUCKET);
            int bx0 = Math.max(0, cbx - marginBuckets);
            int bx1 = Math.min(bucketsX - 1, cbx + marginBuckets);
            int by0 = Math.max(0, cby - marginBuckets);
            int by1 = Math.min(bucketsY - 1, cby + marginBuckets);
            for (int by = by0; by <= by1; by++) {
                for (int bx = bx0; bx <= bx1; bx++) {
                    // Already covered by the previous sample's rectangle ⟹
                    // already covered by every earlier sample too (see the
                    // monotonicity invariant above) — skip re-testing it.
                    if (bx >= prevBx0 && bx <= prevBx1 && by >= prevBy0 && by <= prevBy1) continue;
                    gatherBucketAlongSegment(buckets[by * bucketsX + bx], x0, y0, dx, dy, len, margin, out);
                }
            }
            prevBx0 = bx0; prevBx1 = bx1; prevBy0 = by0; prevBy1 = by1;
        }
    }

    /**
     * Point-to-segment distance filter for one bucket's contents, shared by
     * every bucket {@link #gatherAlongSegment} visits. The segment is given
     * as origin ({@code x0}, {@code y0}) + direction ({@code dx}, {@code dy}),
     * {@code len} = the direction vector's magnitude.
     */
    private void gatherBucketAlongSegment(Bucket bucket, float x0, float y0, float dx, float dy,
                                           float len, float margin, LongBucket out) {
        if (bucket == null) return;
        long[] ids = bucket.ids;
        float[] bpx = bucket.posX;
        float[] bpy = bucket.posY;
        float len2 = len * len;
        float m2 = margin * margin;
        for (int i = 0, n = bucket.size; i < n; i++) {
            float px = bpx[i] - x0;
            float py = bpy[i] - y0;
            float t = len2 > 0f ? (px * dx + py * dy) / len2 : 0f;
            t = t < 0f ? 0f : (t > 1f ? 1f : t);
            float ex = px - dx * t;
            float ey = py - dy * t;
            if (ex * ex + ey * ey <= m2) out.add(ids[i]);
        }
    }
}
