package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Arrays;

/**
 * Dense entity registry for {@link Entity}s — packed {@code Entity[]} keyed by
 * monotonic {@code long} entity ids, with swap-and-pop release so iteration
 * over {@code [0, liveCount())} is cache-coherent and dead entities never
 * appear in the dense view.
 *
 * <h2>Phase 1 (this revision)</h2>
 * <p>The registry lives in parallel with {@link UnitRosterService}'s legacy
 * {@code List<Entity>}: both are kept in sync on add, but death releases only
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
 * returns false. Same lazy-validity pattern, centralized at the registry seam
 * ({@link #isAliveById(long)} / {@link #getOrNull(long)}). Generation
 * bits would only earn their cost under ID recycling, which doesn't happen
 * here — see {@code feedback_skip_generation_bits} memory.
 *
 * <p>{@code nextId} starts at 1 so {@code 0} is reserved as the "no entity"
 * sentinel — matches {@code LongIntMap}'s convention. {@link Entity#entityId}
 * is 0 before allocation.
 *
 * <h2>Thread safety</h2>
 * <p>Single-writer / multi-reader within a tick. {@link #allocate(Entity)}
 * and {@link #release(long)} run in serial sim phases (spawn flush and the
 * post-UPDATE_UNITS death drain); the parallel UPDATE_UNITS dispatch reads
 * {@link Entity#entityId} fields and may call {@link #isLive(long)} /
 * {@link #indexOf(long)} but never mutates. Same contract
 * {@link UnitRosterService}'s {@code List<Entity>} already enforces.
 */
public final class UnitRegistry {

    /** Sentinel returned by {@link #indexOf(long)} when the id is unknown. Matches the {@code -1} convention used by {@code LongIntMap.NOT_FOUND}. */
    public static final int INVALID_INDEX = -1;

    private static final int INITIAL_CAPACITY = 64;

    private Entity[] dense = new Entity[INITIAL_CAPACITY];
    /** Per-unit primary weapon cooldown (sim-seconds until next fire). Mid-combat-only lifecycle: no pre-allocation seed, reset to default on slot reuse in {@link #allocate}, no post-release reader. */
    private float[] cooldownTimer = new float[INITIAL_CAPACITY];
    /** Per-unit movement lerp factor [0,1] toward the next path cell. Same lifecycle as {@link #cooldownTimer}. */
    private float[] moveProgress = new float[INITIAL_CAPACITY];
    /** Per-unit base attack damage. Seed-only lifecycle (seeded from {@link Entity#seedAttackDamage}, not snapshotted on release) — see {@link #cellX}. */
    private float[] attackDamage = new float[INITIAL_CAPACITY];
    /** Per-unit base attack range in cells. Seed-only lifecycle (seeded from {@link Entity#seedAttackRange}) — see {@link #cellX}. */
    private float[] attackRange = new float[INITIAL_CAPACITY];
    /** Per-unit base accuracy [0,1]. Seed-only lifecycle (seeded from {@link Entity#seedAccuracy}) — see {@link #cellX}. */
    private float[] accuracy = new float[INITIAL_CAPACITY];
    /** Per-unit secondary-weapon cooldown (sim-seconds). Same lifecycle as {@link #cooldownTimer}. */
    private float[] secondaryCooldownTimer = new float[INITIAL_CAPACITY];
    /** Per-unit sim-seconds remaining in the secondary aim-then-fire window. Same lifecycle as {@link #cooldownTimer}. */
    private float[] secondaryActionTimer = new float[INITIAL_CAPACITY];
    /** Per-unit entity id locked at secondary aim start (0L = none). Same lifecycle as {@link #cooldownTimer}. */
    private long[] secondaryAimTargetId = new long[INITIAL_CAPACITY];
    /** Per-unit burst rounds queued after the AI's initial primary shot. Decremented one-per-spacing in {@code InfantryWeapons.tick}. Same lifecycle as {@link #cooldownTimer}. */
    private int[] burstRemaining = new int[INITIAL_CAPACITY];
    /** Per-unit sim-seconds until the next queued burst round fires. Same lifecycle as {@link #cooldownTimer}. */
    private float[] burstTimer = new float[INITIAL_CAPACITY];
    /** Per-unit entity id captured when the burst was queued (0L = idle). Same lifecycle as {@link #cooldownTimer}. */
    private long[] burstTargetId = new long[INITIAL_CAPACITY];
    /** Per-unit current-target entity id (0L = no target). Same lifecycle as {@link #cooldownTimer}. */
    private long[] targetId = new long[INITIAL_CAPACITY];
    /** Per-unit sim-seconds until the unit may next micro-reposition between shots. Decremented in {@code InfantryUnitPrep.tickCooldowns}. Same lifecycle as {@link #cooldownTimer}. */
    private float[] repositionCooldown = new float[INITIAL_CAPACITY];
    /** Per-unit sim-seconds remaining in break-contact fall-back state (>0 = falling back toward {@link #fallbackCellX}/{@link #fallbackCellY}). Same lifecycle as {@link #cooldownTimer}. */
    private float[] fallbackTimer = new float[INITIAL_CAPACITY];
    /** Per-unit cached fall-back destination cell X (-1 = none). Paired with {@link #fallbackCellY}; same int-pair layout as {@link #cellX}. Same lifecycle as {@link #cooldownTimer}. */
    private int[] fallbackCellX = new int[INITIAL_CAPACITY];
    /** Per-unit cached fall-back destination cell Y, paired with {@link #fallbackCellX}. */
    private int[] fallbackCellY = new int[INITIAL_CAPACITY];
    /** Per-unit FLEE-role idle pause between wander legs (sim-seconds). Same lifecycle as {@link #cooldownTimer}. */
    private float[] wanderDwellTimer = new float[INITIAL_CAPACITY];
    private int liveCount = 0;
    private long nextId = 1L;

