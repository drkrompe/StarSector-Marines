package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.vehicle.HullLocations.Plate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The turret section's arithmetic, on a hull of 1000 structure and 500 armor whose turret holds a fifth, resolved
 * through the hull's locations: the front plate holds the hull's other 400 armor, and no drive.
 */
class HullTurretSectionTest {
    private static final float RATING = 20f;
    private static final float[] FRONT_ONLY = {1f, 0f, 0f, 0f};
    private static final float[] NO_DRIVE_HITS = {0f, 0f, 0f};
    private final DurabilityModel.Resolution scratch = new DurabilityModel.Resolution();
    private final HullLocations.Hit hit = new HullLocations.Hit();

    private static HullTurretSection section() {
        return new HullTurretSection(.25f, .2f, 1000f, 500f);
    }

    private static HullLocations hull(HullTurretSection turret) {
        return new HullLocations(400f, FRONT_ONLY, 0f, NO_DRIVE_HITS, turret);
    }

    private void strike(HullLocations hull, boolean onTurret, float damage, float penetration, float structure) {
        hull.resolve(onTurret, false, Plate.FRONT, damage, penetration, RATING, 1f, RATING, 1f, structure, scratch, hit);
    }

    @Test
    void theTurretHoldsItsShareOfBothPoolsAndTheRollPicksAQuarterOfHits() {
        HullTurretSection s = section();
        assertEquals(200f, s.maxStructure(), 1e-4f);
        assertEquals(100f, s.maxArmor(), 1e-4f);
        assertTrue(s.struck(0.24f));
        assertFalse(s.struck(0.25f));
    }

    @Test
    void aHullHitSpendsTheHullsOwnPlateAndLeavesTheTurretWhole() {
        HullTurretSection s = section();
        HullLocations hull = hull(s);
        // Full-penetration hit larger than the hull's own 400 armor: 400 to plate, 50 to structure.
        strike(hull, false, 450f, 40f, 1000f);
        assertEquals(400f, hit.armorDamage(), 1e-3f);
        assertEquals(50f, hit.structureDamage(), 1e-3f);
        assertEquals(100f, s.armor(), 1e-4f);
        assertEquals(200f, s.structure(), 1e-4f);
        assertFalse(hit.gunDestroyed());
    }

    @Test
    void aTurretHitBreaksTheTurretThenCarriesIntoTheHullsPlateAndDestroysTheGunOnce() {
        HullTurretSection s = section();
        HullLocations hull = hull(s);
        // 100 turret armor, 200 turret structure, then 50 into the hull's 400 armor.
        strike(hull, true, 350f, 40f, 1000f);
        assertEquals(150f, hit.armorDamage(), 1e-3f);
        assertEquals(200f, hit.structureDamage(), 1e-3f);
        assertTrue(hit.gunDestroyed());
        assertTrue(s.destroyed());
        assertFalse(hit.hullDestroyed());
        // Through the hole: the next turret hit meets the hull's remaining plate, and the gun is not lost twice.
        strike(hull, true, 100f, 40f, 800f);
        assertEquals(100f, hit.armorDamage(), 1e-3f);
        assertEquals(0f, hit.structureDamage(), 1e-3f);
        assertFalse(hit.gunDestroyed());
    }

    @Test
    void lowPenetrationChipsTheTurretPlateAtTheSharedEfficiency() {
        HullTurretSection s = section();
        HullTurretSection.Hit turretHit = new HullTurretSection.Hit();
        float through = s.strike(10f, 2f, RATING, 1f, scratch, turretHit);
        float efficiency = DurabilityModel.armorEfficiency(2f, RATING);
        assertEquals(0f, through);
        assertEquals(10f * efficiency, turretHit.armorDamage(), 1e-4f);
        assertEquals(100f - 10f * efficiency, s.armor(), 1e-4f);
        assertEquals(0f, turretHit.structureDamage());
    }

    @Test
    void onlyTheHullsOwnStructureKillsWhateverTheTurretStillHolds() {
        HullTurretSection s = section();
        HullLocations hull = new HullLocations(0f, FRONT_ONLY, 0f, NO_DRIVE_HITS, s);
        // Hull's own structure is 10 (the column still carries the turret's 200).
        strike(hull, false, 15f, 40f, 210f);
        assertEquals(10f, hit.structureDamage(), 1e-4f);
        assertTrue(hit.hullDestroyed());
        assertFalse(s.destroyed());
    }

    @Test
    void onlyTheGunTanksAuthorASection() {
        if (!HullTurretSection.ENABLED) return;
        assertNotNull(HullTurretSection.forHull(VehicleType.BREAKTHROUGH_TANK));
        assertNotNull(HullTurretSection.forHull(VehicleType.LASER_TANK));
        assertNull(HullTurretSection.forHull(VehicleType.HEAVY_APC));
        assertNull(HullTurretSection.forHull(VehicleType.SCOUT_CAR));
    }
}
