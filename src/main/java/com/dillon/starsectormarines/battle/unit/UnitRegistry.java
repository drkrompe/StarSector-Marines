package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.unit.Unit;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Arrays;

/**
 * Dense entity registry for {@link Unit}s — packed {@code Unit[]} keyed by
 * monotonic {@code long} entity ids, with swap-and-pop release so iteration
 * over {@code [0, liveCount())} is cache-coherent and dead entities never
 * appear in the dense view.
 *
 * <h2>Phase 1 (this revision)</h2>
 * <p>The registry lives in parallel with {@link UnitRosterService}'s legacy
 * {@code List<Unit>}: both are kept in sync on add, but death releases only
 * from the registry. The list keeps dead entries so existing post-death
 * consumers (turret demolition, drone crash sequencing, etc.) continue to
 * iterate them — those migrate to event-driven death emit in a later phase.
 * No hot iteration site reads from the registry yet; Phase 2 flips them.
 *
 * <h2>vs. campaign-tier IdRegistry / LongIntMap</h2>
 * <p>The campaign tier uses {@code LongIntMap}, which is structurally
 * append-only — dead rows tombstone in place because xstream save/load
 * requires stable indices. Battle is ephemeral and high-churn (~200 spawns
 * with ~50% dying per 5-min battle); tombstones would defeat the
 * cache-locality win this class exists for, so battle uses hard-delete
 * via swap-and-pop. The two tiers share the {@code long → int} lookup API
 * but diverge on deletion semantics.
 *
 * <h2>ID strategy</h2>
 * <p>Monotonic {@code long} sequence, no recycling, no generation bits.
 * A released id stays released forever; any stale reference resolves to
 * {@link #INVALID_INDEX} via {@link #indexOf(long)} and {@link #isLive(long)}
 * returns false. Same lazy-validity pattern existing code already uses via
 * {@link Unit#isAlive()}, just centralized at one registry seam. Generation
 * bits would only earn their cost under ID recycling, which doesn't happen
 * here — see {@code feedback_skip_generation_bits} memory.
 *
 * <p>{@code nextId} starts at 1 so {@code 0} is reserved as the "no entity"
 * sentinel — matches {@code LongIntMap}'s convention. {@link Unit#entityId}
 * is 0 before allocation.
 *
 * <h2>Thread safety</h2>
 * <p>Single-writer / multi-reader within a tick. {@link #allocate(Unit)}
 * and {@link #release(long)} run in serial sim phases (spawn flush and the
 * post-UPDATE_UNITS death drain); the parallel UPDATE_UNITS dispatch reads
 * {@link Unit#entityId} fields and may call {@link #isLive(long)} /
 * {@link #indexOf(long)} but never mutates. Same contract
 * {@link UnitRosterService}'s {@code List<Unit>} already enforces.
 */
public final class UnitRegistry {

    /** Sentinel returned by {@link #indexOf(long)} when the id is unknown. Matches the {@code -1} convention used by {@code LongIntMap.NOT_FOUND}. */
    public static final int INVALID_INDEX = -1;

    private static final int INITIAL_CAPACITY = 64;

    private Unit[] dense = new Unit[INITIAL_CAPACITY];
    /**
     * Per-unit current HP, keyed by dense index. Grown in lockstep with
     * {@link #dense}; swap-and-pop release moves the tail entry here too.
     * <b>Canonical storage</b> — the {@link Unit#getHp}/{@link Unit#setHp}
     * accessors route through this array after allocation. Pre-allocation
     * values live in the unit's transient {@code localHp} field; allocate
     * seeds the slot, release snapshots back to the field for any reader
     * that holds the released {@link Unit} reference after the registry
     * has dropped it (legacy {@code units} list path).
     */
    private float[] hp = new float[INITIAL_CAPACITY];
    /** Per-unit max HP, same lifecycle as {@link #hp}. */
    private float[] maxHp = new float[INITIAL_CAPACITY];
    /**
     * Per-unit logical cell X — the pathfinder's domain (integer cells). Same
     * grow/swap/snapshot lifecycle as {@link #hp}. Parallel-array layout (not
     * interleaved int[] cellXY stride-2) so any future single-axis sweep
     * (e.g. axis-aligned partition, sort by x) reads at full cache-line
     * efficiency without striding past the off-axis values. Paired access
     * patterns still prefetch both lines in tandem under sequential dense
     * iteration, so we're not paying for that flexibility on the hot rebuild.
     */
    private int[] cellX = new int[INITIAL_CAPACITY];
    /** Per-unit logical cell Y, paired with {@link #cellX}. */
    private int[] cellY = new int[INITIAL_CAPACITY];
    private int liveCount = 0;
    private long nextId = 1L;