    private final Long2IntOpenHashMap indexById = new Long2IntOpenHashMap();

    /**
     * Smooth render position, decomposed out of the dense table into a
     * standalone entity-id-keyed service so it survives release (the corpse
     * draws its frozen death pose where it fell). The registry owns the
     * instance and seeds it in {@link #allocate}; {@link Entity#getRenderX()} /
     * {@link Entity#setRenderPos} route through the per-unit
     * {@link Entity#renderPositions} reference, which is <b>not</b> nulled on
     * release. See {@link RenderPositionService} for the entity-id-keyed
     * rationale.
     */
    private final RenderPositionService renderPositions = new RenderPositionService();

    /**
     * The battle's archetype-table entity world + its game component
     * registrations — owned here <em>for the transition</em> (same owned-sub-store
     * shape as {@link #renderPositions}): {@link #allocate} is the single spawn
     * seam, so owning the world keeps "mint the id" and "adopt it into the world
     * as {@code {IDENTITY, HEALTH}}" in one place, and lets every standalone
     * registry user (tests included) get a coherent world for free.
     * {@code BattleSimulation} re-exposes both via its getters. When step 4
     * dissolves this registry, ownership hops up to the sim.
     */
    private final EntityWorld entityWorld = new EntityWorld();
    private final BattleComponents components = new BattleComponents(entityWorld);

    public UnitRegistry() {
        // Make missing-key lookups return INVALID_INDEX directly. The remove
        // path relies on this too: Long2IntOpenHashMap.remove returns the
        // default-return-value when the key isn't present, so duplicate
        // release calls are no-ops without the caller checking first.
        indexById.defaultReturnValue(INVALID_INDEX);
    }

