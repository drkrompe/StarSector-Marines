package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;

import java.util.Map;

/**
 * Entity-access facade — the artemis-shaped read layer over the two stores the
 * sim already keeps: the dense SoA {@link UnitRegistry} (universal hot columns)
 * and the sparse {@link ComponentStore}s (optional capabilities). The entity is
 * its {@code long} id; you reach its state <em>by id</em> through one receiver,
 * instead of holding a {@code Entity} object that self-routes. This is the access
 * half of the {@code world-facade} endgame (see
 * {@code roadmap/ecs-migration/stories/world-facade.md}); as call sites migrate
 * onto it, {@code Entity} loses its {@code registry} back-pointer and shrinks to a
 * bare id + immutable archetype ({@code Entity} → {@code Entity}).
 *
 * <p><b>Two faces, deliberately split</b> — the split is what preserves the
 * ECS primitive / cache-locality win while adding the ergonomic by-id access:
 *
 * <ul>
 *   <li><b>Hot face — primitive accessors</b> ({@link #hp}, {@code setHp}, and
 *       one method per dense column as groups migrate). Backed directly by the
 *       SoA arrays via {@link UnitRegistry}'s {@code *ById} accessors — one map
 *       probe + array read, <b>zero object construction</b>. Mandatory columns
 *       (every live entity has hp/cell/position) live here. Per-tick <b>bulk</b>
 *       systems do not even use this — they iterate the dense arrays over
 *       {@code [0, liveCount())}; the primitive face is the random-access / held-
 *       ref path.</li>
 *   <li><b>Cold face — projected components</b> ({@code id(id).getOrNull(Cmp.class)}).
 *       Opt-in convenience for optional capabilities, debug/UI, cross-cutting
 *       reads. Today every projected component is a <b>sparse store lookup</b>
 *       (zero construction — presence <em>is</em> the data, the real artemis
 *       {@code ComponentMapper.get}); a dense-column group projected into a view
 *       object would allocate, so such a projection must stay off hot paths.
 *       <b>Never materialize a component inside a per-tick bulk loop.</b></li>
 * </ul>
 *
 * <p>Serial-only, mirroring {@link ComponentStore} — built for the
 * single-threaded tick + render read.
 */
public final class World {

    private final UnitRegistry registry;
    /**
     * Component class → its sparse store, for the cold projection face. Wired
     * once at sim construction with the stores that exist today
     * ({@code CrashingComponent}, {@code MechLoadoutComponent}); the corpse home
     * moved to the archetype {@code EntityWorld} (the committed storage target
     * this facade's stores migrate toward).
     */
    private final Map<Class<?>, ComponentStore<?>> stores;

    public World(UnitRegistry registry, Map<Class<?>, ComponentStore<?>> stores) {
        this.registry = registry;
        this.stores = stores;
    }

    // ---- hot face: primitive by-id accessors over the dense SoA ----
    //
    // Each resolves the dense index once via UnitRegistry.requireLiveIndex(id)
    // (fail-loud on a dead/unknown id — these serve live entities; use isAlive()/getOrNull
    // for liveness on a maybe-released id) then reads the existing by-idx column
    // accessor. No Entity dereference. Bulk per-tick systems do NOT use these —
    // they iterate the dense arrays over [0, liveCount()).

    /**
     * Liveness for a held entity id — has a {@code HEALTH} component with
     * {@code hp > 0}; {@code false} for a corpse (the death transmute removed
     * {@code HEALTH}), a never-allocated id, and {@code 0L}. The by-id
     * replacement for {@code Entity.isAlive()}: this is the <em>non</em>-fail-loud
     * face (unlike {@link #hp}), the defined "dead/never" answer for a
     * maybe-released ref. Mirrors {@link UnitRegistry#isAliveById}.
     */
    public boolean isAlive(long id) { return registry.isAliveById(id); }

