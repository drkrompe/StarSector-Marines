package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.vehicle.VehicleType.Stance;

/**
 * How a hull fights, read from the gun it fires (mechs-nouns.md, a vehicle is a mech for parts). A hull on its own
 * gun keeps its type's stance; a hull carrying a weapon from its gun card takes the stance the weapon asks for:
 * an indirect-fire weapon fights from beyond its target's reach and on a spotter's call, an anti-armor rack with no
 * arc waits in its cover for armor to come into reach, and anything else duels. The rule gives each stock hull the
 * stance its type already states for its stock gun, so a hull's behaviour changes only with the weapon it is given.
 */
public final class VehicleStance {

    private VehicleStance() { }

    /** The stance {@code mount}'s gun asks for. */
    public static Stance of(TurretMountDef mount) {
        if (mount.weapon.indirectFire) return Stance.STANDOFF;
        if (mount.hardenedTargetsOnly) return Stance.AMBUSH;
        return Stance.DUEL;
    }
}
