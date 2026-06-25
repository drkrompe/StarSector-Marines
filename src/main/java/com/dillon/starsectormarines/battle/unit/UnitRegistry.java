package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
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
    // The per-unit AI-cadence columns (repositionCooldown, fallbackTimer,
    // fallbackCellX/Y, wanderDwellTimer) left these dense arrays for the world's
    // AI_STATE component in step 3f — the last dense SoA columns to migrate, so
    // the registry now holds only the dense Entity[] + its id↔slot map.
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
        }
        long id = nextId++;
        u.entityId = id;
        dense[liveCount] = u;
        // Adopt the minted id into the entity world. Every live unit is at least
        // {IDENTITY, POSITION, HEALTH, COMBAT}; on top of that:
        //   - MOVEMENT + AI_STATE iff the unit is mobile. A static emplacement (a
        //     turret or drone hub; UnitType.isStatic) neither paths nor decides, so
        //     "has MOVEMENT" defines a mover and "has AI_STATE" a thinker — presence
        //     IS the capability (like SECONDARY_WEAPON; no inert path/cadence columns
        //     on a turret). The handful of all-unit readers gate on hasMovement /
        //     hasAiState; per-unit behavior code only ever runs for movers/thinkers.
        //   - SECONDARY_WEAPON iff the unit carries one.
        // Identity is written once here and persists alive→dead (the corpse
        // transmute's row-move carries it — as does the cell, which IS the death
        // cell by the time the corpse forms); Position, Health and Combat seed from
        // the write-only seed* fields and are canonical in the world thereafter —
        // "has HEALTH with hp > 0" is the liveness definition (isAliveById). The
        // corpse transmute removes HEALTH, COMBAT, and any MOVEMENT / AI_STATE /
        // SECONDARY_WEAPON (a remove of a component a static unit never had is a
        // no-op).
        boolean mobile = !u.type.isStatic();
        boolean hasSecondary = u.seedSecondaryWeapon != null;
        ComponentType[] archetype = new ComponentType[4 + (mobile ? 2 : 0) + (hasSecondary ? 1 : 0)];
        int c = 0;
        archetype[c++] = components.IDENTITY;
        archetype[c++] = components.POSITION;
        archetype[c++] = components.HEALTH;
        archetype[c++] = components.COMBAT;
        if (mobile) {
            archetype[c++] = components.MOVEMENT;
            archetype[c++] = components.AI_STATE;
        }
        if (hasSecondary) archetype[c++] = components.SECONDARY_WEAPON;
        entityWorld.createEntity(id, archetype);
        entityWorld.setObject(id, components.IDENTITY, BattleComponents.IDENTITY_TYPE, u.type);
        entityWorld.setObject(id, components.IDENTITY, BattleComponents.IDENTITY_FACTION, u.faction);
        entityWorld.setInt(id, components.POSITION, BattleComponents.POSITION_CELL_X, u.seedCellX);
        entityWorld.setInt(id, components.POSITION, BattleComponents.POSITION_CELL_Y, u.seedCellY);
        entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_HP, u.seedHp);
        entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_MAX_HP, u.seedMaxHp);
        // Seed the COMBAT stat columns from the unit's pre-allocation seed*
        // fields; the mid-combat COMBAT scalars (cooldownTimer, targetId, burst*)
        // start at zero — a fresh world row is zero-initialised by append, so no
        // explicit reset is needed (unlike the registry-resident columns below,
        // whose dense slot may be reused after a swap-and-pop release).
        entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_DAMAGE, u.seedAttackDamage);
        entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_RANGE, u.seedAttackRange);
        entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ACCURACY, u.seedAccuracy);
        // Seed the SECONDARY_WEAPON spec + ammo when present; its mid-combat
        // scalars (cooldown/action timers, aim target, fired latch) start at zero
        // by the world's row append.
        if (hasSecondary) {
            entityWorld.setObject(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_SPEC, u.seedSecondaryWeapon);
            entityWorld.setInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AMMO, u.seedSecondaryAmmo);
        }
        // Seed the non-zero defaults of the mobile-only components (a static unit
        // carries neither, so there's nothing to seed). AI_STATE's fall-back cell
        // is the one mid-combat scalar whose default isn't zero (-1/-1 = "no cached
        // cell"; readers treat a non-negative cell as a live cached destination),
        // and MOVEMENT's path is an OBJECT column that appends as null while every
        // path reader dereferences it (length / Paths helpers) — both must be seeded
        // since a fresh world row can't supply them. The remaining AI_STATE/MOVEMENT
        // scalars (reposition/fallback/wander timers, pathIdx, moveProgress) start
        // at zero by the row append, as do all the COMBAT mid-combat scalars — a
        // fresh per-id world row means no slot-reuse reset is needed (unlike the old
        // dense columns).
        if (mobile) {
            entityWorld.setInt(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_CELL_X, -1);
            entityWorld.setInt(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_CELL_Y, -1);
            entityWorld.setObject(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH, GridPathfinder.EMPTY_PATH);
        }
        // Seed + wire the decomposed render-position service. Unlike the world
        // entity, this reference is NOT nulled on release — the entry survives so
        // a released corpse still resolves its death-pose location.
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
        // back-pointer to clear (Entity no longer holds one). No per-unit state
        // is moved by the swap any more — every column lives in the entity world
        // keyed by id, immune to the dense reshuffle: the cell pair + Group-S
        // stats persist (the cell's post-release readers — turret/hub demolition
        // + mech wreck handlers — read the death cell off the DeathEvent
        // snapshot, render position lives in the RenderPositionService keyed by
        // entityId, NOT cleared on release), and hp (HEALTH), the combat
        // stats/scalars (COMBAT), moveProgress (MOVEMENT), the AI-cadence scalars
        // (AI_STATE), and any SECONDARY_WEAPON all stay under the entity's id
        // until the death drain transmutes it to the corpse archetype, which
        // removes the live-only ones. So the swap-and-pop only moves the dense
        // Entity[] slot + fixes the tail's id↔slot mapping.
        if (idx != last) {
            Entity tail = dense[last];
            dense[idx] = tail;
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

    // Transitional by-id combat adapters over the world's COMBAT columns — same
    // shape and fate as the hp/cell adapters above. Strict reads (fail-loud once
    // the entity is gone OR transmuted to a corpse — a corpse lacks COMBAT). The
    // attack stats are seed-only; the rest are mid-combat scalars.
    public float attackDamageById(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_DAMAGE); }
    public void setAttackDamageById(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_DAMAGE, v); }
    public float attackRangeById(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_RANGE); }
    public void setAttackRangeById(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_RANGE, v); }
    public float accuracyById(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_ACCURACY); }
    public void setAccuracyById(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ACCURACY, v); }
    public float cooldownTimerById(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER); }
    public void setCooldownTimerById(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER, v); }
    public long targetIdById(long id) { return entityWorld.getLong(id, components.COMBAT, BattleComponents.COMBAT_TARGET_ID); }
    public void setTargetIdById(long id, long v) { entityWorld.setLong(id, components.COMBAT, BattleComponents.COMBAT_TARGET_ID, v); }
    public int burstRemainingById(long id) { return entityWorld.getInt(id, components.COMBAT, BattleComponents.COMBAT_BURST_REMAINING); }
    public void setBurstRemainingById(long id, int v) { entityWorld.setInt(id, components.COMBAT, BattleComponents.COMBAT_BURST_REMAINING, v); }
    public float burstTimerById(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_BURST_TIMER); }
    public void setBurstTimerById(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_BURST_TIMER, v); }
    public long burstTargetIdById(long id) { return entityWorld.getLong(id, components.COMBAT, BattleComponents.COMBAT_BURST_TARGET_ID); }
    public void setBurstTargetIdById(long id, long v) { entityWorld.setLong(id, components.COMBAT, BattleComponents.COMBAT_BURST_TARGET_ID, v); }

    // Transitional by-id secondary-weapon adapters over the world's optional
    // SECONDARY_WEAPON component. hasSecondaryWeapon is the presence check that
    // replaces the old `u.secondaryWeapon != null`; every field read below is
    // fail-loud on a unit that lacks the component, so callers MUST gate on
    // hasSecondaryWeapon first.
    public boolean hasSecondaryWeapon(long id) { return entityWorld.has(id, components.SECONDARY_WEAPON); }
    public MarineSecondary secondaryWeaponOf(long id) { return (MarineSecondary) entityWorld.getObject(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_SPEC); }
    public int secondaryAmmoById(long id) { return entityWorld.getInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AMMO); }
    public void setSecondaryAmmoById(long id, int v) { entityWorld.setInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AMMO, v); }
    public float secondaryCooldownTimerById(long id) { return entityWorld.getFloat(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_COOLDOWN_TIMER); }
    public void setSecondaryCooldownTimerById(long id, float v) { entityWorld.setFloat(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_COOLDOWN_TIMER, v); }
    public float secondaryActionTimerById(long id) { return entityWorld.getFloat(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_ACTION_TIMER); }
    public void setSecondaryActionTimerById(long id, float v) { entityWorld.setFloat(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_ACTION_TIMER, v); }
    public long secondaryAimTargetIdById(long id) { return entityWorld.getLong(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AIM_TARGET_ID); }
    public void setSecondaryAimTargetIdById(long id, long v) { entityWorld.setLong(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AIM_TARGET_ID, v); }
    public boolean secondaryFiredById(long id) { return entityWorld.getInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_FIRED) != 0; }
    public void setSecondaryFiredById(long id, boolean v) { entityWorld.setInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_FIRED, v ? 1 : 0); }

    /**
     * Grants the secondary-weapon capability to an already-live unit — the
     * runtime counterpart to seeding it pre-{@link #allocate} (the born-with-it
     * path AirSystem/GroundSystem use). Adds the {@code SECONDARY_WEAPON}
     * component (a one-time archetype row-move) and writes the spec + ammo; the
     * mid-combat scalars start zeroed by the moved row. The model seam for a
     * future "pick up a dropped launcher" mechanic. Serial-only — being a
     * structural change, never call it mid-{@link Query} walk (use the
     * world's command buffer there).
     */
    public void attachSecondaryWeapon(long id, MarineSecondary spec, int ammo) {
        entityWorld.addComponent(id, components.SECONDARY_WEAPON);
        entityWorld.setObject(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_SPEC, spec);
        entityWorld.setInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AMMO, ammo);
    }

    // Transitional by-id movement adapters over the world's optional MOVEMENT
    // component — same shape and fate as the hp/cell/combat adapters above, but
    // OPTIONAL (mirrors SECONDARY_WEAPON): a static emplacement (turret, drone hub)
    // has no MOVEMENT, so hasMovement is the presence check, and every field read
    // below is fail-loud on a unit that lacks the component (also fail-loud once
    // the entity is gone OR transmuted to a corpse). Per-unit movement code only
    // runs for movers; the few all-unit readers gate on hasMovement first.
    public boolean hasMovement(long id) { return entityWorld.has(id, components.MOVEMENT); }
    public float moveProgressById(long id) { return entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_MOVE_PROGRESS); }
    public void setMoveProgressById(long id, float v) { entityWorld.setFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_MOVE_PROGRESS, v); }
    public int[] pathById(long id) { return (int[]) entityWorld.getObject(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH); }
    public void setPathRefById(long id, int[] path) { entityWorld.setObject(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH, path); }
    public int pathIdxById(long id) { return entityWorld.getInt(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX); }
    public void setPathIdxById(long id, int v) { entityWorld.setInt(id, components.MOVEMENT, BattleComponents.MOVEMENT_PATH_IDX, v); }

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

    // Transitional by-id AI-state adapters over the world's optional AI_STATE
    // component — same shape and fate as the movement adapters above, and likewise
    // OPTIONAL: a static emplacement (turret, drone hub) has no decision cadence,
    // so hasAiState is the presence check and every field read below is fail-loud
    // on a unit that lacks the component (also fail-loud once the entity is gone OR
    // transmuted to a corpse). Per-unit decision code only runs for thinkers; the
    // few all-unit readers gate on hasAiState first. The fall-back cell pair seeds
    // -1/-1 at allocate (the one non-zero default); the timers start zeroed by the
    // world row append.
    public boolean hasAiState(long id) { return entityWorld.has(id, components.AI_STATE); }
    public float repositionCooldownById(long id) { return entityWorld.getFloat(id, components.AI_STATE, BattleComponents.AI_STATE_REPOSITION_COOLDOWN); }
    public void setRepositionCooldownById(long id, float v) { entityWorld.setFloat(id, components.AI_STATE, BattleComponents.AI_STATE_REPOSITION_COOLDOWN, v); }
    public float fallbackTimerById(long id) { return entityWorld.getFloat(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_TIMER); }
    public void setFallbackTimerById(long id, float v) { entityWorld.setFloat(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_TIMER, v); }
    public int fallbackCellXById(long id) { return entityWorld.getInt(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_CELL_X); }
    public int fallbackCellYById(long id) { return entityWorld.getInt(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_CELL_Y); }
    public void setFallbackCellById(long id, int x, int y) {
        entityWorld.setInt(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_CELL_X, x);
        entityWorld.setInt(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_CELL_Y, y);
    }
    public float wanderDwellTimerById(long id) { return entityWorld.getFloat(id, components.AI_STATE, BattleComponents.AI_STATE_WANDER_DWELL_TIMER); }
    public void setWanderDwellTimerById(long id, float v) { entityWorld.setFloat(id, components.AI_STATE, BattleComponents.AI_STATE_WANDER_DWELL_TIMER, v); }

    // Transitional by-id mech-loadout adapters over the world's optional
    // MECH_LOADOUT component — presence IS "is a mech". mechLoadoutOf is null-safe
    // (returns null when absent, matching the old store.get); attach/remove mirror
    // attachSecondaryWeapon. The dead mech keeps the component (off the corpse
    // remove mask) until the wreck handler detaches it.
    public boolean hasMechLoadout(long id) { return entityWorld.has(id, components.MECH_LOADOUT); }
    public MechLoadoutComponent mechLoadoutOf(long id) {
        return entityWorld.has(id, components.MECH_LOADOUT)
                ? (MechLoadoutComponent) entityWorld.getObject(id, components.MECH_LOADOUT, BattleComponents.MECH_LOADOUT_STATE)
                : null;
    }
    /** Grant the mech-loadout capability (an {@code addComponent} row-move), mirroring {@link #attachSecondaryWeapon}. Serial-only. */
    public void attachMechLoadout(long id, MechLoadoutComponent loadout) {
        entityWorld.addComponent(id, components.MECH_LOADOUT);
        entityWorld.setObject(id, components.MECH_LOADOUT, BattleComponents.MECH_LOADOUT_STATE, loadout);
    }
    /** Detach the loadout when the wreck spawns (a {@code removeComponent} row-move back to a plain corpse). Serial-only. */
    public void removeMechLoadout(long id) { entityWorld.removeComponent(id, components.MECH_LOADOUT); }

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
