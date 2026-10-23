package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleDamageFxSystemTest {

    @Test
    void aWholeHullRaisesNothing() {
        assertEquals(0, VehicleDamageFxSystem.emitters(1f, 0f, false, false));
        assertEquals(0, VehicleDamageFxSystem.emitters(0.6f, 0.5f, false, false));
    }

    @Test
    void spentStructureOrWornPlatesSmokeTheDeckAndFailingThickensIt() {
        assertEquals(VehicleDamageFxSystem.WORN, VehicleDamageFxSystem.emitters(0.5f, 0f, false, false));
        assertEquals(VehicleDamageFxSystem.WORN, VehicleDamageFxSystem.emitters(1f, 0.75f, false, false));
        assertEquals(VehicleDamageFxSystem.WORN | VehicleDamageFxSystem.FAILING,
                VehicleDamageFxSystem.emitters(0.25f, 0f, false, false));
    }

    @Test
    void driveAndTurretLossRaiseTheirOwnEmittersOnAWholeHull() {
        assertEquals(VehicleDamageFxSystem.DRIVE, VehicleDamageFxSystem.emitters(1f, 0f, true, false));
        assertEquals(VehicleDamageFxSystem.TURRET, VehicleDamageFxSystem.emitters(1f, 0f, false, true));
    }

    @Test
    void hullShareLeavesOutTheTurretAndDrivePools() {
        HullTurretSection turret = new HullTurretSection(0.2f, 0.25f, 100f, 40f);
        HullLocations locations = new HullLocations(60f, new float[]{1f, 1f, 1f, 1f}, 20f,
                new float[]{0.1f, 0.2f, 0.3f}, turret);
        // 145 total: 25 turret, 20 drive, 100 the hull's own.
        assertEquals(1f, VehicleDamageFxSystem.hullShare(locations, 145f, 145f), 1e-6f);
        assertEquals(0.5f, VehicleDamageFxSystem.hullShare(locations, 95f, 145f), 1e-6f);
    }

    @Test
    void plateWearIsTheMeanOverThePlates() {
        HullLocations locations = new HullLocations(60f, new float[]{1f, 1f, 1f, 1f}, 20f,
                new float[]{0.1f, 0.2f, 0.3f}, null);
        assertEquals(0f, VehicleDamageFxSystem.meanPlateWear(locations), 1e-6f);
        locations.stageArmorShare(HullLocations.Plate.LEFT, 0f);
        assertEquals(0.25f, VehicleDamageFxSystem.meanPlateWear(locations), 1e-6f);
    }
}
