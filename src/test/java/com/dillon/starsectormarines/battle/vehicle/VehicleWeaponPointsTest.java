package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A weapon's colour on a hull: the head it turns in, the part a stock spawn fits, and the colour a faction prefers. */
class VehicleWeaponPointsTest {

    @Test
    void aMissileRackStandsInTheOpenBarbetteAndEveryOtherGunUnderTheClosedHead() {
        assertEquals(TurretHead.HEGEMONY_BARBETTE, VehicleType.MEDIUM_TANK.turretHead(HardpointType.MISSILE));
        assertEquals(TurretHead.HEGEMONY, VehicleType.MEDIUM_TANK.turretHead(HardpointType.ENERGY));
        assertEquals(TurretHead.HEGEMONY, VehicleType.MEDIUM_TANK.turretHead(null));
        assertFalse(VehicleGun.closedHead(VehicleType.BREAKTHROUGH_TANK, HardpointType.MISSILE));
        assertNull(VehicleType.LASER_TANK.turretHead(HardpointType.MISSILE), "a hull with no head gains none");
    }

    @Test
    void aStockSpawnFitsItsSidesColourOnlyInAnOmniCardAndAlwaysAPartTheCardTakes() {
        assertEquals(MechWeaponComponent.SRM_15, VehicleType.BREAKTHROUGH_TANK.standardPart(HardpointType.MISSILE));
        assertEquals(VehicleType.LRM_CARRIER.standardPart(), VehicleType.LRM_CARRIER.standardPart(HardpointType.ENERGY),
                "a locked card keeps its own part");
        assertEquals(VehicleType.MEDIUM_TANK.standardPart(), VehicleType.MEDIUM_TANK.standardPart(null));
        for (VehicleType type : VehicleType.values()) {
            VehicleGunCard card = VehicleGunCard.of(type);
            for (HardpointType point : HardpointType.values()) {
                MechWeaponComponent part = type.standardPart(point);
                if (card == null || part == null || part == type.standardPart()) continue;
                assertTrue(card.accepts(part), type + " " + point + " " + part);
            }
        }
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