    /**
     * Adds {@code u} to the next dense slot, assigns its
     * {@link Entity#entityId}, and returns the id. Grows the backing array
     * by doubling on overflow.
     *
     * <p>Rejects re-allocation: a {@link Entity} whose {@code entityId} is
     * non-zero already lives in the registry, and re-allocating would mint
     * a new id pointing at the same instance while the old id stays mapped
     * to a now-stale dense slot — a later release on the old id would null
     * a slot the new id still resolves to. The throw makes the double-add
     * a loud setup bug rather than a silent corruption.
     */
    public long allocate(Entity u) {
        if (u.entityId != 0L) {
            throw new IllegalStateException(
                    "Entity '" + u.id + "' already has entityId " + u.entityId + " — double allocate");
        }
        if (liveCount == dense.length) {
            int newCap = dense.length * 2;
            dense = Arrays.copyOf(dense, newCap);
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
        // Adopt the minted id into the entity world as a live {IDENTITY,
        // POSITION, HEALTH} entity. Identity is written once here and persists
        // alive→dead (the corpse transmute's row-move carries it — as does the
        // cell, which IS the death cell by the time the corpse forms); Position
        // and Health seed from the write-only seed* fields and are canonical in
        // the world thereafter — "has HEALTH with hp > 0" is the liveness
        // definition (isAliveById).
        entityWorld.createEntity(id, components.IDENTITY, components.POSITION, components.HEALTH);
        entityWorld.setObject(id, components.IDENTITY, BattleComponents.IDENTITY_TYPE, u.type);
        entityWorld.setObject(id, components.IDENTITY, BattleComponents.IDENTITY_FACTION, u.faction);
        entityWorld.setInt(id, components.POSITION, BattleComponents.POSITION_CELL_X, u.seedCellX);
        entityWorld.setInt(id, components.POSITION, BattleComponents.POSITION_CELL_Y, u.seedCellY);
        entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_HP, u.seedHp);
        entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_MAX_HP, u.seedMaxHp);
        // Seed the remaining dense seed-bearing columns from the unit's
        // pre-allocation transient fields. After this point these are canonical
        // and none is snapshotted back on release — the Group-S stats seed from
        // write-only seed* fields.
        attackDamage[liveCount] = u.seedAttackDamage;
        attackRange[liveCount] = u.seedAttackRange;
        accuracy[liveCount] = u.seedAccuracy;
        // Reset the mid-combat-only columns to their defaults. These have no
        // pre-allocation seed (a fresh unit starts at rest) and no post-release
        // reader, so they carry no local* twin on Entity; the explicit reset
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
     * "never allocated" sentinel a setup-discarded {@link Entity} carries,
     * so routing it through the map (where it would also miss, since
     * {@code nextId} starts at 1) would still be a no-op — the explicit
     * guard makes the contract intentional rather than incidental.
     */
    public void release(long id) {
        if (id == 0L) return;
        int idx = indexById.remove(id);
        if (idx == INVALID_INDEX) return;
        int last = liveCount - 1;
        // Nothing is snapshotted back onto the released unit, and there is no
        // back-pointer to clear (Entity no longer holds one). The cell pair and
        // the Group-S stats carry no post-release shadow: the cell's post-release
        // readers (turret/hub demolition + mech wreck handlers) read the death
        // cell off the DeathEvent snapshot, and render position lives in the
        // RenderPositionService keyed by entityId — NOT cleared on release, so
        // the corpse's death-pose location survives. The world entity is NOT
        // touched here: hp lives in its HEALTH component (isAliveById reads it,
        // so a released-pre-transmute id still reports dead via hp <= 0), and
        // the death drain transmutes it to the corpse archetype.
        if (idx != last) {
            Entity tail = dense[last];
            dense[idx] = tail;
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
     * Liveness for a held entity id — has a {@code HEALTH} component with
     * {@code hp > 0}. Backs {@code World.isAlive(id)} and every held-ref
     * liveness check. The definition is now purely world-side: a corpse fails
     * it by <em>lacking</em> {@code HEALTH} (the death transmute removed it), a
     * just-killed-not-yet-transmuted id fails on {@code hp <= 0} (every release
     * path zeroes hp first), and a never-allocated / {@code 0L} id misses the
     * world entirely. One tolerant-read location probe, no registry involvement
     * — this is the end-state liveness the registry's dissolution leaves behind.
     */
    public boolean isAliveById(long id) {
        return entityWorld.getFloat(id, components.HEALTH, BattleComponents.HEALTH_HP, 0f) > 0f;
    }

    /**
     * Returns the {@link Entity} for {@code id}, or {@code null} if the id is
     * unknown (never allocated) or released. The lazy-validity replacement
     * for the old {@code target != null && target.isAlive()} idiom — a
     * dangling {@code long} resolves cleanly to null without the holder
     * needing to know whether the target was killed or just never existed.
     *
     * <p>{@code id == 0L} (the "no entity" sentinel) returns null without
     * a map probe — that path runs every tick from every behavior that
     * checks "do I have a target," so the fast-path matters.
     */
    public Entity getOrNull(long id) {
        if (id == 0L) return null;
        int idx = indexById.get(id);
        if (idx == INVALID_INDEX) return null;
        return dense[idx];
    }

    /** Returns the unit at dense slot {@code idx}. Callers iterate over {@code [0, liveCount())}; no bounds check. */
    public Entity get(int idx) {
        return dense[idx];
    }

    /**
     * Resolves a live entity id to its dense slot, fail-loud on an
     * unknown/released id. Backs the {@link World}-facade by-id accessors (the
     * hot face): {@code world.hp(id)} resolves the index once here, then reads
     * the existing by-idx column accessor. Those serve random-access reads of
     * <em>live</em> entities, so a dead id is a programming error (unlike
     * {@link #getOrNull}, whose null is a defined "dead/never" answer for
     * held-ref liveness).
     */
    public int requireLiveIndex(long id) {
        int idx = indexById.get(id);
        if (idx == INVALID_INDEX) throw new IllegalArgumentException("no live entity for id " + id);
        return idx;
    }

    // Transitional by-id hp adapters over the world's HEALTH columns — call
    // sites keep their registry/World receiver while the storage lives in the
    // entity world. Strict reads (the engine throws if the entity is gone or
    // already transmuted to a corpse, i.e. fail-loud after the death drain).
    // These dissolve with the registry in migration step 4.
    public float hpById(long id) { return entityWorld.getFloat(id, components.HEALTH, BattleComponents.HEALTH_HP); }
    public void setHpById(long id, float v) { entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_HP, v); }
    public float maxHpById(long id) { return entityWorld.getFloat(id, components.HEALTH, BattleComponents.HEALTH_MAX_HP); }
    public void setMaxHpById(long id, float v) { entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_MAX_HP, v); }

