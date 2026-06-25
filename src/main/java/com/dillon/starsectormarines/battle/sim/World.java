package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;

/**
 * Entity-access facade — the artemis-shaped read layer over the battle's entity
 * storage. The entity is its {@code long} id; you reach its state <em>by id</em>
 * through one receiver, instead of holding an {@code Entity} object that
 * self-routes. This is the access half of the {@code world-facade} endgame (see
 * {@code roadmap/ecs-migration/stories/world-facade.md}).
 *
 * <p><b>By-id primitive accessors</b> ({@link #hp}/{@code setHp}, cell, combat,
 * movement, …) back directly onto the {@link UnitRegistry}'s {@code *ById}
 * adapters over the archetype {@code EntityWorld} — one location probe + column
 * read, <b>zero object construction</b>. Mandatory columns (hp/cell) are always
 * present; each optional capability exposes a presence check + typed accessor
 * ({@link #hasSecondaryWeapon}/{@link #secondaryWeapon},
 * {@link #hasMechLoadout}/{@link #mechLoadout}). The field reads are fail-loud
 * without the component, so gate on the presence check first (or use the
 * null-returning typed accessor where one is provided). Per-tick <b>bulk</b>
 * systems do not use this — they iterate the dense registry array or a world
 * {@code Query}'s columns directly; this is the random-access / held-ref path.
 *
 * <p>Serial-only — built for the single-threaded tick + render read.
 */
public final class World {

    private final UnitRegistry registry;

    public World(UnitRegistry registry) {
        this.registry = registry;
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

    // Movement lives in the entity world's OPTIONAL MOVEMENT component (migration
    // step 3e) — same transitional by-id adapter routing as combat above, but
    // narrowed to movers: a static emplacement (turret, drone hub) has no MOVEMENT.
    // hasMovement is the presence check; the field accessors are fail-loud on a
    // unit that lacks it (and once the death drain has transmuted to a corpse).
    public boolean hasMovement(long id) { return registry.hasMovement(id); }
    public float moveProgress(long id) { return registry.moveProgressById(id); }
    public void setMoveProgress(long id, float v) { registry.setMoveProgressById(id, v); }

    // The path reference + cursor live in the MOVEMENT component too. setPathRef
    // is the raw column write; the occupancy-bookkeeping path change goes through
    // BattleControl.setPath (NavigationService), which calls this under the hood.
    public int[] path(long id) { return registry.pathById(id); }
    public void setPathRef(long id, int[] p) { registry.setPathRefById(id, p); }
    public int pathIdx(long id) { return registry.pathIdxById(id); }
    public void setPathIdx(long id, int v) { registry.setPathIdxById(id, v); }

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

    // AI-cadence state lives in the entity world's OPTIONAL AI_STATE component
    // (migration step 3f) — same transitional by-id adapter routing as movement
    // above, and likewise narrowed to thinkers: a static emplacement (turret,
    // drone hub) has no decision cadence. hasAiState is the presence check; the
    // field accessors are fail-loud on a unit that lacks it (and once the death
    // drain has transmuted to a corpse).
    public boolean hasAiState(long id) { return registry.hasAiState(id); }
    public float repositionCooldown(long id) { return registry.repositionCooldownById(id); }
    public void setRepositionCooldown(long id, float v) { registry.setRepositionCooldownById(id, v); }

    public float fallbackTimer(long id) { return registry.fallbackTimerById(id); }
    public void setFallbackTimer(long id, float v) { registry.setFallbackTimerById(id, v); }

    public int fallbackCellX(long id) { return registry.fallbackCellXById(id); }
    public int fallbackCellY(long id) { return registry.fallbackCellYById(id); }
    public void setFallbackCell(long id, int x, int y) { registry.setFallbackCellById(id, x, y); }

    public float wanderDwellTimer(long id) { return registry.wanderDwellTimerById(id); }
    public void setWanderDwellTimer(long id, float v) { registry.setWanderDwellTimerById(id, v); }

    // Mech loadout is an OPTIONAL capability in the world's MECH_LOADOUT component
    // (one OBJECT column holding the MechLoadoutComponent state bag) — presence IS
    // "is a mech". mechLoadout returns null when absent (so the scattered
    // `m == null` decide-phase reads keep working); attachMechLoadout is the
    // spawn-time grant; the dead mech keeps the component until the wreck handler
    // detaches it. The live-mech bulk fire pass walks the MECH_LOADOUT query, not
    // these by-id accessors.
    public boolean hasMechLoadout(long id) { return registry.hasMechLoadout(id); }
    public MechLoadoutComponent mechLoadout(long id) { return registry.mechLoadoutOf(id); }
    /** Grant the mech-loadout capability at spawn (archetype row-move). Serial-only; see {@link UnitRegistry#attachMechLoadout}. */
    public void attachMechLoadout(long id, MechLoadoutComponent loadout) { registry.attachMechLoadout(id, loadout); }
}
