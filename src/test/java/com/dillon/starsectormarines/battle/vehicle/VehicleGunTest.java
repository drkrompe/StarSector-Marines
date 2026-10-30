package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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
        assertTrue(small.accepts(MechWeaponComponent.SRM_3_QS));
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
        assertEquals(Math.max(0.1f, srm.fluxPerTrigger() / Math.max(1, srm.weaponDef().burstCount)),
                fitted.mount.flux.perRound(), 1e-5f, "a round costs the weapon's own flux");
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
        VehicleType tank = VehicleType.FIRE_SUPPORT_APC;
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
        assertTrue(tank.wearsHeadOf(MechWeaponComponent.HEAVY_PD_LASER));
        assertFalse(tank.wearsHeadOf(MechWeaponComponent.PD_LASER), "the lighter lasers are drawn as themselves");
        assertFalse(tank.wearsHeadOf(MechWeaponComponent.CHAINGUN), "any other part is drawn as itself");
        assertFalse(tank.wearsHeadOf(null));
        assertFalse(VehicleType.BREAKTHROUGH_TANK.wearsHeadOf(MechWeaponComponent.BURST_LASER));

        assertEquals(0f, VehicleGun.lengthCells(tank, MechWeaponComponent.HEAVY_PD_LASER), 0f, "no drawing of its own to size");
        assertEquals(tank.turretStructure().mount.muzzleOffsetCells,
                VehicleGun.fit(tank, MechWeaponComponent.HEAVY_PD_LASER).mount.muzzleOffsetCells, 0f,
                "it fires from the head's lens");

        assertEquals(1, tank.headFrame(MechWeaponComponent.BURST_LASER, 1), "the white head is the stock frames");
        assertEquals(5, tank.headFrame(MechWeaponComponent.HEAVY_PD_LASER, 1));
        assertEquals(6, tank.headFrame(MechWeaponComponent.HEAVY_PD_LASER, 3));
        assertEquals(7, tank.headFrame(MechWeaponComponent.HEAVY_PD_LASER, 4));
        assertEquals(1, tank.headFrame(null, 1));
    }

    @Test
    void theOnagerIsModularAndWearsItsSingleCannonTurretAsTheHeavyCannonsHead() {
        VehicleType onager = VehicleType.BREAKTHROUGH_TANK;
        assertTrue(onager.hasModularTurret(), "an empty card is a bare ring");
        assertEquals(MechWeaponComponent.SINGLE_HEAVY_CANNON, onager.standardPart(), "a stock Onager fields the heavy cannon");
        assertTrue(onager.wearsHeadOf(MechWeaponComponent.SINGLE_HEAVY_CANNON));
        assertEquals(onager.turretFrame, onager.headFrame(MechWeaponComponent.SINGLE_HEAVY_CANNON, onager.turretFrame),
                "the heavy cannon's head is the approved turret");
        assertEquals(onager.wreckTurretFrame(),
                onager.headFrame(MechWeaponComponent.SINGLE_HEAVY_CANNON, onager.wreckTurretFrame()));
        assertEquals(0f, VehicleGun.lengthCells(onager, MechWeaponComponent.SINGLE_HEAVY_CANNON), 0f);
        assertEquals(onager.turretStructure().mount.muzzleOffsetCells,
                VehicleGun.fit(onager, MechWeaponComponent.SINGLE_HEAVY_CANNON).mount.muzzleOffsetCells, 0f,
                "it fires from the head's muzzle");
        assertFalse(VehicleType.LASER_TANK.wearsHeadOf(MechWeaponComponent.SINGLE_HEAVY_CANNON), "each hull its own heads");
        assertEquals(MechWeaponComponent.SINGLE_HEAVY_CANNON,
                VehicleDeploymentSpec.standard(onager).gun(), "a mission's standard Onager is not a bare ring");
    }

    @Test
    void eachBallisticFamilyWearsItsOwnOnagerHeadAndOnlyOnTheOnager() {
        VehicleType onager = VehicleType.BREAKTHROUGH_TANK;
        int autocannon = onager.headFrame(MechWeaponComponent.BASTION_AUTOCANNON, onager.turretFrame);
        int rotary = onager.headFrame(MechWeaponComponent.CHAINGUN, onager.turretFrame);
        int rail = onager.headFrame(MechWeaponComponent.LINEAR_CANNON, onager.turretFrame);
        assertEquals(4, Set.of(onager.turretFrame, autocannon, rotary, rail).size(), "four distinct heads");
        for (MechWeaponComponent family : List.of(MechWeaponComponent.MUSTER_AUTOGUN, MechWeaponComponent.DEMOLITION_CANNON,
                MechWeaponComponent.QUARRY_BREAKER_CANNON)) {
            assertEquals(autocannon, onager.headFrame(family, onager.turretFrame), family + " shares the autocannon head");
        }
        for (MechWeaponComponent family : List.of(MechWeaponComponent.CHAINGUN_HF, MechWeaponComponent.LIGHT_MACHINE_GUN)) {
            assertEquals(rotary, onager.headFrame(family, onager.turretFrame), family + " shares the rotary head");
        }
        int[] wrecks = {
                onager.headFrame(MechWeaponComponent.BASTION_AUTOCANNON, onager.wreckTurretFrame()),
                onager.headFrame(MechWeaponComponent.CHAINGUN, onager.wreckTurretFrame()),
                onager.headFrame(MechWeaponComponent.LINEAR_CANNON, onager.wreckTurretFrame())};
        for (int wreck : wrecks) {
            assertTrue(wreck > 0 && wreck < onager.frameCount && wreck != autocannon && wreck != rotary && wreck != rail,
                    "a head's wreck is its own frame on the sheet");
        }
        assertEquals(0f, VehicleGun.lengthCells(onager, MechWeaponComponent.LINEAR_CANNON), 0f, "a head draws no gun of its own");
        assertEquals(0f, VehicleGun.plateCells(onager, MechWeaponComponent.CHAINGUN), 0f, "nor stands on the gun plate");
        assertFalse(VehicleType.HEAVY_APC.wearsHeadOf(MechWeaponComponent.LIGHT_MACHINE_GUN),
                "a hull that declares no rotary head draws its machine gun as itself");
        for (MechWeaponComponent part : MechWeaponComponent.values()) {
            if (VehicleGunCard.of(onager).accepts(part)) {
                assertTrue(onager.wearsHeadOf(part), part + " goes in the Onager's card, so it wears a head");
            }
        }
    }

    @Test
    void onlyTheMachineGunHullsAndAHeadlessWeaponOnTheOnagerTurnOnTheGunPlate() {
        for (VehicleType hull : List.of(VehicleType.HEAVY_APC, VehicleType.MILITIA_TECHNICAL, VehicleType.DOZER_BREACHER)) {
            float plate = VehicleGun.plateCells(hull, MechWeaponComponent.LIGHT_MACHINE_GUN);
            assertTrue(plate > 0f && plate < VehicleGun.mountCells(hull), "inside the ring's race: " + hull);
        }
        assertEquals(0f, VehicleGun.plateCells(VehicleType.HEAVY_APC, null), "an empty card has no plate");
        assertEquals(0f, VehicleGun.plateCells(VehicleType.SRM_TECHNICAL, MechWeaponComponent.LIGHT_ROCKET_POD),
                "not on the rocket pickup");
        assertEquals(0f, VehicleGun.plateCells(VehicleType.FIRE_SUPPORT_APC, MechWeaponComponent.BASTION_AUTOCANNON),
                "nor under a fixed hull's other cannons");
        assertEquals(0f, VehicleGun.plateCells(VehicleType.LASER_TANK, MechWeaponComponent.LIGHT_PULSE_LASER),
                "a white hull's weapon stands in its own base");
        assertEquals(0f, VehicleGun.plateCells(VehicleType.BREAKTHROUGH_TANK, MechWeaponComponent.SINGLE_HEAVY_CANNON),
                "a head needs no plate");
    }
}
