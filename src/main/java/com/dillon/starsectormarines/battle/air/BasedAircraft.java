package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * Factory for the airframe standing on a garrison hardstand — the grid-unit
 * half of a based aircraft.
 *
 * <p>An aircraft is two things depending on where it is. In the air it is an
 * air entity: air identity, kinematics, a mission, and deliberately no grid or
 * combat components, which is what lets every grid walk in the battle skip air
 * for free. On its pad it is this instead — an ordinary unit, so that being
 * seen, gated by fog, traced against line of sight, hit, attributed, killed and
 * wrecked all come from the paths that already do those things. Teaching the
 * combat stack to see air would have bought three shootable aircraft at the
 * cost of that property forever.
 *
 * <p>That trade has since shifted. The unit spatial index indexes bodies rather
 * than dense-roster rows, so an entity now reaches every scan by carrying
 * {@code IDENTITY} alone and is skipped by the fire, movement and planning
 * systems for lack of their components — a convoy chassis works exactly that
 * way. When anti-air arrives, an airborne craft can become a body on the same
 * terms rather than earning a branch in each grid walk; see {@code air-nouns.md}.
 *
 * <p>The shape is {@link com.dillon.starsectormarines.battle.drone.DroneHub}'s:
 * combatant so it is targeted and damaged, {@link UnitRole#STRUCTURE} so it
 * never aims and never fires, and a per-instance hull rather than stats on the
 * {@link UnitType} — the {@link UnitType#TURRET} convention. A based aircraft
 * is a target, not a weapon.
 *
 * <p>Which hull it is, which berth it belongs to, and what happens to it across
 * a sortie are the berth's business, not the unit's; see {@link AirfieldService}.
 */
public final class BasedAircraft {

    /**
     * Armor on an airframe standing on the ground, as a fraction of its hull HP.
     *
     * <p>An aircraft is skinned to fly rather than to be shot at on the ground,
     * so it soaks far less than an emplacement of the same size. Enough that a
     * single rifle does not casually write one off; not enough to make burning
     * the field a project.
     *
     * <p>Read by {@code AirSystem} as well, because a machine taxiing across
     * the apron is the same skin as the one parked beside it and two ladders
     * for one aircraft would be a fact with two values.
     */
    static final float ARMOR_CAPACITY_FRACTION = 0.5f;

    /** Armor rating on an airframe on the ground, parked or rolling. Below a turret's — an airframe is a skin, not a casemate. */
    static final float ARMOR_RATING = 6f;

    private BasedAircraft() {}

    /**
     * Builds the airframe standing on a hardstand at {@code (cellX, cellY)},
     * with {@code hullHp} of structure left on it.
     *
     * <p>Hull HP is passed in rather than taken from the airframe because an
     * aircraft that comes home shot up parks shot up. The caller still owns
     * handing the result to {@code sim.spawn}.
     *
     * <p>The airframe goes onto the unit as well as its numbers. How much of an
     * aircraft there is to hit is per-instance geometry, and the alternative to
     * carrying it — asking the field which berth this id is standing on — is a
     * walk over every hardstand from inside the per-candidate-per-shot radius
     * read.
     */
    public static EntitySpec create(String id, Faction faction, Airframe airframe,
                                    int cellX, int cellY, float hullHp) {
        float capacity = Math.max(1f, airframe.maxHp());
        return new EntitySpec(id, faction, UnitType.BASED_AIRCRAFT, cellX, cellY)
                .airframe(airframe)
                .health(capacity)
                .hp(Math.max(1f, Math.min(capacity, hullHp)))
                .armor(capacity * ARMOR_CAPACITY_FRACTION, ARMOR_RATING)
                .attackDamage(0f)
                .attackRange(0f)
                .attackCooldown(1f)
                .accuracy(0f)
                .moveSpeed(0f)
                .role(UnitRole.STRUCTURE);
    }
}