    private final Long2IntOpenHashMap indexById = new Long2IntOpenHashMap();

    public UnitRegistry() {
        // Make missing-key lookups return INVALID_INDEX directly. The remove
        // path relies on this too: Long2IntOpenHashMap.remove returns the
        // default-return-value when the key isn't present, so duplicate
        // release calls are no-ops without the caller checking first.
        indexById.defaultReturnValue(INVALID_INDEX);
    }

    /**
     * Adds {@code u} to the next dense slot, assigns its
     * {@link Unit#entityId}, and returns the id. Grows the backing array
     * by doubling on overflow.
     *
     * <p>Rejects re-allocation: a {@link Unit} whose {@code entityId} is
     * non-zero already lives in the registry, and re-allocating would mint
     * a new id pointing at the same instance while the old id stays mapped
     * to a now-stale dense slot — a later release on the old id would null
     * a slot the new id still resolves to. The throw makes the double-add
     * a loud setup bug rather than a silent corruption.
     */
    public long allocate(Unit u) {
        if (u.entityId != 0L) {
            throw new IllegalStateException(
                    "Unit '" + u.id + "' already has entityId " + u.entityId + " — double allocate");
        }
        if (liveCount == dense.length) {
            int newCap = dense.length * 2;
            dense = Arrays.copyOf(dense, newCap);
            hp = Arrays.copyOf(hp, newCap);
            maxHp = Arrays.copyOf(maxHp, newCap);
            cellX = Arrays.copyOf(cellX, newCap);
            cellY = Arrays.copyOf(cellY, newCap);
        }
        long id = nextId++;
        u.entityId = id;
        dense[liveCount] = u;
        // Seed SoA from the unit's pre-allocation transient fields. After this
        // point hp[idx]/maxHp[idx]/cellX[idx]/cellY[idx] is canonical — the
        // unit's localHp/localMaxHp/localCellX/localCellY fields are stale
        // until release writes them back for post-release readers (corpse on
        // the legacy units list, isAlive() on a dead drone before its crash
        // sequence finishes).
        hp[liveCount] = u.localHp;
        maxHp[liveCount] = u.localMaxHp;
        cellX[liveCount] = u.localCellX;
        cellY[liveCount] = u.localCellY;
        u.denseIdx = liveCount;
        u.registry = this;
        indexById.put(id, liveCount);
        liveCount++;
        return id;
    }

    /**
     * Hard-removes the entity with id {@code id} via swap-and-pop. The tail
     * entity moves into the freed slot and its id→index mapping updates.
     * No-op if {@code id} is unknown — supports duplicate-release safety
     * even though current callers (the death cascade in
     * {@code DamageResolver.resolve}) emit at most one release per entity.
     *
     * <p>{@code id == 0L} is short-circuited explicitly: it's the
     * "never allocated" sentinel a setup-discarded {@link Unit} carries,
     * so routing it through the map (where it would also miss, since
     * {@code nextId} starts at 1) would still be a no-op — the explicit
     * guard makes the contract intentional rather than incidental.
     */
    public void release(long id) {
        if (id == 0L) return;
        int idx = indexById.remove(id);
        if (idx == INVALID_INDEX) return;
        int last = liveCount - 1;
        // Snapshot HP + cell back onto the released unit so post-release
        // readers (corpses still in the legacy units list; isAlive() chained
        // via getHp() in flyout / drone-crash code; the drone-crash sprite
        // that still needs to know where to draw the corpse) see the
        // moment-of-death values rather than stale defaults.
        Unit released = dense[idx];
        released.localHp = hp[idx];
        released.localMaxHp = maxHp[idx];
        released.localCellX = cellX[idx];
        released.localCellY = cellY[idx];
        released.denseIdx = -1;
        released.registry = null;
        if (idx != last) {
            Unit tail = dense[last];
            dense[idx] = tail;
            hp[idx] = hp[last];
            maxHp[idx] = maxHp[last];
            cellX[idx] = cellX[last];
            cellY[idx] = cellY[last];
            tail.denseIdx = idx;
            indexById.put(tail.entityId, idx);
        }
        dense[last] = null;
        liveCount--;
    }