    // Transitional by-id cell adapters over the world's POSITION columns — same
    // shape and fate as the hp adapters above. Strict reads (fail-loud once the
    // entity is gone; a corpse still answers — POSITION persists alive→dead).
    public int cellXById(long id) { return entityWorld.getInt(id, components.POSITION, BattleComponents.POSITION_CELL_X); }
    public int cellYById(long id) { return entityWorld.getInt(id, components.POSITION, BattleComponents.POSITION_CELL_Y); }
    public void setCellPosById(long id, int x, int y) {
        entityWorld.setInt(id, components.POSITION, BattleComponents.POSITION_CELL_X, x);
        entityWorld.setInt(id, components.POSITION, BattleComponents.POSITION_CELL_Y, y);
    }

    public float getCooldownTimer(int idx) { return cooldownTimer[idx]; }
    public void setCooldownTimer(int idx, float v) { cooldownTimer[idx] = v; }
    public float[] cooldownTimerArray() { return cooldownTimer; }

    public float getMoveProgress(int idx) { return moveProgress[idx]; }
    public void setMoveProgress(int idx, float v) { moveProgress[idx] = v; }
    public float[] moveProgressArray() { return moveProgress; }

    /**
     * The decomposed render-position service this registry seeds + wires on
     * {@link #allocate}. Render position is keyed by {@link Entity#entityId} and
     * survives release (see {@link RenderPositionService}); the registry no
     * longer holds dense {@code renderX/renderY} columns.
     */
    public RenderPositionService getRenderPositions() { return renderPositions; }

    /** The battle's archetype-table entity world — owned here for the transition (see the field doc). */
    public EntityWorld entityWorld() { return entityWorld; }

    /** Game component-type registrations + shared queries for {@link #entityWorld()}. */
    public BattleComponents components() { return components; }

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
     * replaced by {@link #allocate(Entity)} when {@link #liveCount()} hits
     * {@code dense.length}; a cached reference becomes a stale view of an
     * abandoned array. Safe to alias for the duration of a single tick
     * phase that doesn't allocate (the parallel UPDATE_UNITS dispatch is
     * the intended Phase 2 consumer — spawns are queued and flushed in a
     * separate serial phase, so the array is stable across the dispatch).
     */
    public Entity[] denseArray() {
        return dense;
    }
}
