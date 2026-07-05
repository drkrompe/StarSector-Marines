package com.dillon.starsectormarines.battle.unit;

/**
 * One combatant in the battle simulation, reduced to its identity: a {@code long}
 * {@link #entityId} plus the immutable archetype ({@link #faction} + {@link #type}).
 * Every mutable per-unit datum lives in the battle {@code EntityWorld}'s components,
 * reached <em>by id</em> through the {@code World} facade / per-component Services
 * ({@code world.hp(id)}, {@code sim.combat().primaryWeapon(id)}, …) — never through
 * this object.
 *
 * <p><b>Construction.</b> A unit is described by an {@link EntitySpec} and minted via
 * {@code UnitRosterService.spawn(spec)} (or {@code BattleSimulation.spawn} /
 * {@code queueSpawn}), which assigns the id and seeds every world column from the
 * spec; this handle is the return value. There is no public field to write after the
 * fact — post-spawn mutation goes through the by-id Services.
 *
 * <p>This is the end state of the identity-collapse epic: an {@code Entity} is a thin
 * handle over its id. (Phase D replaces even the handle with a bare {@code long}.)
 */
public class Entity {

    /**
     * Monotonic entity id assigned by {@link UnitRosterService} on adoption.
     * {@code 0} means "not yet adopted" (the "no entity" sentinel a setup-discarded
     * handle carries); a non-zero value is stable for the life of the unit and never
     * recycled. This is the unit's identity — all mutable state is keyed off it in the
     * component stores, reached by id rather than through this object.
     */
    public long entityId = 0L;

    public final Faction faction;
    /** Archetype — drives sprite + base stat block. Set once at construction. */
    public final UnitType type;

    public Entity(Faction faction, UnitType type) {
        this.faction = faction;
        this.type = type;
    }

    /**
     * Null-safe entity id of an {@link Entity} ref: {@code u.entityId}, or {@code 0L}
     * (the "no entity" sentinel) when {@code u == null}. The single chokepoint for the
     * "ref → id, null → 0L" convention every cross-reference setter relies on
     * ({@code world.setTargetId(self.entityId, Entity.idOf(target))}).
     */
    public static long idOf(Entity u) {
        return (u == null) ? 0L : u.entityId;
    }
}
