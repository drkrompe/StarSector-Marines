package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.ArmorArcs;
import com.dillon.starsectormarines.battle.combat.Crippled;
import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.vehicle.HullLocations.Plate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HullLocationsTest {

    private static final float[] SHARES = {1f, .6f, .6f, .45f};
    private static final float[] DRIVE_HITS = {.05f, .15f, .12f};

    @Test
    void sidesAreReadFromWhereTheOriginStands() {
        HullLocations hull = new HullLocations(400f, SHARES, 50f, DRIVE_HITS, null);
        // Facing 0 is +Y; the hull's right is +X.
        assertEquals(Plate.FRONT, plate(hull, 0f, 0f, 10f));
        assertEquals(Plate.REAR, plate(hull, 0f, 0f, -10f));
        assertEquals(Plate.RIGHT, plate(hull, 0f, 10f, 0f));
        assertEquals(Plate.LEFT, plate(hull, 0f, -10f, 0f));
        // Turned to face +X, its right is -Y.
        assertEquals(Plate.RIGHT, plate(hull, -90f, 0f, -10f));
        assertEquals(Plate.LEFT, plate(hull, -90f, 0f, 10f));
    }

    @Test
    void theFrontKeepsTheWholePlateAndTheOthersAreThinner() {
        HullLocations hull = new HullLocations(400f, SHARES, 50f, DRIVE_HITS, null);
        assertEquals(400f, hull.maxArmor(Plate.FRONT), 1e-3f);
        assertEquals(240f, hull.maxArmor(Plate.LEFT), 1e-3f);
        assertEquals(180f, hull.maxArmor(Plate.REAR), 1e-3f);
        assertEquals(400f + 240f + 240f + 180f, hull.maxTotalArmor(), 1e-3f);
    }

    @Test
    void aHullShotOnlyFromTheLeftLosesItsLeftPlateThenStructure() {
        HullLocations hull = new HullLocations(400f, SHARES, 50f, DRIVE_HITS, null);
        float structure = 500f;
        DurabilityModel.Resolution scratch = new DurabilityModel.Resolution();
        HullLocations.Hit hit = new HullLocations.Hit();
        float structureLost = 0f;
        for (int i = 0; i < 12; i++) {
            hull.resolve(false, false, Plate.LEFT, 40f, 30f, 20f, 1f, 20f, 1f, structure, scratch, hit);
            structure -= hit.structureDamage();
            structureLost += hit.structureDamage();
        }
        assertEquals(0f, hull.armor(Plate.LEFT), 1e-3f);
        assertTrue(structureLost > 0f, "once the plate is gone, hits reach the hull");
        assertEquals(hull.maxArmor(Plate.FRONT), hull.armor(Plate.FRONT));
        assertEquals(hull.maxArmor(Plate.RIGHT), hull.armor(Plate.RIGHT));
        assertEquals(hull.maxArmor(Plate.REAR), hull.armor(Plate.REAR));
        assertEquals(hull.maxDrive(), hull.drive());
    }

    @Test
    void aLostDriveCripplesAndSpillsIntoTheHull() {
        HullLocations hull = new HullLocations(0f, SHARES, 50f, DRIVE_HITS, null);
        DurabilityModel.Resolution scratch = new DurabilityModel.Resolution();
        HullLocations.Hit hit = new HullLocations.Hit();
        hull.resolve(false, true, Plate.REAR, 30f, 30f, 20f, 1f, 20f, 1f, 500f, scratch, hit);
        assertFalse(hull.crippled());
        assertEquals(20f, hull.drive(), 1e-3f);
        hull.resolve(false, true, Plate.REAR, 50f, 30f, 20f, 1f, 20f, 1f, 470f, scratch, hit);
        assertTrue(hull.crippled());
        assertTrue(hit.driveDestroyed());
        assertEquals(50f, hit.structureDamage(), 1e-3f, "twenty on the drive, thirty through into the hull");
        assertFalse(hit.hullDestroyed());
    }

    @Test
    void onlyTheHullsOwnStructureKills() {
        HullLocations hull = new HullLocations(0f, SHARES, 50f, DRIVE_HITS, null);
        DurabilityModel.Resolution scratch = new DurabilityModel.Resolution();
        HullLocations.Hit hit = new HullLocations.Hit();
        // 200 total, 50 of it drive: the hull's own is 150.
        hull.resolve(false, false, Plate.FRONT, 149f, 30f, 20f, 1f, 20f, 1f, 200f, scratch, hit);
        assertFalse(hit.hullDestroyed());
        hull.resolve(false, false, Plate.FRONT, 5f, 30f, 20f, 1f, 20f, 1f, 51f, scratch, hit);
        assertTrue(hit.hullDestroyed());
    }

    @Test
    void aCrippledBodyStillDrivesAndTurnsOnTheSpotButSlowly() {
        BicycleBody whole = new BicycleBody(1.6f, 22f, 150f, 1.8f, 3.5f, 2.8f);
        BicycleBody crippled = new BicycleBody(1.6f, 22f, 150f, 1.8f, 3.5f, 2.8f);
        whole.teleport(5f, 5f, 0f);
        crippled.teleport(5f, 5f, 0f);
        crippled.crippled = true;
        for (int i = 0; i < 90; i++) {
            whole.tick(5f, 50f, 2.8f, 1f / 30f);
            crippled.tick(5f, 50f, 2.8f, 1f / 30f);
        }
        assertEquals(2.8f * Crippled.SPEED_SHARE, crippled.speed, 1e-4f, "slowed to its share, not stopped");
        assertTrue(crippled.y > 5f && crippled.y - 5f < (whole.y - 5f) * .5f);

        crippled.pivot(10f, 1f / 30f);
        assertEquals(10f * Crippled.SPEED_SHARE, crippled.facingDegrees, 1e-3f, "it turns on the spot, slowly");
    }

    private static Plate plate(HullLocations hull, float facing, float dx, float dy) {
        return hull.plateFor(ArmorArcs.classify(facing, dx, dy), facing, dx, dy);
    }
}
