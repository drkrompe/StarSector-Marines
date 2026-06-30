package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirTurrets;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.engine.ThrusterFx;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Entity-access facade — the artemis-shaped read layer over the battle's entity
 * storage. The entity is its {@code long} id; you reach its state <em>by id</em>
 * through one receiver, instead of holding an {@code Entity} object that
 * self-routes. This is the access half of the {@code world-facade} endgame (see
 * {@code roadmap/ecs-migration/stories/world-facade.md}).
 *
 * <p><b>By-id accessors</b> ({@link #hp}/{@code setHp}, cell, combat, movement, …)
 * read the archetype {@link EntityWorld}'s component columns directly by id — one
 * location probe + column read, <b>zero object construction</b>. This is the sole
 * by-id facade; the dissolved {@code UnitRegistry}'s {@code *ById} adapter layer
 * folded into these methods (migration step 4). Mandatory columns (hp/cell) are
 * always present; each optional capability exposes a presence check + typed
 * accessor ({@link #hasSecondaryWeapon}/{@link #secondaryWeapon},
 * {@link #hasMechLoadout}/{@link #mechLoadout}). The field reads are fail-loud
 * without the component, so gate on the presence check first (or use the
 * null-returning typed accessor where one is provided). Per-tick <b>bulk</b>
 * systems still iterate the dense roster array or a world {@code Query}'s columns
 * directly; this is the random-access / held-ref path.
 *
 * <p>Strict vs. tolerant reads mirror the columns' lifecycle: mandatory live
 * columns (hp/cell/combat) are <b>fail-loud</b> once the death drain has
 * transmuted the entity to a corpse; {@link #renderX}/{@link #renderY} are
 * <b>tolerant</b> (0 when the entity is gone / lacks the component) because
 * render code must not fail on a maybe-released ref, and {@code RENDER_POSITION}
 * survives the transmute so a corpse still draws where it fell.
 *
 * <p>Serial-only — built for the single-threaded tick + render read.
 */
public final class World {

    private final EntityWorld entityWorld;
    private final BattleComponents components;
    // World no longer owns COMBAT / MOVEMENT access — it delegates to the
    // per-component Services (the data owners). Held here only so the legacy
    // world.<combat/movement>(id) call sites keep working during the incremental
    // retirement of this facade; new consumers inject the Service directly.
    private final CombatService combat;
    private final MovementService movement;

    public World(EntityWorld entityWorld, BattleComponents components,
                 CombatService combat, MovementService movement) {
        this.entityWorld = entityWorld;
        this.components = components;
        this.combat = combat;
        this.movement = movement;
    }

    /**
     * Liveness for a held entity id — has a {@code HEALTH} component with
     * {@code hp > 0}; {@code false} for a corpse (the death transmute removed
     * {@code HEALTH}), a never-allocated id, and {@code 0L}. The by-id
     * replacement for {@code Entity.isAlive()}: this is the <em>non</em>-fail-loud
     * face (unlike {@link #hp}), the defined "dead/never" answer for a
     * maybe-released ref. A tolerant probe (0 hp when the entity / component is
     * gone) so it never throws — every release path zeroes hp first.
     */
    public boolean isAlive(long id) { return entityWorld.getFloat(id, components.HEALTH, BattleComponents.HEALTH_HP, 0f) > 0f; }

    // hp lives in the entity world's HEALTH columns. Fail-loud once the death
    // drain has transmuted the entity to a corpse (HEALTH gone).
    public float hp(long id) { return entityWorld.getFloat(id, components.HEALTH, BattleComponents.HEALTH_HP); }
    public void setHp(long id, float v) { entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_HP, v); }

    public float maxHp(long id) { return entityWorld.getFloat(id, components.HEALTH, BattleComponents.HEALTH_MAX_HP); }
    public void setMaxHp(long id, float v) { entityWorld.setFloat(id, components.HEALTH, BattleComponents.HEALTH_MAX_HP, v); }

    // The cell pair lives in the entity world's POSITION columns. POSITION
    // persists alive→dead, so a corpse still answers cell reads.
    public int cellX(long id) { return entityWorld.getInt(id, components.POSITION, BattleComponents.POSITION_CELL_X); }
    public int cellY(long id) { return entityWorld.getInt(id, components.POSITION, BattleComponents.POSITION_CELL_Y); }
    public void setCellPos(long id, int x, int y) {
        entityWorld.setInt(id, components.POSITION, BattleComponents.POSITION_CELL_X, x);
        entityWorld.setInt(id, components.POSITION, BattleComponents.POSITION_CELL_Y, y);
    }

    // Smooth render position — the world's universal RENDER_POSITION component
    // (survives the death transmute, so a corpse draws where it fell). Reads are
    // TOLERANT (0 when the entity is gone / lacks it) — render code must not
    // fail-loud on a maybe-released ref, unlike the strict hp/cell accessors.
    public float renderX(long id) { return entityWorld.getFloat(id, components.RENDER_POSITION, BattleComponents.RENDER_POSITION_X, 0f); }
    public float renderY(long id) { return entityWorld.getFloat(id, components.RENDER_POSITION, BattleComponents.RENDER_POSITION_Y, 0f); }
    public void setRenderPos(long id, float x, float y) {
        entityWorld.setFloat(id, components.RENDER_POSITION, BattleComponents.RENDER_POSITION_X, x);
        entityWorld.setFloat(id, components.RENDER_POSITION, BattleComponents.RENDER_POSITION_Y, y);
    }
    public void setRenderX(long id, float v) { entityWorld.setFloat(id, components.RENDER_POSITION, BattleComponents.RENDER_POSITION_X, v); }
    public void setRenderY(long id, float v) { entityWorld.setFloat(id, components.RENDER_POSITION, BattleComponents.RENDER_POSITION_Y, v); }

    // Combat lives in the entity world's OPTIONAL COMBAT component, narrowed to
    // combatants: a non-combatant (civilian/engineer/scientist) carries none.
    // hasCombat is the presence check; the field accessors below are fail-loud on a
    // unit that lacks COMBAT (a non-combatant, or once the death drain has
    // transmuted the entity to a corpse — COMBAT gone). A caller that can see a
    // non-combatant id MUST gate on hasCombat / u.type.combatant first.
    public boolean hasCombat(long id) { return combat.has(id); }
    public float cooldownTimer(long id) { return combat.cooldownTimer(id); }
    public void setCooldownTimer(long id, float v) { combat.setCooldownTimer(id, v); }
    /** Per-unit primary cooldown reset value (seed-only stat); {@code setCooldownTimer(id, attackCooldown(id))} on a fire. */
    public float attackCooldown(long id) { return combat.attackCooldown(id); }

    // Movement lives in the entity world's OPTIONAL MOVEMENT component, narrowed
    // to movers: a static emplacement (turret, drone hub) has no MOVEMENT.
    // hasMovement is the presence check; the field accessors are fail-loud on a
    // unit that lacks it (and once the death drain has transmuted to a corpse).
    public boolean hasMovement(long id) { return movement.has(id); }
    public float moveProgress(long id) { return movement.moveProgress(id); }
    public void setMoveProgress(long id, float v) { movement.setMoveProgress(id, v); }
    /** Per-unit movement speed in cells/sec (seed-only mover stat). Fail-loud on a non-mover; gate on {@link #hasMovement}. */
    public float moveSpeed(long id) { return movement.moveSpeed(id); }

    // The path reference + cursor live in the MOVEMENT component too. setPathRef
    // is the raw column write; the occupancy-bookkeeping path change goes through
    // BattleControl.setPath (NavigationService), which calls this under the hood.
    public int[] path(long id) { return movement.path(id); }
    public void setPathRef(long id, int[] p) { movement.setPathRef(id, p); }
    public int pathIdx(long id) { return movement.pathIdx(id); }
    public void setPathIdx(long id, int v) { movement.setPathIdx(id, v); }

    public float attackDamage(long id) { return combat.attackDamage(id); }
    public void setAttackDamage(long id, float v) { combat.setAttackDamage(id, v); }

    public float attackRange(long id) { return combat.attackRange(id); }
    public void setAttackRange(long id, float v) { combat.setAttackRange(id, v); }

    public float accuracy(long id) { return combat.accuracy(id); }
    public void setAccuracy(long id, float v) { combat.setAccuracy(id, v); }

    public long targetId(long id) { return combat.targetId(id); }
    public void setTargetId(long id, long v) { combat.setTargetId(id, v); }

    public int burstRemaining(long id) { return combat.burstRemaining(id); }
    public void setBurstRemaining(long id, int v) { combat.setBurstRemaining(id, v); }

    public float burstTimer(long id) { return combat.burstTimer(id); }
    public void setBurstTimer(long id, float v) { combat.setBurstTimer(id, v); }

    public long burstTargetId(long id) { return combat.burstTargetId(id); }
    public void setBurstTargetId(long id, long v) { combat.setBurstTargetId(id, v); }

    // Secondary weapon is an OPTIONAL capability living in the world's
    // SECONDARY_WEAPON component. hasSecondaryWeapon is the presence check that
    // replaces the old `secondaryWeapon != null`; every other accessor is
    // fail-loud on a unit that lacks the component, so callers MUST gate on
    // hasSecondaryWeapon first.
    public boolean hasSecondaryWeapon(long id) { return entityWorld.has(id, components.SECONDARY_WEAPON); }
    public MarineSecondary secondaryWeapon(long id) { return (MarineSecondary) entityWorld.getObject(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_SPEC); }
    public int secondaryAmmo(long id) { return entityWorld.getInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AMMO); }
    public void setSecondaryAmmo(long id, int v) { entityWorld.setInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AMMO, v); }

    public float secondaryCooldownTimer(long id) { return entityWorld.getFloat(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_COOLDOWN_TIMER); }
    public void setSecondaryCooldownTimer(long id, float v) { entityWorld.setFloat(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_COOLDOWN_TIMER, v); }

    public float secondaryActionTimer(long id) { return entityWorld.getFloat(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_ACTION_TIMER); }
    public void setSecondaryActionTimer(long id, float v) { entityWorld.setFloat(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_ACTION_TIMER, v); }

    public long secondaryAimTargetId(long id) { return entityWorld.getLong(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AIM_TARGET_ID); }
    public void setSecondaryAimTargetId(long id, long v) { entityWorld.setLong(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AIM_TARGET_ID, v); }

    public boolean secondaryFired(long id) { return entityWorld.getInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_FIRED) != 0; }
    public void setSecondaryFired(long id, boolean v) { entityWorld.setInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_FIRED, v ? 1 : 0); }

    /** Grant the secondary capability to a live unit at runtime (archetype row-move). Serial-only — never mid-{@code Query} walk. */
    public void attachSecondaryWeapon(long id, MarineSecondary spec, int ammo) {
        entityWorld.addComponent(id, components.SECONDARY_WEAPON);
        entityWorld.setObject(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_SPEC, spec);
        entityWorld.setInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AMMO, ammo);
    }

    // AI-cadence state lives in the entity world's OPTIONAL AI_STATE component,
    // narrowed to thinkers: a static emplacement (turret, drone hub) has no
    // decision cadence. hasAiState is the presence check; the field accessors are
    // fail-loud on a unit that lacks it (and once the death drain has transmuted
    // to a corpse).
    public boolean hasAiState(long id) { return entityWorld.has(id, components.AI_STATE); }
    public float repositionCooldown(long id) { return entityWorld.getFloat(id, components.AI_STATE, BattleComponents.AI_STATE_REPOSITION_COOLDOWN); }
    public void setRepositionCooldown(long id, float v) { entityWorld.setFloat(id, components.AI_STATE, BattleComponents.AI_STATE_REPOSITION_COOLDOWN, v); }

    public float fallbackTimer(long id) { return entityWorld.getFloat(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_TIMER); }
    public void setFallbackTimer(long id, float v) { entityWorld.setFloat(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_TIMER, v); }

    public int fallbackCellX(long id) { return entityWorld.getInt(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_CELL_X); }
    public int fallbackCellY(long id) { return entityWorld.getInt(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_CELL_Y); }
    public void setFallbackCell(long id, int x, int y) {
        entityWorld.setInt(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_CELL_X, x);
        entityWorld.setInt(id, components.AI_STATE, BattleComponents.AI_STATE_FALLBACK_CELL_Y, y);
    }

    public float wanderDwellTimer(long id) { return entityWorld.getFloat(id, components.AI_STATE, BattleComponents.AI_STATE_WANDER_DWELL_TIMER); }
    public void setWanderDwellTimer(long id, float v) { entityWorld.setFloat(id, components.AI_STATE, BattleComponents.AI_STATE_WANDER_DWELL_TIMER, v); }

    // Mech loadout is an OPTIONAL capability in the world's MECH_LOADOUT component
    // (one OBJECT column holding the MechLoadoutComponent state bag) — presence IS
    // "is a mech". mechLoadout returns null when absent (so the scattered
    // `m == null` decide-phase reads keep working); attachMechLoadout is the
    // spawn-time grant; the dead mech keeps the component until the wreck handler
    // detaches it. The live-mech bulk fire pass walks the MECH_LOADOUT query, not
    // these by-id accessors.
    public boolean hasMechLoadout(long id) { return entityWorld.has(id, components.MECH_LOADOUT); }
    public MechLoadoutComponent mechLoadout(long id) {
        return entityWorld.has(id, components.MECH_LOADOUT)
                ? (MechLoadoutComponent) entityWorld.getObject(id, components.MECH_LOADOUT, BattleComponents.MECH_LOADOUT_STATE)
                : null;
    }
    /** Grant the mech-loadout capability at spawn (archetype row-move). Serial-only — never mid-{@code Query} walk. */
    public void attachMechLoadout(long id, MechLoadoutComponent loadout) {
        entityWorld.addComponent(id, components.MECH_LOADOUT);
        entityWorld.setObject(id, components.MECH_LOADOUT, BattleComponents.MECH_LOADOUT_STATE, loadout);
    }
    /** Detach the loadout when the wreck spawns (a {@code removeComponent} row-move back to a plain corpse). Serial-only. */
    public void removeMechLoadout(long id) { entityWorld.removeComponent(id, components.MECH_LOADOUT); }

    // ---- air craft (the air-into-world epic) ----
    //
    // Air entities are world-resident but NOT in the dense ground roster; their
    // archetype {AIR_IDENTITY, KINEMATICS, SHUTTLE_MISSION} (+ optional
    // THRUSTER_FX/AIR_TURRETS) carries no grid/combat components, so every grid
    // system skips them for free. Object reads here are has-gated null-returning
    // (EntityWorld.getObject THROWS on an absent id, unlike a ComponentStore.get)
    // so a render/audio frame straddling a despawn never throws. The set*
    // seeders write a column already present from createEntity (the air spawn
    // archetype); the attach*/remove* pairs add/drop the OPTIONAL capabilities.

    /** The flier's continuous-position {@link AirBody}, or null if the entity has no KINEMATICS. */
    public boolean hasKinematics(long id) { return entityWorld.has(id, components.KINEMATICS); }
    public AirBody kinematics(long id) {
        return entityWorld.has(id, components.KINEMATICS)
                ? (AirBody) entityWorld.getObject(id, components.KINEMATICS, BattleComponents.KINEMATICS_BODY)
                : null;
    }
    /** Seed KINEMATICS on an entity that already carries it (the air spawn archetype). */
    public void setKinematics(long id, AirBody body) { entityWorld.setObject(id, components.KINEMATICS, BattleComponents.KINEMATICS_BODY, body); }
    /** Grant KINEMATICS to an entity that lacks it — an {@code addComponent} row-move (a drone gaining a body). Serial-only. */
    public void attachKinematics(long id, AirBody body) {
        entityWorld.addComponent(id, components.KINEMATICS);
        entityWorld.setObject(id, components.KINEMATICS, BattleComponents.KINEMATICS_BODY, body);
    }

    // has-gated null-returning like the rest of the air surface (kinematics/mission/…)
    // so a held id read after the craft's GONE-destroy answers null instead of
    // throwing — a live craft always has AIR_IDENTITY, so this never hides a real bug.
    public ShuttleType airType(long id) {
        return entityWorld.has(id, components.AIR_IDENTITY)
                ? (ShuttleType) entityWorld.getObject(id, components.AIR_IDENTITY, BattleComponents.AIR_IDENTITY_TYPE)
                : null;
    }
    public Faction airFaction(long id) {
        return entityWorld.has(id, components.AIR_IDENTITY)
                ? (Faction) entityWorld.getObject(id, components.AIR_IDENTITY, BattleComponents.AIR_IDENTITY_FACTION)
                : null;
    }
    /** Seed AIR_IDENTITY (present from the air spawn archetype). */
    public void setAirIdentity(long id, ShuttleType type, Faction faction) {
        entityWorld.setObject(id, components.AIR_IDENTITY, BattleComponents.AIR_IDENTITY_TYPE, type);
        entityWorld.setObject(id, components.AIR_IDENTITY, BattleComponents.AIR_IDENTITY_FACTION, faction);
    }

    /** The shuttle's {@link ShuttleMission} bag, or null if absent. */
    public ShuttleMission mission(long id) {
        return entityWorld.has(id, components.SHUTTLE_MISSION)
                ? (ShuttleMission) entityWorld.getObject(id, components.SHUTTLE_MISSION, BattleComponents.SHUTTLE_MISSION_STATE)
                : null;
    }
    /** Seed SHUTTLE_MISSION (present from the air spawn archetype). */
    public void setMission(long id, ShuttleMission mission) { entityWorld.setObject(id, components.SHUTTLE_MISSION, BattleComponents.SHUTTLE_MISSION_STATE, mission); }

    public boolean hasThrusterFx(long id) { return entityWorld.has(id, components.THRUSTER_FX); }
    public ThrusterFx thrusterFx(long id) {
        return entityWorld.has(id, components.THRUSTER_FX)
                ? (ThrusterFx) entityWorld.getObject(id, components.THRUSTER_FX, BattleComponents.THRUSTER_FX_STATE)
                : null;
    }
    /** Grant THRUSTER_FX (lazy attach by ThrusterFxSystem). Serial-only. */
    public void attachThrusterFx(long id, ThrusterFx fx) {
        entityWorld.addComponent(id, components.THRUSTER_FX);
        entityWorld.setObject(id, components.THRUSTER_FX, BattleComponents.THRUSTER_FX_STATE, fx);
    }
    public void removeThrusterFx(long id) { entityWorld.removeComponent(id, components.THRUSTER_FX); }

    public boolean hasAirTurrets(long id) { return entityWorld.has(id, components.AIR_TURRETS); }
    public AirTurrets airTurrets(long id) {
        return entityWorld.has(id, components.AIR_TURRETS)
                ? (AirTurrets) entityWorld.getObject(id, components.AIR_TURRETS, BattleComponents.AIR_TURRETS_STATE)
                : null;
    }
    /** Grant AIR_TURRETS ("armed") at setup. Serial-only. */
    public void attachAirTurrets(long id, AirTurrets turrets) {
        entityWorld.addComponent(id, components.AIR_TURRETS);
        entityWorld.setObject(id, components.AIR_TURRETS, BattleComponents.AIR_TURRETS_STATE, turrets);
    }
    public void removeAirTurrets(long id) { entityWorld.removeComponent(id, components.AIR_TURRETS); }

    // Authored air render-state (APPEARANCE) — altitudeT + flightPhase FLOAT
    // columns, part of the air spawn archetype. Reads are TOLERANT (0 when the
    // entity is gone / lacks the component) so a render/audio frame straddling a
    // despawn never throws, mirroring renderX/renderY; the derived scaleMult /
    // altitude offset / engine intensity are pure functions of these two, computed
    // by AirAppearance (not stored).
    public boolean hasAppearance(long id) { return entityWorld.has(id, components.APPEARANCE); }
    public float altitudeT(long id) { return entityWorld.getFloat(id, components.APPEARANCE, BattleComponents.APPEARANCE_ALTITUDE_T, 0f); }
    public void setAltitudeT(long id, float v) { entityWorld.setFloat(id, components.APPEARANCE, BattleComponents.APPEARANCE_ALTITUDE_T, v); }
    public float flightPhase(long id) { return entityWorld.getFloat(id, components.APPEARANCE, BattleComponents.APPEARANCE_FLIGHT_PHASE, 0f); }
    public void setFlightPhase(long id, float v) { entityWorld.setFloat(id, components.APPEARANCE, BattleComponents.APPEARANCE_FLIGHT_PHASE, v); }
}
