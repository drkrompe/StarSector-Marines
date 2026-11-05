package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechHardpointGeometry;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertTrue(cannon.accepts(MechWeaponComponent.HC_20));
        assertTrue(cannon.accepts(MechWeaponComponent.CHAINGUN), "a smaller gun of the type fits a larger card");
        assertFalse(cannon.accepts(MechWeaponComponent.PL_2_AP), "an energy weapon is not a ballistic one");
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
        assertTrue(small.accepts(MechWeaponComponent.BL_1));
        assertTrue(small.accepts(MechWeaponComponent.SRM_3_QS));
        assertFalse(small.accepts(MechWeaponComponent.PL_2_AP), "footprint is the only cap: two rows are too tall");
        assertFalse(small.accepts(null));
        assertFalse(VehicleGunCard.CANNON.accepts(MechWeaponComponent.BL_1), "a locked card keeps its lock");
    }

    @Test
    void theLaserTankTakesTheLaserPartsAndNoOtherColour() {
        VehicleGunCard laser = VehicleGunCard.of(VehicleType.LASER_TANK);
        assertSame(VehicleGunCard.LASER, laser);
        assertTrue(laser.accepts(MechWeaponComponent.PL_3), "the burst laser fills the three-row red card");
        assertTrue(laser.accepts(MechWeaponComponent.BL_2));
        assertFalse(laser.accepts(MechWeaponComponent.HC_20), "a ballistic gun is not an energy one");
        assertFalse(VehicleGunCard.SMALL.accepts(MechWeaponComponent.PL_3), "a three-row part does not fit a small card");
    }

    @Test
    void theMediumTankTakesTwoRowBallisticGunsButNeverTheHeavyCannon() {
        VehicleGunCard card = VehicleGunCard.of(VehicleType.MEDIUM_TANK);
        assertSame(VehicleGunCard.MEDIUM_CANNON, card);
        assertTrue(card.accepts(VehicleType.MEDIUM_TANK.standardPart()), "its stock gun fits its own card");
        assertTrue(card.accepts(MechWeaponComponent.AC_6));
        assertFalse(card.accepts(MechWeaponComponent.HC_20), "three rows are the Onager's");
        assertFalse(card.accepts(MechWeaponComponent.PL_2_AP));
    }

    @Test
    void aHeadSitsOnTheRingItsHullPaints() {
        VehicleType tank = VehicleType.MEDIUM_TANK;
        assertEquals(tank.visualWidthCells * tank.turretRingShareOfWidth(), VehicleGun.mountCells(tank), 1e-6f);
        assertTrue(VehicleGun.mountCells(tank) < tank.visualWidthCells * VehicleType.HEAVY_APC.turretRingShareOfWidth(),
                "a hull whose ring is narrower than most draws a smaller head");
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
        assertEquals(0, VehicleGun.fit(tank, MechWeaponComponent.HC_10).mount.ammoCapacity);
        assertEquals(0, VehicleGun.fit(tank, MechWeaponComponent.CHAINGUN).mount.ammoCapacity,
                "a rotary gun's belt is its feed");
        assertEquals(MechWeaponComponent.DC_5.ammoCapacity,
                VehicleGun.fit(tank, MechWeaponComponent.DC_5).mount.ammoCapacity);

        GroundTurret open = GroundTurret.fitted(VehicleGun.fit(tank, MechWeaponComponent.HC_10).mount, 0,
                MissileReplenisherComponent.STANDARD, null);
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
        StructureDef cannon = VehicleGun.fit(tank, MechWeaponComponent.HC_20);
        assertEquals((1f - VehicleGun.RING_FROM_REAR) * VehicleGun.lengthCells(tank, MechWeaponComponent.HC_20),
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
        assertTrue(tank.wearsHeadOf(MechWeaponComponent.PL_3));
        assertTrue(tank.wearsHeadOf(MechWeaponComponent.PL_3_SWEEP));
        assertFalse(tank.wearsHeadOf(MechWeaponComponent.BL_2), "the lighter lasers are drawn as themselves");
        assertFalse(tank.wearsHeadOf(MechWeaponComponent.CHAINGUN), "any other part is drawn as itself");
        assertFalse(tank.wearsHeadOf(null));
        assertFalse(VehicleType.BREAKTHROUGH_TANK.wearsHeadOf(MechWeaponComponent.PL_3));

        assertEquals(0f, VehicleGun.lengthCells(tank, MechWeaponComponent.PL_3_SWEEP), 0f, "no drawing of its own to size");
        assertEquals(tank.turretStructure().mount.muzzleOffsetCells,
                VehicleGun.fit(tank, MechWeaponComponent.PL_3_SWEEP).mount.muzzleOffsetCells, 0f,
                "it fires from the head's lens");

        assertEquals(1, tank.headFrame(MechWeaponComponent.PL_3, 1), "the white head is the stock frames");
        assertEquals(5, tank.headFrame(MechWeaponComponent.PL_3_SWEEP, 1));
        assertEquals(6, tank.headFrame(MechWeaponComponent.PL_3_SWEEP, 3));
        assertEquals(7, tank.headFrame(MechWeaponComponent.PL_3_SWEEP, 4));
        assertEquals(1, tank.headFrame(null, 1));
    }

    @Test
    void theOnagerDrawsItsWeaponUnderTheSharedHegemonyHead() {
        VehicleType onager = VehicleType.BREAKTHROUGH_TANK;
        assertEquals(TurretHead.HEGEMONY, onager.turretHead());
        assertFalse(onager.hasModularTurret(), "an empty card shows the head with nothing in its slot, not a bare ring");
        assertEquals(MechWeaponComponent.HC_20, onager.standardPart(), "a stock Onager fields the heavy cannon");
        assertEquals(MechWeaponComponent.HC_20,
                VehicleDeploymentSpec.standard(onager).gun(), "a mission's standard Onager is not an empty card");
        for (MechWeaponComponent part : MechWeaponComponent.values()) {
            if (!VehicleGunCard.of(onager).accepts(part)) continue;
            assertFalse(onager.wearsHeadOf(part), part + " is drawn as the shared mech drawing, under the head");
            assertTrue(VehicleGun.lengthCells(onager, part) > 0f, part + " is drawn");
        }
        assertTrue(VehicleGun.headCells(onager) > VehicleGun.mountCells(onager), "the head's base covers the ring");
    }

    @Test
    void onlyTheMachineGunHullsWithoutAHeadTurnTheirGunOnTheGunPlate() {
        for (VehicleType hull : List.of(VehicleType.MILITIA_TECHNICAL)) {
            float plate = VehicleGun.plateCells(hull, MechWeaponComponent.LIGHT_MACHINE_GUN);
            assertTrue(plate > 0f && plate < VehicleGun.mountCells(hull), "inside the ring's race: " + hull);
        }
        assertEquals(0f, VehicleGun.plateCells(VehicleType.MILITIA_TECHNICAL, null), "an empty card has no plate");
        assertEquals(0f, VehicleGun.plateCells(VehicleType.SRM_TECHNICAL, MechWeaponComponent.SRM_3_QS),
                "not on the rocket pickup");
        assertEquals(0f, VehicleGun.plateCells(VehicleType.FIRE_SUPPORT_APC, MechWeaponComponent.AC_6),
                "nor under a fixed hull's other cannons");
        assertEquals(0f, VehicleGun.plateCells(VehicleType.LASER_TANK, MechWeaponComponent.BL_1),
                "a white hull's weapon stands in its own base");
        assertEquals(0f, VehicleGun.plateCells(VehicleType.BREAKTHROUGH_TANK, MechWeaponComponent.HC_20),
                "a head needs no plate");
    }

    @Test
    void aClosedFamilyHeadCoversTheRingAndTakesThePlatesPlace() {
        VehicleType apc = VehicleType.HEAVY_APC;
        assertEquals(TurretHead.HEGEMONY, apc.turretHead());
        assertEquals(0f, VehicleGun.plateCells(apc, MechWeaponComponent.LIGHT_MACHINE_GUN), 0f,
                "the weapon is under the head, not on a gunner's plate");
        assertTrue(VehicleGun.headCells(apc) > VehicleGun.mountCells(apc), "the head's base covers the ring");
        assertTrue(VehicleGun.lengthCells(apc, MechWeaponComponent.LIGHT_MACHINE_GUN) > 0f,
                "the weapon is still drawn, under the head");
        for (MechWeaponComponent part : List.of(MechWeaponComponent.LIGHT_MACHINE_GUN, MechWeaponComponent.BL_1)) {
            float muzzle = (1f - VehicleGun.RING_FROM_REAR) * VehicleGun.lengthCells(apc, part);
            assertTrue(muzzle > VehicleGun.headCells(apc) / 2f, part + "'s muzzle clears the head's mantlet");
        }
        assertEquals(0f, VehicleGun.headCells(VehicleType.MILITIA_TECHNICAL), 0f, "a hull with no head draws none");
    }

    @Test
    void anOpenBarbetteHoldsMoreThanTheRingAndForcesNoBarrelPastIt() {
        VehicleType mortar = VehicleType.FIRE_SUPPORT_APC;
        assertFalse(VehicleGun.closedHead(mortar), "the carriers' weapons stand up in an open barbette");
        assertTrue(VehicleGun.headCells(mortar) > VehicleGun.headCells(VehicleType.HEAVY_APC),
                "the barbette's well gives the weapon more room than the closed head");
        float own = MechHardpointGeometry.barrelHullWidths(MechWeaponComponent.LIGHT_MACHINE_GUN)
                * VehicleGun.cellsPerMechWidth(mortar);
        assertEquals(own, VehicleGun.lengthCells(mortar, MechWeaponComponent.LIGHT_MACHINE_GUN), 1e-4f,
                "nothing to clear over an open well, so the weapon keeps its own length");
    }
}
