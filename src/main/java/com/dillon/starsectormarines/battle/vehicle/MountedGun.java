package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.turret.TurretMountDef;

/**
 * One secondary mount on a live hull: what it is ({@link VehicleMount}) and its own aim, magazine and cadence
 * ({@link GroundTurret}, with no hull section, so it cannot be shot away on its own).
 */
public record MountedGun(VehicleMount mount, GroundTurret turret) {
    /** A fresh mount, its magazine full, on the stock loader. */
    public static MountedGun of(VehicleMount mount) {
        return of(mount, VehicleKit.Effect.NONE);
    }

    /** A fresh mount, its magazine full, its loader and flux bank as the hull's kits make them. */
    public static MountedGun of(VehicleMount mount, VehicleKit.Effect kits) {
        TurretMountDef gun = mount.structure().mount;
        return new MountedGun(mount, GroundTurret.fitted(gun, gun.ammoCapacity, kits, null));
    }
}
