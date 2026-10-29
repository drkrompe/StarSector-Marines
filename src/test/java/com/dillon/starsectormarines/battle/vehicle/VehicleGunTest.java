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
        assertFalse(VehicleGunCard.SMALL.accepts(MechWeaponComponent.CHAINGUN), "two rows do not fit a one-row card");
        assertFalse(VehicleGunCard.LRM.accepts(MechWeaponComponent.SRM_15), "three rows do not fit a two-row card");
        assertTrue(VehicleGunCard.LRM.accepts(MechWeaponComponent.SRM_5), "launchers are one type, whichever mount family");
        assertFalse(VehicleGunCard.CANNON.accepts(null));
    }

    @Test
    void anOmniCardTakesAnyOneRowPartWhateverItsType() {
        VehicleGunCard small = VehicleGunCard.SMALL;
        assertNull(small.hardpoint, "unlocked");
        assertEquals(1, small.rows);
        assertTrue(small.accepts(MechWeaponComponent.LIGHT_MACHINE_GUN));
        assertTrue(small.accepts(MechWeaponComponent.LIGHT_PULSE_LASER));
        assertTrue(small.accepts(MechWeaponComponent.LIGHT_ROCKET_POD));
        assertFalse(small.accepts(MechWeaponComponent.PULSE_LASER), "footprint is the only cap: two rows are too tall");
        assertFalse(small.accepts(null));
        assertFalse(VehicleGunCard.CANNON.accepts(MechWeaponComponent.LIGHT_PULSE_LASER), "a locked card keeps its lock");
    }

    @Test
    void theLaserTankTakesTheLaserPartsAndNoOtherColour() {
        VehicleGunCard laser = VehicleGunCard.of(VehicleType.LASER_TANK);
        assertSame(VehicleGunCard.LASER, laser);
        assertTrue(laser.accepts(MechWeaponComponent.BURST_LASER), "the burst laser fills the three-row red card");
        assertTrue(laser.accepts(MechWeaponComponent.PD_LASER));
        assertFalse(laser.accepts(MechWeaponComponent.SINGLE_HEAVY_CANNON), "a ballistic gun is not an energy one");
        assertFalse(VehicleGunCard.SMALL.accepts(MechWeaponComponent.BURST_LASER), "a three-row part does not fit a small card");
    }

    @Test
    void anUnarmedHullHasNoCard() {
        assertNull(VehicleGunCard.of(VehicleType.SUPPLY_TRUCK));
    }

    @Test
    void aFittedWeaponFiresWithItsOwnBallisticsFromTheHullsCarriage() {
        StructureDef stock = VehicleType.LRM_CARRIER.turretStructure();
        MechWeaponComponent srm = MechWeaponComponent.SRM_5;
        StructureDef fitted = VehicleGun.fit(VehicleType.LRM_CARRIER, srm);

        assertSame(srm.weaponDef(), fitted.mount.weapon, "the weapon's own ballistics");
        assertEquals(stock.mount.turnRateDegPerSec, fitted.mount.turnRateDegPerSec, "the hull's own carriage");
        assertEquals(stock.maxStructure, fitted.maxStructure);
        assertEquals(srm.ammoCapacity * Math.max(1, srm.weaponDef().burstCount), fitted.mount.ammoCapacity,
                "a magazine of the weapon's pulls");
        assertEquals(stock.mount.flux.capacity(), fitted.mount.flux.capacity(), "the hull's own flux bank");
        assertSame(stock, VehicleGun.fit(VehicleType.LRM_CARRIER, null), "no weapon is the hull's own gun");
    }

    @Test
    void aWeaponWithNoLimitOnAMechHasNoMagazineHereAndAFiniteOneKeepsItsPulls() {
        VehicleType tank = VehicleType.BREAKTHROUGH_TANK;
        assertEquals(0, VehicleGun.fit(tank, MechWeaponComponent.LINEAR_CANNON).mount.ammoCapacity);
        assertEquals(0, VehicleGun.fit(tank, MechWeaponComponent.CHAINGUN).mount.ammoCapacity,
                "a rotary gun's belt is its feed");
        assertEquals(MechWeaponComponent.DEMOLITION_CANNON.ammoCapacity,
                VehicleGun.fit(tank, MechWeaponComponent.DEMOLITION_CANNON).mount.ammoCapacity);

        GroundTurret open = GroundTurret.fitted(VehicleGun.fit(tank, MechWeaponComponent.LINEAR_CANNON).mount, 0,
                VehicleKit.Effect.NONE, null);
        open.spend();
        assertTrue(open.loaded());
        assertEquals(0, open.ammo);
        GroundTurret limited = new GroundTurret(2);
        limited.spend();
        limited.spend();
        assertFalse(limited.loaded());
    }

    @Test
    void aFittedWeaponBringsItsOwnMuzzleAndEffects() {
        VehicleType tank = VehicleType.BREAKTHROUGH_TANK;
        StructureDef cannon = VehicleGun.fit(tank, MechWeaponComponent.SINGLE_HEAVY_CANNON);
        assertEquals((1f - VehicleGun.RING_FROM_REAR) * VehicleGun.lengthCells(tank, MechWeaponComponent.SINGLE_HEAVY_CANNON),
                cannon.mount.muzzleOffsetCells, 1e-5f, "the muzzle is the tip of the weapon's drawing");
        assertEquals(0, cannon.mount.muzzles.size(), "one barrel, not the hull's stock muzzle points");
        assertEquals(tank.turretStructure().mount.ejectsCasings, cannon.mount.ejectsCasings);

        StructureDef pod = VehicleGun.fit(VehicleType.LRM_CARRIER, MechWeaponComponent.SRM_5);
        assertEquals(false, pod.mount.ejectsCasings, "a missile pod ejects no casings");
        assertEquals(0f, pod.mount.scorchCells, 0f);
    }

    @Test
    void theLaserTankWearsItsOwnHeadForTheLaserPartsAndNoOtherHullDoes() {
        VehicleType tank = VehicleType.LASER_TANK;
        assertTrue(tank.wearsHeadOf(MechWeaponComponent.BURST_LASER));
        assertTrue(tank.wearsHeadOf(MechWeaponComponent.PD_LASER));
        assertFalse(tank.wearsHeadOf(MechWeaponComponent.CHAINGUN), "any other part is drawn as itself");
        assertFalse(tank.wearsHeadOf(null));
        assertFalse(VehicleType.BREAKTHROUGH_TANK.wearsHeadOf(MechWeaponComponent.BURST_LASER));

        assertEquals(0f, VehicleGun.lengthCells(tank, MechWeaponComponent.PD_LASER), 0f, "no drawing of its own to size");
        assertEquals(tank.turretStructure().mount.muzzleOffsetCells,
                VehicleGun.fit(tank, MechWeaponComponent.PD_LASER).mount.muzzleOffsetCells, 0f,
                "it fires from the head's lens");

        assertEquals(1, tank.headFrame(MechWeaponComponent.BURST_LASER, 1), "the white head is the stock frames");
        assertEquals(5, tank.headFrame(MechWeaponComponent.PD_LASER, 1));
        assertEquals(6, tank.headFrame(MechWeaponComponent.PD_LASER, 3));
        assertEquals(7, tank.headFrame(MechWeaponComponent.PD_LASER, 4));
        assertEquals(1, tank.headFrame(null, 1));
    }
}
