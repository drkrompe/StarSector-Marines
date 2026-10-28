package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A vehicle's gun card: the weapons it takes by type and size, and the turret a fitted weapon makes of the hull's. */
class VehicleGunTest {

    @Test
    void aCardTakesOnlyAWeaponOfItsOwnTypeNoLargerThanItself() {
        VehicleGunCard cannon = VehicleGunCard.of(VehicleType.BREAKTHROUGH_TANK);
        assertSame(VehicleGunCard.CANNON, cannon);
        assertTrue(cannon.accepts(MechWeaponComponent.SINGLE_HEAVY_CANNON));
        assertTrue(cannon.accepts(MechWeaponComponent.CHAINGUN), "a smaller gun of the type fits a larger card");
        assertFalse(cannon.accepts(MechWeaponComponent.PULSE_LASER), "an energy weapon is not a ballistic one");
        assertFalse(cannon.accepts(MechWeaponComponent.LRM_5));
        assertFalse(VehicleGunCard.HEAVY_MG.accepts(MechWeaponComponent.CHAINGUN), "two rows do not fit a one-row card");
        assertFalse(VehicleGunCard.LRM.accepts(MechWeaponComponent.SRM_15), "three rows do not fit a two-row card");
        assertTrue(VehicleGunCard.LRM.accepts(MechWeaponComponent.SRM_5), "launchers are one type, whichever mount family");
        assertFalse(VehicleGunCard.CANNON.accepts(null));
    }

    @Test
    void anUnarmedHullHasNoCard() {
        assertNull(VehicleGunCard.of(VehicleType.SUPPLY_TRUCK));
    }

    @Test
    void aFittedWeaponFiresWithItsOwnBallisticsFromTheHullsCarriage() {
        StructureDef stock = VehicleType.LRM_CARRIER.turretStructure();
        MechWeaponComponent srm = MechWeaponComponent.SRM_5;
        StructureDef fitted = VehicleGun.fit(stock, srm);

        assertSame(srm.weaponDef(), fitted.mount.weapon, "the weapon's own ballistics");
        assertEquals(stock.mount.turnRateDegPerSec, fitted.mount.turnRateDegPerSec, "the hull's own carriage");
        assertEquals(stock.maxStructure, fitted.maxStructure);
        assertEquals(srm.ammoCapacity * Math.max(1, srm.weaponDef().burstCount), fitted.mount.ammoCapacity,
                "a magazine of the weapon's pulls");
        assertEquals(stock.mount.flux.capacity(), fitted.mount.flux.capacity(), "the hull's own flux bank");
        assertSame(stock, VehicleGun.fit(stock, null), "no weapon is the hull's own gun");
    }
}