    // hp lives in the entity world's HEALTH columns (migration step 3); these
    // delegate to the registry's transitional by-id adapters so the facade
    // surface is unchanged for its callers. Fail-loud once the death drain has
    // transmuted the entity to a corpse (HEALTH gone).
    public float hp(long id) { return registry.hpById(id); }
    public void setHp(long id, float v) { registry.setHpById(id, v); }

    public float maxHp(long id) { return registry.maxHpById(id); }
    public void setMaxHp(long id, float v) { registry.setMaxHpById(id, v); }

    // The cell pair lives in the entity world's POSITION columns (migration
    // step 3b) — same transitional-adapter routing as hp above. POSITION
    // persists alive→dead, so a corpse still answers cell reads.
    public int cellX(long id) { return registry.cellXById(id); }
    public int cellY(long id) { return registry.cellYById(id); }
    public void setCellPos(long id, int x, int y) { registry.setCellPosById(id, x, y); }

    // Combat lives in the entity world's COMBAT columns (migration step 3) —
    // same transitional by-id adapter routing as hp/cell above. Fail-loud once
    // the death drain has transmuted the entity to a corpse (COMBAT gone).
    public float cooldownTimer(long id) { return registry.cooldownTimerById(id); }
    public void setCooldownTimer(long id, float v) { registry.setCooldownTimerById(id, v); }

    // Movement lives in the entity world's MOVEMENT component (migration step
    // 3e) — same transitional by-id adapter routing as combat above. Fail-loud
    // once the death drain has transmuted the entity to a corpse (MOVEMENT gone).
    public float moveProgress(long id) { return registry.moveProgressById(id); }
    public void setMoveProgress(long id, float v) { registry.setMoveProgressById(id, v); }

    public float attackDamage(long id) { return registry.attackDamageById(id); }
    public void setAttackDamage(long id, float v) { registry.setAttackDamageById(id, v); }

    public float attackRange(long id) { return registry.attackRangeById(id); }
    public void setAttackRange(long id, float v) { registry.setAttackRangeById(id, v); }

    public float accuracy(long id) { return registry.accuracyById(id); }
    public void setAccuracy(long id, float v) { registry.setAccuracyById(id, v); }

    public long targetId(long id) { return registry.targetIdById(id); }
    public void setTargetId(long id, long v) { registry.setTargetIdById(id, v); }

    public int burstRemaining(long id) { return registry.burstRemainingById(id); }
    public void setBurstRemaining(long id, int v) { registry.setBurstRemainingById(id, v); }

    public float burstTimer(long id) { return registry.burstTimerById(id); }
    public void setBurstTimer(long id, float v) { registry.setBurstTimerById(id, v); }

    public long burstTargetId(long id) { return registry.burstTargetIdById(id); }
    public void setBurstTargetId(long id, long v) { registry.setBurstTargetIdById(id, v); }

    // Secondary weapon is an OPTIONAL capability living in the world's
    // SECONDARY_WEAPON component (migration step 3 — first optional live
    // capability). hasSecondaryWeapon is the presence check that replaces the old
    // `secondaryWeapon != null`; every other accessor is fail-loud on a unit that
    // lacks the component, so callers MUST gate on hasSecondaryWeapon first.
    public boolean hasSecondaryWeapon(long id) { return registry.hasSecondaryWeapon(id); }
    public MarineSecondary secondaryWeapon(long id) { return registry.secondaryWeaponOf(id); }
    public int secondaryAmmo(long id) { return registry.secondaryAmmoById(id); }
    public void setSecondaryAmmo(long id, int v) { registry.setSecondaryAmmoById(id, v); }

    public float secondaryCooldownTimer(long id) { return registry.secondaryCooldownTimerById(id); }
    public void setSecondaryCooldownTimer(long id, float v) { registry.setSecondaryCooldownTimerById(id, v); }

    public float secondaryActionTimer(long id) { return registry.secondaryActionTimerById(id); }
    public void setSecondaryActionTimer(long id, float v) { registry.setSecondaryActionTimerById(id, v); }

