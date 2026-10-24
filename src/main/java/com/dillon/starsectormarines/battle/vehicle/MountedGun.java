package com.dillon.starsectormarines.battle.vehicle;

/**
 * One secondary mount on a live hull: what it is ({@link VehicleMount}) and its own aim, magazine and cadence
 * ({@link GroundTurret}, with no hull section, so it cannot be shot away on its own).
 */
public record MountedGun(VehicleMount mount, GroundTurret turret) {
    /** A fresh mount, its magazine full. */
    public static MountedGun of(VehicleMount mount) {
        return new MountedGun(mount, new GroundTurret(mount.structure().mount.ammoCapacity));
    }
}
