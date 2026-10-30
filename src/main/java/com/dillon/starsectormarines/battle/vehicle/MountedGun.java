package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;

/**
 * One secondary mount on a live hull: what it is ({@link VehicleMount}) and its own aim, magazine and cadence
 * ({@link GroundTurret}, with no hull section, so it cannot be shot away on its own).
 */
public record MountedGun(VehicleMount mount, GroundTurret turret) {
    /** A fresh mount, its magazine full, on the standard loader. */
    public static MountedGun of(VehicleMount mount) {
        return of(mount, MissileReplenisherComponent.STANDARD);
    }

    /** A fresh mount, its magazine full, paced by the hull's {@code loader}. */
    public static MountedGun of(VehicleMount mount, MissileReplenisherComponent loader) {
        TurretMountDef gun = mount.structure().mount;
        return new MountedGun(mount, GroundTurret.fitted(gun, gun.ammoCapacity, loader, null));
    }
}