    public long secondaryAimTargetId(long id) { return registry.secondaryAimTargetIdById(id); }
    public void setSecondaryAimTargetId(long id, long v) { registry.setSecondaryAimTargetIdById(id, v); }

    public boolean secondaryFired(long id) { return registry.secondaryFiredById(id); }
    public void setSecondaryFired(long id, boolean v) { registry.setSecondaryFiredById(id, v); }

    /** Grant the secondary capability to a live unit at runtime (archetype row-move). Serial-only; see {@link UnitRegistry#attachSecondaryWeapon}. */
    public void attachSecondaryWeapon(long id, MarineSecondary spec, int ammo) { registry.attachSecondaryWeapon(id, spec, ammo); }

    public float repositionCooldown(long id) { return registry.getRepositionCooldown(registry.requireLiveIndex(id)); }
    public void setRepositionCooldown(long id, float v) { registry.setRepositionCooldown(registry.requireLiveIndex(id), v); }

    public float fallbackTimer(long id) { return registry.getFallbackTimer(registry.requireLiveIndex(id)); }
    public void setFallbackTimer(long id, float v) { registry.setFallbackTimer(registry.requireLiveIndex(id), v); }

    public int fallbackCellX(long id) { return registry.getFallbackCellX(registry.requireLiveIndex(id)); }
    public int fallbackCellY(long id) { return registry.getFallbackCellY(registry.requireLiveIndex(id)); }
    public void setFallbackCell(long id, int x, int y) { registry.setFallbackCell(registry.requireLiveIndex(id), x, y); }

    public float wanderDwellTimer(long id) { return registry.getWanderDwellTimer(registry.requireLiveIndex(id)); }
    public void setWanderDwellTimer(long id, float v) { registry.setWanderDwellTimer(registry.requireLiveIndex(id), v); }

    // ---- cold face: projected component access ----

    /**
     * Direct, <b>zero-allocation</b> sparse-component lookup — the artemis
     * {@code ComponentMapper.get} for the cold stores. Returns {@code id}'s
     * component of {@code type}, or null if it has none / the type has no store.
     *
     * <p>This is the hot-path-safe component read: unlike {@link #id(long)}
     * {@code .getOrNull}, it allocates no handle, so it's the one to use inside
     * per-tick systems and per-unit decide-phase code (the guardrail is "never
     * <em>materialize</em> a component in a bulk loop" — a handle is the
     * materialization, the store lookup itself is fine). Reserve the
     * {@code id(id).getOrNull} sugar for incidental, off-hot-path reads.
     */
    public <T> T component(long id, Class<T> type) {
        ComponentStore<?> store = stores.get(type);
        return store == null ? null : type.cast(store.get(id));
    }

    /**
     * Presence check for a sparse component — one store lookup, no cast, no
     * allocation. The capability-as-presence query that replaces a scattered
     * nullable-field {@code != null} test (e.g. the former {@code u.mech != null}).
     */
    public boolean hasComponent(long id, Class<?> type) {
        ComponentStore<?> store = stores.get(type);
        return store != null && store.has(id);
    }

    /**
     * An entity handle bound to {@code id} for the cold projection face. A small
     * allocation per call — acceptable because this face is opt-in and off the
     * hot path (see class doc). If a projected component ever gets hot, the fix
     * is {@link #component(long, Class)} (zero-alloc), not a flyweight band-aid.
     */
    public EntityHandle id(long id) { return new EntityHandle(id, this); }

    /**
     * Cold-face handle: {@code world.id(entityId).getOrNull(Cmp.class)}. Bound to
     * one entity id; obtain a fresh one per access via {@link World#id(long)}.
     */
    public record EntityHandle(long id, World world) {
        /** This entity's {@code type} component, or null if it doesn't carry one. */
        public <T> T getOrNull(Class<T> type) { return world.component(id, type); }
    }
}
