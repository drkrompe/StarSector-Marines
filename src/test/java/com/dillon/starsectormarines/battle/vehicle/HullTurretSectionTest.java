package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The turret section's arithmetic, on a hull of 1000 structure and 500 armor whose turret holds a fifth. */
class HullTurretSectionTest {
    private static final float RATING = 20f;
    private final DurabilityModel.Resolution scratch = new DurabilityModel.Resolution();
    private final HullTurretSection.Hit hit = new HullTurretSection.Hit();

    private static HullTurretSection section() {
        return new HullTurretSection(.25f, .2f, 1000f, 500f);
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
        // Full-penetration hit larger than the hull's own 400 armor: 400 to plate, 50 to structure.
        s.resolve(false, 450f, 40f, RATING, 500f, 1000f, scratch, hit);
        assertEquals(400f, hit.armorDamage(), 1e-3f);
        assertEquals(50f, hit.structureDamage(), 1e-3f);
        assertEquals(100f, s.armor(), 1e-4f);
        assertEquals(200f, s.structure(), 1e-4f);
        assertFalse(hit.gunDestroyed());
    }

    @Test
    void aTurretHitBreaksTheTurretThenCarriesIntoTheHullsArmorAndDestroysTheGunOnce() {
        HullTurretSection s = section();
        // 100 turret armor, 200 turret structure, then 50 into the hull's 400 armor.
        s.resolve(true, 350f, 40f, RATING, 500f, 1000f, scratch, hit);
        assertEquals(150f, hit.armorDamage(), 1e-3f);
        assertEquals(200f, hit.structureDamage(), 1e-3f);
        assertTrue(hit.gunDestroyed());
        assertTrue(s.destroyed());
        assertFalse(hit.hullDestroyed());
        // Through the hole: the next turret hit meets the hull's remaining plate, and the gun is not lost twice.
        s.resolve(true, 100f, 40f, RATING, 350f, 800f, scratch, hit);
        assertEquals(100f, hit.armorDamage(), 1e-3f);
        assertEquals(0f, hit.structureDamage(), 1e-3f);
        assertFalse(hit.gunDestroyed());
    }

    @Test
    void lowPenetrationChipsTheTurretPlateAtTheSharedEfficiency() {
        HullTurretSection s = section();
        s.resolve(true, 10f, 2f, RATING, 500f, 1000f, scratch, hit);
        float efficiency = DurabilityModel.armorEfficiency(2f, RATING);
        assertEquals(10f * efficiency, hit.armorDamage(), 1e-4f);
        assertEquals(100f - 10f * efficiency, s.armor(), 1e-4f);
        assertEquals(0f, hit.structureDamage());
    }

    @Test
    void onlyTheHullsOwnStructureKillsWhateverTheTurretStillHolds() {
        HullTurretSection s = section();
        // Hull's own pools: 0 armor, 10 structure (the columns still carry the turret's 100 + 200).
        s.resolve(false, 15f, 40f, RATING, 100f, 210f, scratch, hit);
        assertEquals(10f, hit.structureDamage(), 1e-4f);
        assertTrue(hit.hullDestroyed());
        assertFalse(s.destroyed());
    }

    @Test
    void onlyTheGunTanksAuthorASection() {
        if (!HullTurretSection.ENABLED) return;
        assertNotNull(HullTurretSection.forHull(VehicleType.BREAKTHROUGH_TANK));
        assertNotNull(HullTurretSection.forHull(VehicleType.SCREENED_TANK));
        assertNull(HullTurretSection.forHull(VehicleType.HEAVY_APC));
        assertNull(HullTurretSection.forHull(VehicleType.SCOUT_CAR));
    }
}
