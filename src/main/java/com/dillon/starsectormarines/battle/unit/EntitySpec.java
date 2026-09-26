package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

/**
 * Construction spec for a ground-roster unit — the mutable bag of "what to spawn"
 * that {@code UnitRosterService.spawn} consumes to mint a world entity. It carries
 * the same data the {@code Entity} {@code seed*} fields used to (identity + cell +
 * the optional capability seeds + the stat block), so a spawn no longer needs a
 * hand-built {@code Entity} handle: the caller describes the unit, {@code spawn}
 * builds it.
 *
 * <p>The stat block ({@link #maxHp}, attack stats, {@link #moveSpeed},
 * {@link #visionRange}, {@link #attackCooldown}) is initialized from the
 * {@link UnitType} at construction — the same defaults the old {@code Entity} ctor
 * applied — and the fluent setters override individual values (a turret's per-kind
 * stats, a drone's tuned numbers, a deboard loadout's weapon-derived stats). Every
 * optional capability (squad, secondary weapon, kinematic body, home post,
 * objective, turret kind, hub cooldown, drone home-hub) defaults to "absent" and is
 * opted into by its setter — presence at spawn IS the archetype membership, exactly
 * as the seeds drove it.
 *
 * <p>Fluent: setters return {@code this} so a spawn reads as one expression.
 * Mirrors the {@code allocateAir}/{@code allocateVehicle} spec shape for the ground
 * roster. Part of identity-collapse Phase C (spawn-spec).
 */
public final class EntitySpec {

    // ---- identity (required) ----
    public final String name;
    public final Faction faction;
    public final UnitType type;
    public int cellX;
    public int cellY;
    /** Exact initial center; defaults to the requested cell center. */
    public float spawnX;
    public float spawnY;

    // ---- optional capability seeds (default = absent) ----
    public int squadId = Squad.NO_SQUAD;
    public int fireTeamIndex = Squad.NO_FIRE_TEAM;
    public UnitRole role = UnitRole.COMBATANT;
    /** Authoritative special-equipment definition. */
    public SpecialEquipmentDef specialEquipment;
    /** The capability the worn armour pattern declares, or null — most declare none. */
    public IntegralSystemDef integralSystem;
    public int secondaryAmmo;
    public AirBody body;
    /** Authoritative primary definition. */
    public WeaponDef primaryWeaponDef;
    public EquipmentGrade equipmentGrade = EquipmentGrade.SERVICE;
    public SoldierProfile soldierProfile = SoldierProfile.REGULAR;
    public String campaignSoldierId;
    /** Campaign squad this marine deployed with; null for generated units. */
    public String campaignSquadId;
    /** Persistent physical/loadout profile for mech-class entities; null otherwise. */
    public MechVariant mechVariant;
    /** Which aircraft this hull is, for a based aircraft; null otherwise. */
    public Airframe airframe;
    public LayeredArmorFamily layeredArmorFamily;
    public Objective assignedObjective;
    public int homeCellX = -1;
    public int homeCellY = -1;
    public float hubSpawnCooldown = 0f;
    public String turretStructureId;
    public long homeHubId = 0L;

    // ---- stat block (seeded from type, override via setters) ----
    public float moveSpeed;
    public float hp;
    public float maxHp;
    /** Optional live armor seed; {@code maxArmor == 0} means the capability is absent. */
    public float currentArmor;
    public float maxArmor;
    public float armorRating;
    public float attackDamage;
    public float attackRange;
    public float accuracy;
    public float visionRange;
    public float airLosRadius = 0f;
    public float attackCooldown;
    public float damageTakenMult = 1f;
    public float incomingAccuracyMult = 1f;