    /** Returns the current dense index for {@code id}, or {@link #INVALID_INDEX} if released or never allocated. */
    public int indexOf(long id) {
        return indexById.get(id);
    }

    /** True iff {@code id} is currently in the registry (allocated and not yet released). */
    public boolean isLive(long id) {
        return indexById.containsKey(id);
    }

    /**
     * Returns the {@link Unit} for {@code id}, or {@code null} if the id is
     * unknown (never allocated) or released. The lazy-validity replacement
     * for the old {@code target != null && target.isAlive()} idiom — a
     * dangling {@code long} resolves cleanly to null without the holder
     * needing to know whether the target was killed or just never existed.
     *
     * <p>{@code id == 0L} (the "no entity" sentinel) returns null without
     * a map probe — that path runs every tick from every behavior that
     * checks "do I have a target," so the fast-path matters.
     */
    public Unit getOrNull(long id) {
        if (id == 0L) return null;
        int idx = indexById.get(id);
        if (idx == INVALID_INDEX) return null;
        return dense[idx];
    }

    /** Returns the unit at dense slot {@code idx}. Callers iterate over {@code [0, liveCount())}; no bounds check. */
    public Unit get(int idx) {
        return dense[idx];
    }

    /**
     * Direct array access for the SoA hp slot. Used by {@link Unit#getHp}
     * (the OO-shape accessor every existing call site goes through) and by
     * any future hot bulk loop that iterates over {@code [0, liveCount())}
     * without a {@link Unit} dereference.
     */
    public float getHp(int idx) { return hp[idx]; }
    public void setHp(int idx, float v) { hp[idx] = v; }
    public float getMaxHp(int idx) { return maxHp[idx]; }
    public void setMaxHp(int idx, float v) { maxHp[idx] = v; }

    /**
     * Raw {@code float[]} hp view for bulk iteration over
     * {@code [0, liveCount())}. Same caveat as {@link #denseArray()} —
     * the array reference may be replaced by {@link #allocate(Unit)} on
     * growth, so don't cache across allocations.
     */
    public float[] hpArray() { return hp; }
    public float[] maxHpArray() { return maxHp; }

    /**
     * Direct array access for the SoA cell-position slots. Sequential dense
     * iteration over {@code [0, liveCount())} streams cellX and cellY in
     * tandem under prefetch; paired index reads via the OO accessor
     * ({@link Unit#getCellX} / {@link Unit#getCellY}) route here through
     * {@code denseIdx}.
     */
    public int getCellX(int idx) { return cellX[idx]; }
    public int getCellY(int idx) { return cellY[idx]; }
    public void setCellPos(int idx, int x, int y) {
        cellX[idx] = x;
        cellY[idx] = y;
    }
    public int[] cellXArray() { return cellX; }
    public int[] cellYArray() { return cellY; }

    public int liveCount() {
        return liveCount;
    }

    /**
     * Direct access to the backing array. Indices {@code [0, liveCount())}
     * are live; slots beyond that are null. Exposed so hot loops can avoid
     * the per-iteration accessor hop — same alias-field rationale as
     * {@link UnitRosterService}'s units-list field on
     * {@code BattleSimulation}.
     *
     * <p><b>Do not cache across allocations.</b> The backing array is
     * replaced by {@link #allocate(Unit)} when {@link #liveCount()} hits
     * {@code dense.length}; a cached reference becomes a stale view of an
     * abandoned array. Safe to alias for the duration of a single tick
     * phase that doesn't allocate (the parallel UPDATE_UNITS dispatch is
     * the intended Phase 2 consumer — spawns are queued and flushed in a
     * separate serial phase, so the array is stable across the dispatch).
     */
    public Unit[] denseArray() {
        return dense;
    }
}
