package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hull gun's loader and flux bank, on a bare turret. */
class GroundTurretTest {

    @Test
    void theLoaderPutsOnePackBackEachCadenceFiringOrNotAndNeverPastTheMagazine() {
        GroundTurret turret = new GroundTurret(15);
        turret.loaderSeconds = 18f;
        turret.ammo = 0;
        turret.advanceLoader(17.9f, 5);
        assertEquals(0, turret.ammo, "nothing until a whole cadence has run");
        turret.advanceLoader(0.1f, 5);
        assertEquals(5, turret.ammo, "one pack, not the whole magazine");
        turret.advanceLoader(36f, 5);
        assertEquals(15, turret.ammo);
        turret.advanceLoader(100f, 5);
        assertEquals(15, turret.ammo, "the magazine is the ceiling");
        assertEquals(0f, turret.loaderProgress, "a full magazine banks no work toward the next pack");

        turret.ammo = 13;
        turret.advanceLoader(18f, 5);
        assertEquals(15, turret.ammo, "a pack tops the magazine up rather than overfilling it");

        GroundTurret unloaded = new GroundTurret(15);
        unloaded.ammo = 0;
        unloaded.advanceLoader(1000f, 5);
        assertEquals(0, unloaded.ammo, "a gun with no loader keeps what it was set down with");
    }

    @Test
    void theHullsLoaderPacesEveryPackAndWithoutOneOnlyMissilePacksStop() {
        TurretMountDef lrm = VehicleType.LRM_CARRIER.turretStructure().mount;
        assertTrue(lrm.missilePack);
        GroundTurret stock = GroundTurret.fitted(lrm, lrm.ammoCapacity, MissileReplenisherComponent.STANDARD, null);
        assertEquals(lrm.loaderSeconds, stock.loaderSeconds);
        GroundTurret fitted = GroundTurret.fitted(lrm, lrm.ammoCapacity, MissileReplenisherComponent.ACCELERATED_FEED,
                null);
        assertEquals(lrm.loaderSeconds * .75f, fitted.loaderSeconds, 1e-5f);
        GroundTurret none = GroundTurret.fitted(lrm, lrm.ammoCapacity, MissileReplenisherComponent.NONE, null);
        assertEquals(Float.POSITIVE_INFINITY, none.loaderSeconds, "no loader, no missile pack back");

        TurretMountDef cannon = VehicleType.BREAKTHROUGH_TANK.turretStructure().mount;
        assertFalse(cannon.missilePack);
        GroundTurret magazine = GroundTurret.fitted(cannon, cannon.ammoCapacity, MissileReplenisherComponent.NONE, null);
        assertEquals(cannon.loaderSeconds, magazine.loaderSeconds, "a magazine of rounds keeps its own loader time");
        assertTrue(VehicleGun.fit(VehicleType.LRM_CARRIER, MechWeaponComponent.LRM_5).mount.missilePack,
                "a fitted missile part's packs are missiles");
    }

    @Test
    void anArmedHullOwnedByNobodyRunsItsStockBankAndEveryReaderReadsTheReservoir() {
        assertEquals(HullReservoir.FACTORY_BANK, HullReservoir.stockFrame(VehicleType.LRM_CARRIER));
        HullReservoir.Frame laser = HullReservoir.stockFrame(VehicleType.LASER_TANK);
        assertEquals(HullReservoir.stockBank(VehicleType.LASER_TANK).capacity(), laser.capacity(), 1e-6f);
        assertEquals(HullReservoir.stockBank(VehicleType.LASER_TANK).dissipationPerSecond()
                        + HullReservoir.stockVentArrays(VehicleType.LASER_TANK) * HullReservoir.VENT_ARRAY_DISSIPATION,
                laser.dissipationPerSecond(), 1e-6f, "the laser tank's stock vent arrays shed its flux faster");
        assertNull(HullReservoir.stockFrame(VehicleType.SUPPLY_TRUCK), "no gun, no reservoir");

        TurretMountDef lrm = VehicleType.LRM_CARRIER.turretStructure().mount;
        GroundTurret turret = new GroundTurret(lrm.ammoCapacity);
        turret.reservoir = new MechFlux(140f, 7f);
        turret.reservoir.commit(20f);
        assertEquals(140f, turret.fluxCapacity(lrm));
        assertEquals(20f, turret.fluxValue());
        assertFalse(turret.fluxVenting());
    }
    @Test
    void everyGunAHullCarriesBuildsFlux() {
        for (VehicleType type : VehicleType.values()) {
            if (!type.hasTurretWeapon()) continue;
            assertTrue(type.turretStructure().mount.flux != null, type + "'s gun builds no flux");
            for (VehicleMount mount : type.secondaryMounts()) {
                assertTrue(mount.structure().mount.flux != null, type + "'s " + mount.structureId() + " builds no flux");
            }
        }
    }

}