    public EntitySpec(String name, Faction faction, UnitType type, int cellX, int cellY) {
        this.name = name;
        this.faction = faction;
        this.type = type;
        this.cellX = cellX;
        this.cellY = cellY;
        this.spawnX = cellX + 0.5f;
        this.spawnY = cellY + 0.5f;
        // Same archetype-default seeding the Entity ctor applied.
        this.moveSpeed = type.moveSpeed;
        this.hp = type.maxHp;
        this.maxHp = type.maxHp;
        this.attackDamage = type.attackDamage;
        this.attackRange = type.attackRange;
        this.accuracy = type.accuracy;
        this.visionRange = type.visionRange > 0f ? type.visionRange : type.attackRange;
        this.attackCooldown = type.attackCooldown;
        if (type.isMech()) MechVariant.BULWARK.applyTo(this);
    }

    /** Sets an already validated position before adoption; never relocates a live body. */
    public EntitySpec atPosition(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Finite spawn position required");
        }
        spawnX = x;
        spawnY = y;
        cellX = (int) Math.floor(x);
        cellY = (int) Math.floor(y);
        return this;
    }

    public EntitySpec squad(int squadId) { this.squadId = squadId; return this; }
    public EntitySpec fireTeam(int fireTeamIndex) { this.fireTeamIndex = fireTeamIndex; return this; }
    public EntitySpec role(UnitRole role) { this.role = role; return this; }
    public EntitySpec specialEquipment(SpecialEquipmentDef equipment, int ammo) {
        this.specialEquipment = equipment;
        this.secondaryAmmo = ammo;
        return this;
    }
    public EntitySpec body(AirBody body) { this.body = body; return this; }
    public EntitySpec assignedObjective(Objective objective) { this.assignedObjective = objective; return this; }
    public EntitySpec home(int cellX, int cellY) { this.homeCellX = cellX; this.homeCellY = cellY; return this; }
    public EntitySpec hubSpawnCooldown(float sec) { this.hubSpawnCooldown = sec; return this; }
    public EntitySpec turretStructureId(String structureId) { this.turretStructureId = structureId; return this; }
    public EntitySpec homeHubId(long id) { this.homeHubId = id; return this; }
    public EntitySpec campaignSoldierId(String id) { this.campaignSoldierId = id; return this; }
    public EntitySpec campaignSquadId(String id) { this.campaignSquadId = id; return this; }
    public EntitySpec mechVariant(MechVariant variant) {
        if (!type.hasChassis()) {
            throw new IllegalStateException("Only chassis unit types accept a mech variant");
        }
        if (variant == null) throw new IllegalArgumentException("Mech variant is required");
        return variant.applyTo(this);
    }
    public EntitySpec layeredArmorFamily(LayeredArmorFamily family) { this.layeredArmorFamily = family; return this; }

    /**
     * Names the aircraft this hull is, so the body accessors can size it
     * without asking the field which berth it is standing on. Same convention
     * as {@link #mechVariant}: geometry comes from the per-instance thing.
     */
    public EntitySpec airframe(Airframe airframe) { this.airframe = airframe; return this; }

    public EntitySpec moveSpeed(float v) { this.moveSpeed = v; return this; }
    public EntitySpec hp(float v) { this.hp = v; return this; }
    public EntitySpec maxHp(float v) { this.maxHp = v; return this; }
    /** Spawn at full armor with the supplied capacity and resistance rating. */
    public EntitySpec armor(float maxArmor, float armorRating) {
        return armor(maxArmor, maxArmor, armorRating);
    }

    /** Seed an authored armor capability, including a partially depleted capacity. */
    public EntitySpec armor(float currentArmor, float maxArmor, float armorRating) {
        if (!Float.isFinite(maxArmor) || maxArmor <= 0f) {
            throw new IllegalArgumentException("Maximum armor must be finite and positive");
        }
        if (!Float.isFinite(currentArmor) || currentArmor < 0f || currentArmor > maxArmor) {
            throw new IllegalArgumentException("Current armor must be finite and within [0, maxArmor]");
        }
        if (!Float.isFinite(armorRating) || armorRating <= 0f) {
            throw new IllegalArgumentException("Armor rating must be finite and positive");
        }
        this.currentArmor = currentArmor;
        this.maxArmor = maxArmor;
        this.armorRating = armorRating;
        return this;
    }
    public EntitySpec attackDamage(float v) { this.attackDamage = v; return this; }
    public EntitySpec attackRange(float v) { this.attackRange = v; return this; }
    public EntitySpec accuracy(float v) { this.accuracy = v; return this; }
    public EntitySpec visionRange(float v) { this.visionRange = v; return this; }
    public EntitySpec airLosRadius(float v) { this.airLosRadius = v; return this; }
    public EntitySpec attackCooldown(float v) { this.attackCooldown = v; return this; }

    /**
     * Applies one issued armor package while keeping its movement and incoming-hit
     * tradeoffs independent from durability. A zero capacity leaves the ARMOR
     * capability absent but still applies those profile modifiers.
     */
    public EntitySpec armor(float maxArmor, float armorRating, float moveSpeedMult,
                            float incomingAccuracyMult) {
        if (!Float.isFinite(maxArmor) || maxArmor < 0f) {
            throw new IllegalArgumentException("Maximum armor must be finite and non-negative");
        }
        if (maxArmor > 0f) {
            armor(maxArmor, armorRating);
        } else if (!Float.isFinite(armorRating) || armorRating < 0f) {
            throw new IllegalArgumentException("Armor rating must be finite and non-negative");
        }
        this.moveSpeed *= Math.max(0.1f, moveSpeedMult);
        this.incomingAccuracyMult = Math.max(0f, incomingAccuracyMult);
        return this;
    }

    /**
     * Attaches the integral system the unit's armour pattern carries. Separate
     * from {@link #specialEquipment} on purpose: the suit's own capability
     * never spends the billet's carried item
     * ({@code integral-armor-systems.md}).
     */
    public EntitySpec integralSystem(IntegralSystemDef system) {
        this.integralSystem = system;
        return this;
    }

    /** Full HP + max HP to the same value — the common "spawn at full health with this capacity" case. */
    public EntitySpec health(float maxHp) { this.hp = maxHp; this.maxHp = maxHp; return this; }

    /**
     * Sets the primary weapon and derives its combat stat block (range / damage /
     * accuracy / cooldown) from the weapon — the shape the deboard loadout and the
     * {@code Drone} factory both use (a per-weapon profile drives the fire math).
     */
    public EntitySpec primaryWeapon(WeaponDef weapon) {
        return primaryWeapon(weapon, EquipmentGrade.SERVICE, SoldierProfile.REGULAR);
    }

    /** Equips a marine-primary definition. */
    public EntitySpec primaryWeapon(WeaponDef weapon, EquipmentGrade grade,
                                    SoldierProfile profile) {
        this.primaryWeaponDef = weapon;
        return primaryWeaponStats(weapon, grade, profile);
    }

    private EntitySpec primaryWeaponStats(WeaponDef weapon, EquipmentGrade grade,
                                          SoldierProfile profile) {
        this.equipmentGrade = grade != null ? grade : EquipmentGrade.SERVICE;
        this.soldierProfile = profile != null ? profile : SoldierProfile.REGULAR;
        if (weapon != null) {
            this.attackRange = InfantryCombatStats.range(weapon, this.equipmentGrade);
            this.attackDamage = InfantryCombatStats.damage(weapon, this.equipmentGrade);
            this.accuracy = InfantryCombatStats.accuracy(
                    weapon, this.equipmentGrade, this.soldierProfile);
            this.attackCooldown = InfantryCombatStats.cooldown(
                    weapon, this.equipmentGrade, this.soldierProfile);
        }
        return this;
    }

    /** Profile-only seed for infantry that still uses baked archetype weapon stats. */
    public EntitySpec soldierProfile(SoldierProfile profile) {
        this.soldierProfile = profile != null ? profile : SoldierProfile.REGULAR;
        return this;
    }

}
