package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.turret.TurretKind;

/**
 * Construction spec for a ground-roster unit — the mutable bag of "what to spawn"
 * that {@code UnitRosterService.spawn} consumes to mint a world entity. It carries
 * the same data the {@link Entity} {@code seed*} fields used to (identity + cell +
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
    public final int cellX;
    public final int cellY;

    // ---- optional capability seeds (default = absent) ----
    public int squadId = Entity.NO_SQUAD;
    public UnitRole role = UnitRole.COMBATANT;
    public MarineSecondary secondaryWeapon;
    public int secondaryAmmo;
    public AirBody body;
    public MarineWeapon primaryWeapon;
    public Objective assignedObjective;
    public int homeCellX = -1;
    public int homeCellY = -1;
    public float hubSpawnCooldown = 0f;
    public TurretKind turretKind;
    public long homeHubId = 0L;

    // ---- stat block (seeded from type, override via setters) ----
    public float moveSpeed;
    public float hp;
    public float maxHp;
    public float attackDamage;
    public float attackRange;
    public float accuracy;
    public float visionRange;
    public float airLosRadius = 0f;
    public float attackCooldown;

    public EntitySpec(String name, Faction faction, UnitType type, int cellX, int cellY) {
        this.name = name;
        this.faction = faction;
        this.type = type;
        this.cellX = cellX;
        this.cellY = cellY;
        // Same archetype-default seeding the Entity ctor applied.
        this.moveSpeed = type.moveSpeed;
        this.hp = type.maxHp;
        this.maxHp = type.maxHp;
        this.attackDamage = type.attackDamage;
        this.attackRange = type.attackRange;
        this.accuracy = type.accuracy;
        this.visionRange = type.visionRange > 0f ? type.visionRange : type.attackRange;
        this.attackCooldown = type.attackCooldown;
    }

    public EntitySpec squad(int squadId) { this.squadId = squadId; return this; }
    public EntitySpec role(UnitRole role) { this.role = role; return this; }
    public EntitySpec secondary(MarineSecondary weapon, int ammo) { this.secondaryWeapon = weapon; this.secondaryAmmo = ammo; return this; }
    public EntitySpec body(AirBody body) { this.body = body; return this; }
    public EntitySpec assignedObjective(Objective objective) { this.assignedObjective = objective; return this; }
    public EntitySpec home(int cellX, int cellY) { this.homeCellX = cellX; this.homeCellY = cellY; return this; }
    public EntitySpec hubSpawnCooldown(float sec) { this.hubSpawnCooldown = sec; return this; }
    public EntitySpec turretKind(TurretKind kind) { this.turretKind = kind; return this; }
    public EntitySpec homeHubId(long id) { this.homeHubId = id; return this; }

    public EntitySpec moveSpeed(float v) { this.moveSpeed = v; return this; }
    public EntitySpec hp(float v) { this.hp = v; return this; }
    public EntitySpec maxHp(float v) { this.maxHp = v; return this; }
    public EntitySpec attackDamage(float v) { this.attackDamage = v; return this; }
    public EntitySpec attackRange(float v) { this.attackRange = v; return this; }
    public EntitySpec accuracy(float v) { this.accuracy = v; return this; }
    public EntitySpec visionRange(float v) { this.visionRange = v; return this; }
    public EntitySpec airLosRadius(float v) { this.airLosRadius = v; return this; }
    public EntitySpec attackCooldown(float v) { this.attackCooldown = v; return this; }

    /** Full HP + max HP to the same value — the common "spawn at full health with this pool" case. */
    public EntitySpec health(float maxHp) { this.hp = maxHp; this.maxHp = maxHp; return this; }

    /**
     * Sets the primary weapon and derives its combat stat block (range / damage /
     * accuracy / cooldown) from the weapon — the shape the deboard loadout and the
     * {@code Drone} factory both use (a per-weapon profile drives the fire math).
     */
    public EntitySpec primaryWeapon(MarineWeapon weapon) {
        this.primaryWeapon = weapon;
        this.attackRange = weapon.range;
        this.attackDamage = weapon.damage;
        this.accuracy = weapon.accuracy;
        this.attackCooldown = weapon.cooldown;
        return this;
    }

    /**
     * Builds the {@link Entity} handle for this spec and copies the spec into its
     * write-only {@code seed*} fields, ready for {@code UnitRosterService.allocate}.
     * Transitional bridge for identity-collapse Phase C: production spawns go
     * through {@code spawn(spec)}, which routes here; the {@code seed*} fields (and
     * this method) are removed in Phase C's final slice once every caller — tests
     * included — builds specs and {@code spawn} seeds the world columns straight
     * from the spec.
     */
    public Entity toEntity() {
        Entity e = new Entity(name, faction, type, cellX, cellY);
        e.seedSquadId = squadId;
        e.seedRole = role;
        e.seedSecondaryWeapon = secondaryWeapon;
        e.seedSecondaryAmmo = secondaryAmmo;
        e.seedBody = body;
        e.seedPrimaryWeapon = primaryWeapon;
        e.seedAssignedObjective = assignedObjective;
        e.seedHomeCellX = homeCellX;
        e.seedHomeCellY = homeCellY;
        e.seedHubSpawnCooldown = hubSpawnCooldown;
        e.seedTurretKind = turretKind;
        e.seedHomeHubId = homeHubId;
        e.seedMoveSpeed = moveSpeed;
        e.seedHp = hp;
        e.seedMaxHp = maxHp;
        e.seedAttackDamage = attackDamage;
        e.seedAttackRange = attackRange;
        e.seedAccuracy = accuracy;
        e.seedVisionRange = visionRange;
        e.seedAirLosRadius = airLosRadius;
        e.seedAttackCooldown = attackCooldown;
        return e;
    }
}
