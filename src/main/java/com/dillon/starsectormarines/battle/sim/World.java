package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirTurrets;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.engine.ThrusterFx;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.SmokeThrowCommit;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Entity-access facade over the battle's component storage. The entity is its
 * {@code long} id; irregular consumers reach state <em>by id</em> through this
 * receiver or a narrower component service rather than holding an object handle.
 * See {@code ecs-nouns.md}.
 *
 * <p><b>By-id accessors</b> ({@link #hp}/{@code setHp}, cell, combat, movement, …)
 * read the archetype {@link EntityWorld}'s component columns directly by id — one
 * location probe + column read, <b>zero object construction</b>. This is the broad
 * by-id facade. Mandatory columns (hp/cell) are
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
 * <b>tolerant</b> center-based position reads for render/audio paths — equal
 * to {@link #x}/{@link #y} for live units, the last position for corpses
 * ({@code POSITION} survives the death transmute so a corpse still draws
 * where it fell), and 0 for a fully released id.
 *
 * <p>Serial-only — built for the single-threaded tick + render read.
 */
public final class World {

    private final EntityWorld entityWorld;
    private final BattleComponents components;
    // COMBAT and MOVEMENT operations delegate to their component Services, which
    // own the field invariants. New focused consumers should inject those owners
    // directly; World remains the broad irregular-access boundary.
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
    public float damageTakenMult(long id) { return entityWorld.getFloat(id, components.HEALTH, BattleComponents.HEALTH_DAMAGE_TAKEN_MULT); }
    public float incomingAccuracyMult(long id) { return entityWorld.getFloat(id, components.HEALTH, BattleComponents.HEALTH_INCOMING_ACCURACY_MULT); }

    // Armor is an OPTIONAL, live-only capability. Presence means the actor was
    // authored with armor even when the current capacity has reached zero; armorless
    // actors omit it. All field reads are fail-loud without the component, so a
    // maybe-armored caller must gate on hasArmor first.
    public boolean hasArmor(long id) { return entityWorld.has(id, components.ARMOR); }
    public float armor(long id) { return entityWorld.getFloat(id, components.ARMOR, BattleComponents.ARMOR_CURRENT); }
    public void setArmor(long id, float v) { entityWorld.setFloat(id, components.ARMOR, BattleComponents.ARMOR_CURRENT, v); }
    public float maxArmor(long id) { return entityWorld.getFloat(id, components.ARMOR, BattleComponents.ARMOR_MAX); }
    public void setMaxArmor(long id, float v) { entityWorld.setFloat(id, components.ARMOR, BattleComponents.ARMOR_MAX, v); }
    public float armorRating(long id) { return entityWorld.getFloat(id, components.ARMOR, BattleComponents.ARMOR_RATING); }
    public void setArmorRating(long id, float v) { entityWorld.setFloat(id, components.ARMOR, BattleComponents.ARMOR_RATING, v); }

    // The continuous position lives in the entity world's POSITION columns.
    // POSITION persists alive→dead, so a corpse still answers position reads.
    // x/y are the authoritative float coordinates in cell space (cell (cx,cy)
    // spans [cx,cx+1), center at cx+0.5); cellX/cellY are the derived grid cell
    // (floor) for nav/LoS/fog lookups, kept as int-returning accessors so the
    // existing cell-space call sites compile unchanged.
    /**
     * Whether {@code id} stands on a grid cell of its own. False for the bodies
     * that carry their own kinematics instead — a convoy chassis, an aircraft —
     * which is exactly what a caller about to <em>write</em> a cell has to know:
     * they answer {@link #x}/{@link #y} off their body and there is no cell
     * column to put anything back into.
     */
    public boolean hasPosition(long id) { return entityWorld.has(id, components.POSITION); }

    public float x(long id) {
        // One location probe rather than has()+getFloat()'s two: this is the
        // most-read accessor in the sim and the pair showed up in a profile.
        // NaN is the sentinel because no live position can be one.
        float px = entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_X, Float.NaN);
        if (!Float.isNaN(px)) return px;
        GroundBody body = groundBody(id);
        if (body != null) return body.x;
        // POSITION is checked above, so a drone — which carries both a cell and
        // a body — never reaches this and keeps answering off its cell.
        AirBody flier = kinematics(id);
        if (flier != null) return flier.x;
        return entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_X);
    }
    public float y(long id) {
        float py = entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_Y, Float.NaN);
        if (!Float.isNaN(py)) return py;
        GroundBody body = groundBody(id);
        if (body != null) return body.y;
        AirBody flier = kinematics(id);
        if (flier != null) return flier.y;
        return entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_Y);
    }
    public void setPos(long id, float x, float y) {
        entityWorld.setFloat(id, components.POSITION, BattleComponents.POSITION_X, x);
        entityWorld.setFloat(id, components.POSITION, BattleComponents.POSITION_Y, y);
    }

    public int cellX(long id) { return (int) Math.floor(x(id)); }
    public int cellY(long id) { return (int) Math.floor(y(id)); }
    /** Convenience for spawn/nav code that thinks in cells — writes the cell center {@code (cx + 0.5, cy + 0.5)}. */
    public void setCellPos(long id, int x, int y) {
        setPos(id, x + 0.5f, y + 0.5f);
    }

    // Smooth render position — tolerant reads of the world's universal POSITION
    // component (survives the death transmute, so a corpse draws where it
    // fell). Reads are TOLERANT (0 when the entity is gone / lacks it) —
    // render code must not fail-loud on a maybe-released ref, unlike the
    // strict hp/cell accessors. Center-based, same as x()/y().
    public float renderX(long id) {
        GroundBody body = groundBody(id);
        if (body != null) return body.x;
        AirBody flier = flierWithoutACell(id);
        if (flier != null) return flier.x;
        return entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_X, 0f);
    }
    public float renderY(long id) {
        GroundBody body = groundBody(id);
        if (body != null) return body.y;
        AirBody flier = flierWithoutACell(id);
        if (flier != null) return flier.y;
        return entityWorld.getFloat(id, components.POSITION, BattleComponents.POSITION_Y, 0f);
    }

    /**
     * The kinematic body of a craft that has no grid cell of its own — an
     * aircraft, which is a body and nothing else.
     *
     * <p>Gated on the absence of {@code POSITION} rather than on the presence of
     * {@code KINEMATICS}, because a drone carries both and its cell is the
     * authority the rest of the grid stack already agrees with; answering off
     * its body here would quietly put two positions in play for one actor.
     */
    private AirBody flierWithoutACell(long id) {
        return entityWorld.has(id, components.POSITION) ? null : kinematics(id);
    }

    private GroundBody groundBody(long id) {
        return entityWorld.has(id, components.GROUND_KINEMATICS)
                ? (GroundBody) entityWorld.getObject(id, components.GROUND_KINEMATICS,
                    BattleComponents.GROUND_KINEMATICS_BODY)
                : null;
    }

    // Modular appearance is an OPTIONAL, live-only presentation capability.
    // Body and helmet selectors are intentionally independent equipment writes.
    public boolean hasLayeredAppearance(long id) {
        return entityWorld.has(id, components.LAYERED_ANIMATION);
    }
    public LayeredArmorFamily layeredBodyFamily(long id) {
        return LayeredArmorFamily.fromOrdinal(entityWorld.getInt(id, components.LAYERED_ANIMATION,
                BattleComponents.LAYERED_BODY_FAMILY));
    }
    public LayeredArmorFamily layeredHeadFamily(long id) {
        return LayeredArmorFamily.fromOrdinal(entityWorld.getInt(id, components.LAYERED_ANIMATION,
                BattleComponents.LAYERED_HEAD_FAMILY));
    }
    public void setLayeredBodyFamily(long id, LayeredArmorFamily family) {
        entityWorld.setInt(id, components.LAYERED_ANIMATION,
                BattleComponents.LAYERED_BODY_FAMILY, family.ordinal());
    }
    public void setLayeredHeadFamily(long id, LayeredArmorFamily family) {
        entityWorld.setInt(id, components.LAYERED_ANIMATION,
                BattleComponents.LAYERED_HEAD_FAMILY, family.ordinal());
    }

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

    // Special equipment is an OPTIONAL capability living in the world's
    // SECONDARY_WEAPON component. hasSecondaryWeapon is the compatibility-named
    // presence check; every other accessor is
    // fail-loud on a unit that lacks the component, so callers MUST gate on
    // hasSecondaryWeapon first.
    public boolean hasSecondaryWeapon(long id) { return entityWorld.has(id, components.SECONDARY_WEAPON); }
    public SpecialEquipmentDef specialEquipment(long id) {
        Object value = entityWorld.getObject(id, components.SECONDARY_WEAPON,
                BattleComponents.SECONDARY_WEAPON_SPEC);
        return value instanceof SpecialEquipmentDef def ? def : null;
    }
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

    /** The accepted smoke channel, independent of the squad's tactical reservation. */
    public SmokeThrowCommit smokeThrowCommit(long id) {
        return hasSecondaryWeapon(id) ? (SmokeThrowCommit) entityWorld.getObject(id,
                components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_SMOKE_COMMIT) : null;
    }

    public void setSmokeThrowCommit(long id, SmokeThrowCommit commit) {
        entityWorld.setObject(id, components.SECONDARY_WEAPON,
                BattleComponents.SECONDARY_WEAPON_SMOKE_COMMIT, commit);
    }

    /** Grant the secondary capability to a live unit at runtime (archetype row-move). Serial-only — never mid-{@code Query} walk. */
    public void attachSpecialEquipment(long id, SpecialEquipmentDef spec, int ammo) {
        entityWorld.addComponent(id, components.SECONDARY_WEAPON);
        entityWorld.setObject(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_SPEC, spec);
        entityWorld.setInt(id, components.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_AMMO, ammo);
        setSmokeThrowCommit(id, null);
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

    /**
     * Name of the {@code battle.decision.Reflex} that pre-empted this unit's
     * plan step on the last tick it was dispatched, or {@code null} when it was
     * free to execute. Diagnostic only: written by {@code ReflexChain.run} and
     * read by the per-member dumps, never by the simulation.
     */
    public String lastReflex(long id) { return (String) entityWorld.getObject(id, components.AI_STATE, BattleComponents.AI_STATE_LAST_REFLEX); }
    public void setLastReflex(long id, String v) { entityWorld.setObject(id, components.AI_STATE, BattleComponents.AI_STATE_LAST_REFLEX, v); }

    /**
     * Sim-seconds left in a {@code battle.infantry.LaneSidestep} step-aside, or
     * {@code 0} when the unit is not stepping out of a squadmate's firing lane.
     * Drained by {@code InfantryUnitPrep.tickCooldowns} alongside the other
     * per-unit timers, and the marker that lets the reflex finish its own short
     * move on later ticks without also adopting the post-fire cover reposition,
     * which the reposition cooldown alone cannot distinguish it from.
     */
    public float sidestepTimer(long id) { return entityWorld.getFloat(id, components.AI_STATE, BattleComponents.AI_STATE_SIDESTEP_TIMER); }
    public void setSidestepTimer(long id, float v) { entityWorld.setFloat(id, components.AI_STATE, BattleComponents.AI_STATE_SIDESTEP_TIMER, v); }

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
    /** Current planted-hip bearing for a live mech. Fail-loud on a non-mech. */
    public float mechHipFacingDegrees(long id) {
        return entityWorld.getFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
    }
    /** Grant the mech-loadout capability at spawn (archetype row-move). Serial-only — never mid-{@code Query} walk. */
    public void attachMechLoadout(long id, MechLoadoutComponent loadout) {
        if (!entityWorld.has(id, components.MECH_LOADOUT)) {
            entityWorld.addComponent(id, components.MECH_LOADOUT);
        }
        entityWorld.setObject(id, components.MECH_LOADOUT, BattleComponents.MECH_LOADOUT_STATE, loadout);
        setAttackRange(id, loadout.maxWeaponRange());
        if (entityWorld.has(id, components.MECH_LAYERED_ANIMATION)) {
            entityWorld.setInt(id, components.MECH_LAYERED_ANIMATION,
                    BattleComponents.MECH_LAYERED_CHASSIS, loadout.variant.chassisAppearance);
            entityWorld.setInt(id, components.MECH_LAYERED_ANIMATION,
                    BattleComponents.MECH_LAYERED_ARMS,
                    loadout.appearanceSelector(MechMountSlot.ARMS));
            entityWorld.setInt(id, components.MECH_LAYERED_ANIMATION,
                    BattleComponents.MECH_LAYERED_LEFT_SHOULDER,
                    loadout.appearanceSelector(MechMountSlot.LEFT_SHOULDER));
            entityWorld.setInt(id, components.MECH_LAYERED_ANIMATION,
                    BattleComponents.MECH_LAYERED_RIGHT_SHOULDER,
                    loadout.appearanceSelector(MechMountSlot.RIGHT_SHOULDER));
        }
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
    public Airframe airframe(long id) {
        return entityWorld.has(id, components.AIR_IDENTITY)
                ? (Airframe) entityWorld.getObject(id, components.AIR_IDENTITY, BattleComponents.AIR_IDENTITY_TYPE)
                : null;
    }
    public Faction airFaction(long id) {
        return entityWorld.has(id, components.AIR_IDENTITY)
                ? (Faction) entityWorld.getObject(id, components.AIR_IDENTITY, BattleComponents.AIR_IDENTITY_FACTION)
                : null;
    }
    /** Seed AIR_IDENTITY (present from the air spawn archetype). */
    public void setAirIdentity(long id, Airframe type, Faction faction) {
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
