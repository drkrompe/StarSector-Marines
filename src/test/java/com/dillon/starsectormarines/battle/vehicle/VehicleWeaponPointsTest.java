package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hull's weapon-point variant: the gun card it locks, the head its gun turns in, and the colour a faction fields. */
class VehicleWeaponPointsTest {

    @Test
    void aVariantLocksTheSameRowsToAnotherColour() {
        VehicleGunCard green = VehicleGunCard.of(VehicleType.MEDIUM_TANK, HardpointType.MISSILE);
        assertEquals(HardpointType.MISSILE, green.hardpoint);
        assertEquals(VehicleGunCard.of(VehicleType.MEDIUM_TANK).rows, green.rows);
        assertTrue(green.accepts(MechWeaponComponent.SRM_5));
        assertFalse(green.accepts(MechWeaponComponent.SRM_15), "three rows do not go in two");
        assertFalse(green.accepts(MechWeaponComponent.HC_10), "the yellow gun does not go in the green card");

        VehicleGunCard red = VehicleGunCard.of(VehicleType.BREAKTHROUGH_TANK, HardpointType.ENERGY);
        assertTrue(red.accepts(MechWeaponComponent.PL_3));
        assertFalse(red.accepts(MechWeaponComponent.HC_20));
    }

    @Test
    void theHullsOwnColourAndNoColourAreItsOwnCardAndAColourItIsNotBuiltInIsNone() {
        VehicleGunCard own = VehicleGunCard.of(VehicleType.MEDIUM_TANK);
        assertSame(own, VehicleGunCard.of(VehicleType.MEDIUM_TANK, null));
        assertSame(own, VehicleGunCard.of(VehicleType.MEDIUM_TANK, HardpointType.BALLISTIC));
        assertNull(VehicleGunCard.of(VehicleType.LRM_CARRIER, HardpointType.ENERGY));
        assertTrue(VehicleType.HEAVY_APC.weaponPoints().isEmpty(), "an omni card has no colour to choose");
    }

    @Test
    void aMissileRackStandsInTheOpenBarbetteAndEveryOtherGunUnderTheClosedHead() {
        assertEquals(TurretHead.HEGEMONY_BARBETTE, VehicleType.MEDIUM_TANK.turretHead(HardpointType.MISSILE));
        assertEquals(TurretHead.HEGEMONY, VehicleType.MEDIUM_TANK.turretHead(HardpointType.ENERGY));
        assertEquals(TurretHead.HEGEMONY, VehicleType.MEDIUM_TANK.turretHead(null));
        assertFalse(VehicleGun.closedHead(VehicleType.BREAKTHROUGH_TANK, HardpointType.MISSILE));
        assertNull(VehicleType.LASER_TANK.turretHead(HardpointType.MISSILE), "a hull with no head gains none");
    }

    @Test
    void aStockSpawnFitsAPartItsVariantsCardTakes() {
        for (VehicleType type : VehicleType.values()) {
            for (HardpointType point : type.weaponPoints()) {
                MechWeaponComponent part = type.standardPart(point);
                if (part == null) continue;
                assertTrue(VehicleGunCard.of(type, point).accepts(part), type + " " + point + " " + part);
            }
        }
        assertEquals(VehicleType.MEDIUM_TANK.standardPart(), VehicleType.MEDIUM_TANK.standardPart(null));
    }

    @Test
    void factionsLeanToAColourAndEveryoneElseFieldsYellow() {
        assertEquals(HardpointType.ENERGY, FactionVehiclePoints.of("sindrian_diktat"));
        assertEquals(HardpointType.ENERGY, FactionVehiclePoints.of("lions_guard"));
        assertEquals(HardpointType.MISSILE, FactionVehiclePoints.of("luddic_church"));
        assertEquals(HardpointType.MISSILE, FactionVehiclePoints.of(" Luddic_Path "));
        assertEquals(HardpointType.BALLISTIC, FactionVehiclePoints.of("hegemony"));
        assertEquals(HardpointType.BALLISTIC, FactionVehiclePoints.of("pirates"));
        assertEquals(HardpointType.BALLISTIC, FactionVehiclePoints.of(null));
    }
}
