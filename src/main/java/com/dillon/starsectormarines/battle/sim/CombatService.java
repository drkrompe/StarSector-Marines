package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.FireGate;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Data owner for the {@code COMBAT} component — typed by-id access (read + mutate)
 * to a combatant's primary-weapon state in the archetype {@link EntityWorld}.
 *
 * <p>A <b>Service</b> in this codebase's sense (see
 * {@code ecs-nouns.md}): it <em>owns</em>
 * a component's data and exposes the methods to read/modify it — distinct from a
 * per-tick <b>System</b>, which processes every entity matching an aspect by
 * column-walking. A consumer that needs combat state is constructor-injected with
 * this Service (or reaches it via {@code sim.combat()} / {@code roster.combat()})
 * and calls {@code combat.attackCooldown(id)} directly — no {@link World} hop. This
 * is the random-access / held-ref path; per-tick bulk systems column-walk the
 * COMBAT table instead.
 *
 * <p>{@code COMBAT} is OPTIONAL (combatant-narrowed): {@link #has} is the presence
 * check; every field accessor is <b>fail-loud</b> on a unit that lacks it (a
 * non-combatant, or a corpse once the death drain transmuted it away). Gate on
 * {@link #has} (or {@code u.type.combatant}) before any field read.
 *
 * <p>Part of the {@link World} decomposition: World now delegates its COMBAT
 * accessors here, so the flat facade is no longer the data owner. Serial-only.
 */
public final class CombatService {

    private final EntityWorld entityWorld;
    private final BattleComponents components;

    public CombatService(EntityWorld entityWorld, BattleComponents components) {
        this.entityWorld = entityWorld;
        this.components = components;
    }

    /** Presence check — true iff {@code id} carries COMBAT (is a live combatant). Gate field reads on this. */
    public boolean has(long id) { return entityWorld.has(id, components.COMBAT); }

    public float attackDamage(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_DAMAGE); }
    public void setAttackDamage(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_DAMAGE, v); }

    public float attackRange(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_RANGE); }
    public void setAttackRange(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_RANGE, v); }

    public float accuracy(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_ACCURACY); }
    public void setAccuracy(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ACCURACY, v); }

    public float cooldownTimer(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER); }
    public void setCooldownTimer(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_COOLDOWN_TIMER, v); }

    /** Per-unit primary cooldown reset value (seed-only stat); {@code setCooldownTimer(id, attackCooldown(id))} on a fire. */
    public float attackCooldown(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_COOLDOWN); }

    /** The primary handheld weapon flyweight, or {@code null} for a combatant with no per-weapon profile (militia/aliens/turrets fire off the baked attack stats). Seeded at allocate; assigned at deboard via {@link #setPrimaryWeapon}. */
    public WeaponDef primaryWeapon(long id) {
        return (WeaponDef) entityWorld.getObject(id, components.COMBAT,
                BattleComponents.COMBAT_PRIMARY_WEAPON);
    }

    /** Authoritative definition for built-in and contributed primary weapons. */
    public WeaponDef primaryWeaponDef(long id) {
        return primaryWeapon(id);
    }

    public void setPrimaryWeapon(long id, WeaponDef weapon) {
        entityWorld.setObject(id, components.COMBAT,
                BattleComponents.COMBAT_PRIMARY_WEAPON, weapon);
    }

    public void setPrimaryWeaponDef(long id, WeaponDef weapon) {
        setPrimaryWeapon(id, weapon);
    }

    public EquipmentGrade equipmentGrade(long id) {
        EquipmentGrade grade = (EquipmentGrade) entityWorld.getObject(id, components.COMBAT,
                BattleComponents.COMBAT_EQUIPMENT_GRADE);
        return grade != null ? grade : EquipmentGrade.SERVICE;
    }
    public void setEquipmentGrade(long id, EquipmentGrade grade) {
        entityWorld.setObject(id, components.COMBAT, BattleComponents.COMBAT_EQUIPMENT_GRADE,
                grade != null ? grade : EquipmentGrade.SERVICE);
        refreshTieredPrimaryStats(id);
    }

    public SoldierProfile soldierProfile(long id) {
        SoldierProfile profile = (SoldierProfile) entityWorld.getObject(id, components.COMBAT,
                BattleComponents.COMBAT_SOLDIER_PROFILE);
        return profile != null ? profile : SoldierProfile.REGULAR;
    }
    public void setSoldierProfile(long id, SoldierProfile profile) {
        entityWorld.setObject(id, components.COMBAT, BattleComponents.COMBAT_SOLDIER_PROFILE,
                profile != null ? profile : SoldierProfile.REGULAR);
        refreshTieredPrimaryStats(id);
    }

    /** Replaces the whole family × grade × soldier combination at runtime. */
    public void equipPrimaryWeapon(long id, WeaponDef weapon, EquipmentGrade grade,
                                   SoldierProfile profile) {
        setPrimaryWeapon(id, weapon);
        entityWorld.setObject(id, components.COMBAT, BattleComponents.COMBAT_EQUIPMENT_GRADE,
                grade != null ? grade : EquipmentGrade.SERVICE);
        entityWorld.setObject(id, components.COMBAT, BattleComponents.COMBAT_SOLDIER_PROFILE,
                profile != null ? profile : SoldierProfile.REGULAR);
        refreshTieredPrimaryStats(id);
    }

    private void refreshTieredPrimaryStats(long id) {
        WeaponDef weapon = primaryWeaponDef(id);
        if (weapon == null) return;
        EquipmentGrade grade = equipmentGrade(id);
        SoldierProfile profile = soldierProfile(id);
        setAttackRange(id, InfantryCombatStats.range(weapon, grade));
        setAttackDamage(id, InfantryCombatStats.damage(weapon, grade));
        setAccuracy(id, InfantryCombatStats.accuracy(weapon, grade, profile));
        entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_ATTACK_COOLDOWN,
                InfantryCombatStats.cooldown(weapon, grade, profile));
    }

    // ---- incoming fire ----
    //
    // "Somebody is shooting at me right now, and I can see where from." Written
    // by SquadAlertSystem's per-tick shot scan, which already gathers everyone
    // near each round's impact and tests line of sight back to the muzzle; this
    // keeps the individual and the bearing that scan used to discard.

    /**
     * Sim-seconds for an unrepeated round's contribution to fall to half. Two
     * seconds is short enough that a stray shot has faded before the next
     * decision and long enough that a sustained exchange accumulates rather
     * than sawtoothing between rounds.
     *
     * <p>This is a property of the signal — how long being shot at stays true —
     * and so is shared by every consumer. What counts as <em>enough</em>
     * incoming to act on is a judgement about the actor and is authored there.
     */
    public static final float INCOMING_PRESSURE_HALF_LIFE_SECONDS = 2f;


    /**
     * Records one round landing near {@code id} from a hostile it can see, at
     * cell {@code (fromCellX, fromCellY)}.
     *
     * <p>Decays what was already there to {@code simTick} before adding, so the
     * stored value is always "pressure as of its own tick" and a long quiet
     * stretch costs nothing to skip.
     */
    public void recordIncomingFire(long id, int fromCellX, int fromCellY, int simTick) {
        if (!has(id)) return;
        float decayed = incomingPressure(id, simTick);
        entityWorld.setFloat(id, components.COMBAT,
                BattleComponents.COMBAT_INCOMING_PRESSURE, decayed + 1f);
        entityWorld.setInt(id, components.COMBAT,
                BattleComponents.COMBAT_INCOMING_PRESSURE_TICK, simTick);
        entityWorld.setInt(id, components.COMBAT,
                BattleComponents.COMBAT_INCOMING_FROM_X, fromCellX);
        entityWorld.setInt(id, components.COMBAT,
                BattleComponents.COMBAT_INCOMING_FROM_Y, fromCellY);
    }

    /**
     * How much fire {@code id} is under as of {@code simTick}: roughly the
     * number of rounds that have landed near it within the last couple of
     * seconds, from shooters it can see. Zero for a combatant nobody is
     * shooting at, and for a non-combatant.
     */
    public float incomingPressure(long id, int simTick) {
        if (!has(id)) return 0f;
        float stored = entityWorld.getFloat(id, components.COMBAT,
                BattleComponents.COMBAT_INCOMING_PRESSURE);
        if (stored <= 0f) return 0f;
        int elapsedTicks = simTick - entityWorld.getInt(id, components.COMBAT,
                BattleComponents.COMBAT_INCOMING_PRESSURE_TICK);
        if (elapsedTicks <= 0) return stored;
        float elapsedSeconds = elapsedTicks * BattleSimulation.TICK_DT;
        return stored * (float) Math.pow(0.5, elapsedSeconds / INCOMING_PRESSURE_HALF_LIFE_SECONDS);
    }

    /**
     * Records that a hit landed on {@code id} at {@code simTick}. Unlike
     * {@link #recordIncomingFire}, this is damage that actually arrived and
     * carries no requirement that the target could see where it came from.
     */
    public void recordDamageTaken(long id, int simTick) {
        if (!has(id)) return;
        entityWorld.setInt(id, components.COMBAT,
                BattleComponents.COMBAT_LAST_DAMAGED_TICK, simTick + 1);
    }

    /**
     * Ticks since a hit last landed on {@code id}, or {@link Integer#MAX_VALUE}
     * if none ever has.
     */
    public int ticksSinceDamaged(long id, int simTick) {
        if (!has(id)) return Integer.MAX_VALUE;
        int stored = entityWorld.getInt(id, components.COMBAT,
                BattleComponents.COMBAT_LAST_DAMAGED_TICK);
        return stored == 0 ? Integer.MAX_VALUE : simTick - (stored - 1);
    }

    /** Cell x the most recent incoming round came from. Meaningless at zero pressure. */
    public int incomingFromX(long id) {
        return has(id) ? entityWorld.getInt(id, components.COMBAT,
                BattleComponents.COMBAT_INCOMING_FROM_X) : 0;
    }

    /** Cell y the most recent incoming round came from. Meaningless at zero pressure. */
    public int incomingFromY(long id) {
        return has(id) ? entityWorld.getInt(id, components.COMBAT,
                BattleComponents.COMBAT_INCOMING_FROM_Y) : 0;
    }

    public long targetId(long id) { return entityWorld.getLong(id, components.COMBAT, BattleComponents.COMBAT_TARGET_ID); }

    /**
     * Selects the unit's current threat and starts its experience-scaled
     * registration delay when that threat is new. Reasserting the same threat
     * is free, so per-tick target maintenance never restarts the clock.
     */
    public void setTargetId(long id, long v) {
        entityWorld.setLong(id, components.COMBAT, BattleComponents.COMBAT_TARGET_ID, v);
        registerThreat(id, v);
    }

    public long reflexTargetId(long id) {
        return entityWorld.getLong(id, components.COMBAT, BattleComponents.COMBAT_REFLEX_TARGET_ID);
    }

    public void setReflexTargetId(long id, long v) {
        entityWorld.setLong(id, components.COMBAT, BattleComponents.COMBAT_REFLEX_TARGET_ID, v);
    }

    public float reflexTimer(long id) {
        return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_REFLEX_TIMER);
    }

    public void setReflexTimer(long id, float v) {
        entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_REFLEX_TIMER,
                Math.max(0f, v));
    }

    public FireGate lastFireGate(long id) {
        int ordinal = entityWorld.getInt(id, components.COMBAT,
                BattleComponents.COMBAT_LAST_FIRE_GATE);
        return ordinal >= 0 && ordinal < FireGate.VALUES.length
                ? FireGate.VALUES[ordinal] : FireGate.NONE;
    }

    public int lastFireGateTick(long id) {
        return entityWorld.getInt(id, components.COMBAT,
                BattleComponents.COMBAT_LAST_FIRE_GATE_TICK);
    }

    /**
     * Observes a threat without changing the unit's pursuit target. This is
     * the opportunity-fire seam: a passing target still has to be registered,
     * but it does not pull the unit away from its current objective.
     */
    public void registerThreat(long id, long threatId) {
        if (!usesInfantryTraining(id)) return;
        if (reflexTargetId(id) == threatId) return;
        setReflexTargetId(id, threatId);
        setReflexTimer(id, threatId == 0L
                ? 0f
                : soldierProfile(id).experienceTier().reflexDelaySeconds);
    }

    private boolean usesInfantryTraining(long id) {
        UnitType type = (UnitType) entityWorld.getObject(id, components.IDENTITY,
                BattleComponents.IDENTITY_TYPE);
        return type != null && type.usesInfantryTraining();
    }

    public int burstRemaining(long id) { return entityWorld.getInt(id, components.COMBAT, BattleComponents.COMBAT_BURST_REMAINING); }
    public void setBurstRemaining(long id, int v) { entityWorld.setInt(id, components.COMBAT, BattleComponents.COMBAT_BURST_REMAINING, v); }

    public float burstTimer(long id) { return entityWorld.getFloat(id, components.COMBAT, BattleComponents.COMBAT_BURST_TIMER); }
    public void setBurstTimer(long id, float v) { entityWorld.setFloat(id, components.COMBAT, BattleComponents.COMBAT_BURST_TIMER, v); }

    public long burstTargetId(long id) { return entityWorld.getLong(id, components.COMBAT, BattleComponents.COMBAT_BURST_TARGET_ID); }
    public void setBurstTargetId(long id, long v) { entityWorld.setLong(id, components.COMBAT, BattleComponents.COMBAT_BURST_TARGET_ID, v); }

    /**
     * Writes the consume-once fire-intent a behavior queues instead of firing
     * inline: {@code targetId} to shoot this tick, the {@code stance} for the
     * shot (stored as its ordinal), and whether a successful fire should
     * chain into {@code RepositionToCover} same-tick. {@code
     * setFireIntent} also registers the target as an observed threat so
     * opportunistic fire that deliberately leaves the pursuit target alone
     * still obeys the reflex passive. {@code
     * battle.combat.FiringSystem} is the sole reader; it clears {@link
     * #fireTargetId} every tick whether or not it fired.
     */
    public void setFireIntent(long id, long targetId, FireStance stance, boolean repositionAfter) {
        registerThreat(id, targetId);
        entityWorld.setObject(id, components.COMBAT, BattleComponents.COMBAT_POINT_FIRE_AIM, null);
        entityWorld.setLong(id, components.COMBAT, BattleComponents.COMBAT_FIRE_TARGET_ID, targetId);
        entityWorld.setInt(id, components.COMBAT, BattleComponents.COMBAT_FIRE_STANCE, stance.ordinal());
        entityWorld.setInt(id, components.COMBAT, BattleComponents.COMBAT_FIRE_REPOSITION, repositionAfter ? 1 : 0);
    }

    /**
     * The consume-once fire-intent target, {@code 0L} = no intent (hold
     * fire). {@code fireStance}/{@code fireReposition} have no standalone
     * accessor — {@code battle.combat.FiringSystem} is the sole reader of
     * all three intent columns and reads them directly off the archetype
     * table during its column-walk, one-caller-rule style.
     */
    public long fireTargetId(long id) { return entityWorld.getLong(id, components.COMBAT, BattleComponents.COMBAT_FIRE_TARGET_ID); }

    /**
     * Queue the burst follow-up rounds after the AI has already fired round 1.
     * No-op for single-shot weapons or combatants without a primary-weapon profile
     * (militia / aliens / turrets — those use their own burst paths or are
     * intrinsically single-shot). Centralizes the trigger pattern so every
     * fireShot callsite — stanced, moving, opportunity, garrison — gets bursts
     * consistently. Everything it touches is COMBAT — the primary-weapon profile
     * read and all three burst-column writes. Called at most once per shot per unit
     * (not a per-tick bulk path), so the by-id probes are fine. {@code targetId} is
     * the shot's target id ({@code 0L} = none); rehomed from {@code Entity.beginBurst}
     * (identity-collapse Phase A).
     */
    public void beginBurst(long shooterId, long targetId) {
        setBurstPointAim(shooterId, null);
        WeaponDef weapon = primaryWeaponDef(shooterId);
        if (weapon == null || weapon.burstCount <= 1) return;
        setBurstRemaining(shooterId, weapon.burstCount - 1);
        setBurstTimer(shooterId, weapon.burstSpacing);
        setBurstTargetId(shooterId, targetId);
    }

    /** Queue a consume-once bearing without registering or revealing a target. */
    public void setPointFireIntent(long id, PointFireAim aim, FireStance stance) {
        entityWorld.setLong(id, components.COMBAT, BattleComponents.COMBAT_FIRE_TARGET_ID, 0L);
        entityWorld.setObject(id, components.COMBAT, BattleComponents.COMBAT_POINT_FIRE_AIM, aim);
        entityWorld.setInt(id, components.COMBAT, BattleComponents.COMBAT_FIRE_STANCE, stance.ordinal());
        entityWorld.setInt(id, components.COMBAT, BattleComponents.COMBAT_FIRE_REPOSITION, 0);
    }

    public PointFireAim pointFireAim(long id) {
        return (PointFireAim) entityWorld.getObject(id, components.COMBAT, BattleComponents.COMBAT_POINT_FIRE_AIM);
    }

    public PointFireAim burstPointAim(long id) {
        return (PointFireAim) entityWorld.getObject(id, components.COMBAT, BattleComponents.COMBAT_BURST_POINT_AIM);
    }

    public void setBurstPointAim(long id, PointFireAim aim) {
        entityWorld.setObject(id, components.COMBAT, BattleComponents.COMBAT_BURST_POINT_AIM, aim);
    }

    /** Follow-up rounds retain the trigger's world point, with no target tracking. */
    public void beginPointBurst(long id, PointFireAim aim) {
        beginBurst(id, 0L);
        if (burstRemaining(id) > 0) setBurstPointAim(id, aim);
    }

    /** Cancel queued primary work when control changes hands or becomes ineligible. */
    public void clearPrimaryFire(long id) {
        setPointFireIntent(id, null, FireStance.STANCED);
        setBurstRemaining(id, 0);
        setBurstTimer(id, 0f);
        setBurstTargetId(id, 0L);
        setBurstPointAim(id, null);
    }

}
