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
    /**
     * Per-unit max HP. <b>Seed-only</b> lifecycle (unlike {@link #hp}):
     * {@link #allocate} seeds it from {@link Unit#seedMaxHp} and it is canonical
     * thereafter, but {@link #release} does NOT snapshot it back — no
     * post-release reader exists.
     */
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
    /** Per-unit primary weapon cooldown (sim-seconds until next fire). Same lifecycle as {@link #hp}. */
    private float[] cooldownTimer = new float[INITIAL_CAPACITY];
    /** Per-unit movement lerp factor [0,1] toward the next path cell. Same lifecycle as {@link #hp}. */
    private float[] moveProgress = new float[INITIAL_CAPACITY];
    /** Per-unit base attack damage. Seed-only lifecycle (seeded from {@link Unit#seedAttackDamage}, not snapshotted on release) — see {@link #maxHp}. */
    private float[] attackDamage = new float[INITIAL_CAPACITY];
    /** Per-unit base attack range in cells. Seed-only lifecycle (seeded from {@link Unit#seedAttackRange}) — see {@link #maxHp}. */
    private float[] attackRange = new float[INITIAL_CAPACITY];
    /** Per-unit base accuracy [0,1]. Seed-only lifecycle (seeded from {@link Unit#seedAccuracy}) — see {@link #maxHp}. */
    private float[] accuracy = new float[INITIAL_CAPACITY];
    /** Per-unit secondary-weapon cooldown (sim-seconds). Same lifecycle as {@link #hp}. */
    private float[] secondaryCooldownTimer = new float[INITIAL_CAPACITY];
    /** Per-unit sim-seconds remaining in the secondary aim-then-fire window. Same lifecycle as {@link #hp}. */
    private float[] secondaryActionTimer = new float[INITIAL_CAPACITY];
    /** Per-unit entity id locked at secondary aim start (0L = none). Same lifecycle as {@link #hp}. */
    private long[] secondaryAimTargetId = new long[INITIAL_CAPACITY];
    /** Per-unit burst rounds queued after the AI's initial primary shot. Decremented one-per-spacing in {@code InfantryWeapons.tick}. Same lifecycle as {@link #hp}. */
    private int[] burstRemaining = new int[INITIAL_CAPACITY];
    /** Per-unit sim-seconds until the next queued burst round fires. Same lifecycle as {@link #hp}. */
    private float[] burstTimer = new float[INITIAL_CAPACITY];
    /** Per-unit entity id captured when the burst was queued (0L = idle). Same lifecycle as {@link #hp}. */
    private long[] burstTargetId = new long[INITIAL_CAPACITY];
    /** Per-unit current-target entity id (0L = no target). Same lifecycle as {@link #hp}. */
    private long[] targetId = new long[INITIAL_CAPACITY];
    /** Per-unit sim-seconds until the unit may next micro-reposition between shots. Decremented in {@code InfantryUnitPrep.tickCooldowns}. Same lifecycle as {@link #hp}. */
    private float[] repositionCooldown = new float[INITIAL_CAPACITY];
    /** Per-unit sim-seconds remaining in break-contact fall-back state (>0 = falling back toward {@link #fallbackCellX}/{@link #fallbackCellY}). Same lifecycle as {@link #hp}. */
    private float[] fallbackTimer = new float[INITIAL_CAPACITY];
    /** Per-unit cached fall-back destination cell X (-1 = none). Paired with {@link #fallbackCellY}; same int-pair layout as {@link #cellX}. Same lifecycle as {@link #hp}. */
    private int[] fallbackCellX = new int[INITIAL_CAPACITY];
    /** Per-unit cached fall-back destination cell Y, paired with {@link #fallbackCellX}. */
    private int[] fallbackCellY = new int[INITIAL_CAPACITY];
    /** Per-unit FLEE-role idle pause between wander legs (sim-seconds). Same lifecycle as {@link #hp}. */
    private float[] wanderDwellTimer = new float[INITIAL_CAPACITY];
    private int liveCount = 0;
    private long nextId = 1L;

    private final Long2IntOpenHashMap indexById = new Long2IntOpenHashMap();

    /**
     * Smooth render position, decomposed out of the dense table into a
     * standalone entity-id-keyed service so it survives release (the corpse
     * draws its frozen death pose where it fell). The registry owns the
     * instance and seeds it in {@link #allocate}; {@link Unit#getRenderX()} /
     * {@link Unit#setRenderPos} route through the per-unit
     * {@link Unit#renderPositions} reference, which is <b>not</b> nulled on
     * release. See {@link RenderPositionService} for the entity-id-keyed
     * rationale.
     */
    private final RenderPositionService renderPositions = new RenderPositionService();

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
            cooldownTimer = Arrays.copyOf(cooldownTimer, newCap);
            moveProgress = Arrays.copyOf(moveProgress, newCap);
            attackDamage = Arrays.copyOf(attackDamage, newCap);
            attackRange = Arrays.copyOf(attackRange, newCap);
            accuracy = Arrays.copyOf(accuracy, newCap);
            secondaryCooldownTimer = Arrays.copyOf(secondaryCooldownTimer, newCap);
            secondaryActionTimer = Arrays.copyOf(secondaryActionTimer, newCap);
            secondaryAimTargetId = Arrays.copyOf(secondaryAimTargetId, newCap);
            burstRemaining = Arrays.copyOf(burstRemaining, newCap);
            burstTimer = Arrays.copyOf(burstTimer, newCap);
            burstTargetId = Arrays.copyOf(burstTargetId, newCap);
            targetId = Arrays.copyOf(targetId, newCap);
            repositionCooldown = Arrays.copyOf(repositionCooldown, newCap);
            fallbackTimer = Arrays.copyOf(fallbackTimer, newCap);
            fallbackCellX = Arrays.copyOf(fallbackCellX, newCap);
            fallbackCellY = Arrays.copyOf(fallbackCellY, newCap);
            wanderDwellTimer = Arrays.copyOf(wanderDwellTimer, newCap);
        }
        long id = nextId++;
        u.entityId = id;
        dense[liveCount] = u;
        // Seed the seed-bearing columns from the unit's pre-allocation transient
        // fields. After this point these are canonical. The corpse-read subset
        // (hp/cell) keeps its local* twin so release can snapshot the
        // moment-of-death value back for post-release readers (isAlive() on a
        // dead drone before its crash sequence finishes); the Group-S stats
        // (maxHp + attack stats) seed from write-only seed* fields and are never
        // snapshotted back — they have no post-release reader.
        hp[liveCount] = u.localHp;
        maxHp[liveCount] = u.seedMaxHp;
        cellX[liveCount] = u.localCellX;
        cellY[liveCount] = u.localCellY;
        attackDamage[liveCount] = u.seedAttackDamage;
        attackRange[liveCount] = u.seedAttackRange;
        accuracy[liveCount] = u.seedAccuracy;
        // Reset the mid-combat-only columns to their defaults. These have no
        // pre-allocation seed (a fresh unit starts at rest) and no post-release
        // reader, so they carry no local* twin on Unit; the explicit reset
        // clears any stale value left in a dense slot reused after a
        // swap-and-pop release.
        cooldownTimer[liveCount] = 0f;
        moveProgress[liveCount] = 0f;
        secondaryCooldownTimer[liveCount] = 0f;
        secondaryActionTimer[liveCount] = 0f;
        secondaryAimTargetId[liveCount] = 0L;
        burstRemaining[liveCount] = 0;
        burstTimer[liveCount] = 0f;
        burstTargetId[liveCount] = 0L;
        targetId[liveCount] = 0L;
        repositionCooldown[liveCount] = 0f;
        fallbackTimer[liveCount] = 0f;
        fallbackCellX[liveCount] = -1;
        fallbackCellY[liveCount] = -1;
        wanderDwellTimer[liveCount] = 0f;
        u.denseIdx = liveCount;
        u.registry = this;
        // Seed + wire the decomposed render-position service. Unlike the dense
        // columns above, this reference is NOT nulled on release — the entry
        // survives so a released corpse still resolves its death-pose location.
        renderPositions.set(id, u.localRenderX, u.localRenderY);
        u.renderPositions = renderPositions;
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
        // readers (isAlive() chained via getHp() in drone-crash code; the
        // moment-of-death cell read by the crash sprite / equipment-drop emit)
        // see the moment-of-death values rather than stale defaults. The
        // Group-S stats (maxHp + attack stats) are deliberately NOT snapshotted —
        // they have no post-release reader and carry no local* twin. Render
        // position is NOT snapshotted here either — it lives in the decomposed
        // RenderPositionService keyed by entityId, which is not cleared on
        // release, so the corpse's death-pose location survives directly.
        Unit released = dense[idx];
        released.localHp = hp[idx];
        released.localCellX = cellX[idx];
        released.localCellY = cellY[idx];
        released.denseIdx = -1;
        released.registry = null;
        // Deliberately keep released.renderPositions wired — the service entry
        // survives so getRenderX()/getRenderY() still resolve for the corpse.
        if (idx != last) {
            Unit tail = dense[last];
            dense[idx] = tail;
            hp[idx] = hp[last];
            maxHp[idx] = maxHp[last];
            cellX[idx] = cellX[last];
            cellY[idx] = cellY[last];
            cooldownTimer[idx] = cooldownTimer[last];
            moveProgress[idx] = moveProgress[last];
            attackDamage[idx] = attackDamage[last];
            attackRange[idx] = attackRange[last];
            accuracy[idx] = accuracy[last];
            secondaryCooldownTimer[idx] = secondaryCooldownTimer[last];
            secondaryActionTimer[idx] = secondaryActionTimer[last];
            secondaryAimTargetId[idx] = secondaryAimTargetId[last];
            burstRemaining[idx] = burstRemaining[last];
            burstTimer[idx] = burstTimer[last];
            burstTargetId[idx] = burstTargetId[last];
            targetId[idx] = targetId[last];
            repositionCooldown[idx] = repositionCooldown[last];
            fallbackTimer[idx] = fallbackTimer[last];
            fallbackCellX[idx] = fallbackCellX[last];
            fallbackCellY[idx] = fallbackCellY[last];
            wanderDwellTimer[idx] = wanderDwellTimer[last];
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

    public float getCooldownTimer(int idx) { return cooldownTimer[idx]; }
    public void setCooldownTimer(int idx, float v) { cooldownTimer[idx] = v; }
    public float[] cooldownTimerArray() { return cooldownTimer; }

    public float getMoveProgress(int idx) { return moveProgress[idx]; }
    public void setMoveProgress(int idx, float v) { moveProgress[idx] = v; }
    public float[] moveProgressArray() { return moveProgress; }

    /**
     * The decomposed render-position service this registry seeds + wires on
     * {@link #allocate}. Render position is keyed by {@link Unit#entityId} and
     * survives release (see {@link RenderPositionService}); the registry no
     * longer holds dense {@code renderX/renderY} columns.
     */
    public RenderPositionService getRenderPositions() { return renderPositions; }

    public float getAttackDamage(int idx) { return attackDamage[idx]; }
    public void setAttackDamage(int idx, float v) { attackDamage[idx] = v; }
    public float[] attackDamageArray() { return attackDamage; }

    public float getAttackRange(int idx) { return attackRange[idx]; }
    public void setAttackRange(int idx, float v) { attackRange[idx] = v; }
    public float[] attackRangeArray() { return attackRange; }

    public float getAccuracy(int idx) { return accuracy[idx]; }
    public void setAccuracy(int idx, float v) { accuracy[idx] = v; }
    public float[] accuracyArray() { return accuracy; }

    public float getSecondaryCooldownTimer(int idx) { return secondaryCooldownTimer[idx]; }
    public void setSecondaryCooldownTimer(int idx, float v) { secondaryCooldownTimer[idx] = v; }
    public float[] secondaryCooldownTimerArray() { return secondaryCooldownTimer; }

    public float getSecondaryActionTimer(int idx) { return secondaryActionTimer[idx]; }
    public void setSecondaryActionTimer(int idx, float v) { secondaryActionTimer[idx] = v; }
    public float[] secondaryActionTimerArray() { return secondaryActionTimer; }

    public long getSecondaryAimTargetId(int idx) { return secondaryAimTargetId[idx]; }
    public void setSecondaryAimTargetId(int idx, long v) { secondaryAimTargetId[idx] = v; }
    public long[] secondaryAimTargetIdArray() { return secondaryAimTargetId; }

    public int getBurstRemaining(int idx) { return burstRemaining[idx]; }
    public void setBurstRemaining(int idx, int v) { burstRemaining[idx] = v; }
    public int[] burstRemainingArray() { return burstRemaining; }

    public float getBurstTimer(int idx) { return burstTimer[idx]; }
    public void setBurstTimer(int idx, float v) { burstTimer[idx] = v; }
    public float[] burstTimerArray() { return burstTimer; }

    public long getBurstTargetId(int idx) { return burstTargetId[idx]; }
    public void setBurstTargetId(int idx, long v) { burstTargetId[idx] = v; }
    public long[] burstTargetIdArray() { return burstTargetId; }

    public long getTargetId(int idx) { return targetId[idx]; }
    public void setTargetId(int idx, long v) { targetId[idx] = v; }
    public long[] targetIdArray() { return targetId; }

    public float getRepositionCooldown(int idx) { return repositionCooldown[idx]; }
    public void setRepositionCooldown(int idx, float v) { repositionCooldown[idx] = v; }
    public float[] repositionCooldownArray() { return repositionCooldown; }

    public float getFallbackTimer(int idx) { return fallbackTimer[idx]; }
    public void setFallbackTimer(int idx, float v) { fallbackTimer[idx] = v; }
    public float[] fallbackTimerArray() { return fallbackTimer; }

    public int getFallbackCellX(int idx) { return fallbackCellX[idx]; }
    public int getFallbackCellY(int idx) { return fallbackCellY[idx]; }
    public void setFallbackCell(int idx, int x, int y) {
        fallbackCellX[idx] = x;
        fallbackCellY[idx] = y;
    }
    public int[] fallbackCellXArray() { return fallbackCellX; }
    public int[] fallbackCellYArray() { return fallbackCellY; }

    public float getWanderDwellTimer(int idx) { return wanderDwellTimer[idx]; }
    public void setWanderDwellTimer(int idx, float v) { wanderDwellTimer[idx] = v; }
    public float[] wanderDwellTimerArray() { return wanderDwellTimer; }

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
