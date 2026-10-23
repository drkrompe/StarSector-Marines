package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * What a vehicle gun that reaches the air ({@code TurretMountDef#engagesAir}) needs from the air layer: which hostile
 * craft it can put a round into, and the round itself. The gun's aim, magazine, cadence and flux stay with
 * {@link GroundVehicleTurretSystem}, the one authority over a vehicle turret; this is only where the craft are and how
 * a round meets one. The anti-air emplacements' own pass ({@code GroundAntiAirSystem}) answers both, so a vehicle's
 * round against a fighter is rolled and landed the way an emplacement's is.
 */
public interface VehicleAirFire {

    /** No air layer: nothing is ever in the sky. */
    VehicleAirFire NONE = new VehicleAirFire() {
        @Override public long pick(Faction side, float x, float y, float range, long held) { return 0L; }
        @Override public boolean valid(Faction side, float x, float y, float range, long craft) { return false; }
        @Override public void fire(long shooter, Faction side, StructureDef structure, long craft,
                                   float muzzleX, float muzzleY) {}
    };

    /**
     * The airborne hostile craft a gun at {@code (x, y)} would take: {@code held} while it is still {@link #valid},
     * otherwise the nearest valid one, or {@code 0} when the sky in reach is empty.
     */
    long pick(Faction side, float x, float y, float range, long held);

    /** Whether {@code craft} is airborne, hostile to {@code side}, within {@code range} and has a clear line up. */
    boolean valid(Faction side, float x, float y, float range, long craft);

    /** Releases one round from the muzzle at the craft: a hit roll, its flight, and the damage on arrival. */
    void fire(long shooter, Faction side, StructureDef structure, long craft, float muzzleX, float muzzleY);
}
