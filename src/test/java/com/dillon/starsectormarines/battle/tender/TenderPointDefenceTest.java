package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.tender.TenderPointDefence.Candidate;
import com.dillon.starsectormarines.battle.warship.VanillaWeapon;
import com.dillon.starsectormarines.battle.warship.VanillaWeapons;
import com.dillon.starsectormarines.battle.warship.WarshipScale;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenderPointDefenceTest {

    private static VanillaWeapon laser;

    @BeforeAll
    static void data() {
        InstalledVanillaData.install();
        laser = VanillaWeapons.get(TenderPointDefence.WEAPON_ID);
    }

    private static Candidate at(Object id, float x, float y, float landX, float landY) {
        return new Candidate(id, x, y, landX, landY, 60f, 10f);
    }

    @Test
    void theLaserIsTheMiningLaserTheBorerCarries() {
        assertNotNull(laser, "the mining laser is in the weapon table");
        assertTrue(laser.beam());
        assertEquals(600f, laser.rangePx(), 1e-3f);
        assertEquals(30f, TenderPointDefence.damagePerSecond(laser), 1e-3f);
        assertEquals(80f, TenderPointDefence.turnRate(laser), 1e-3f);
    }

    @Test
    void reachIsTheWeaponsRangeAtGroundScale() {
        assertEquals(WarshipScale.cells(600f), TenderPointDefence.reachCells(laser), 1e-4f);
        assertTrue(TenderPointDefence.reachCells(laser) > 20f && TenderPointDefence.reachCells(laser) < 35f,
                "about a rifle's reach, was " + TenderPointDefence.reachCells(laser));
    }

    @Test
    void aBeamBurnsForAsLongAsTheBodyIsTough() {
        assertEquals(2f, TenderPointDefence.secondsToDestroy(60f, laser), 1e-3f);
        assertTrue(TenderPointDefence.secondsToDestroy(750f, laser) > 20f, "a torpedo is not a thing a mining laser stops");
        assertEquals(0f, TenderPointDefence.secondsToDestroy(0f, laser), 1e-6f);
    }

    @Test
    void onlyADroneThatIsOutAliveAndNotChasingARaiderFires() {
        assertTrue(TenderPointDefence.onStation(true, true, false));
        assertFalse(TenderPointDefence.onStation(false, true, false), "docked");
        assertFalse(TenderPointDefence.onStation(true, false, false), "dead");
        assertFalse(TenderPointDefence.onStation(true, true, true), "away fighting");
    }

    @Test
    void ordnanceBoundForTheHullIsChosenBeforeNearerOrdnance() {
        Object near = new Object();
        Object bound = new Object();
        // The drone is at the origin, the tender at (10, 0). One body is close to the drone and lands far off;
        // the other is further from the drone and lands on the hull.
        List<Candidate> pool = List.of(at(near, 4f, 0f, 60f, 0f), at(bound, 15f, 0f, 10f, 0f));
        Candidate pick = TenderPointDefence.choose(pool, 0f, 0f, 10f, 0f, 6f, 27f);
        assertSame(bound, pick.source());
    }

    @Test
    void aDroneAlsoCoversTheGroundItOverfliesWithinItsReach() {
        Object far = new Object();
        List<Candidate> pool = List.of(at(far, 20f, 0f, 90f, 0f));
        assertSame(far, TenderPointDefence.choose(pool, 0f, 0f, 10f, 30f, 6f, 27f).source());
    }

    @Test
    void nothingBeyondTheDronesOwnReachIsEngaged() {
        List<Candidate> pool = List.of(at(new Object(), 40f, 0f, 10f, 0f));
        assertNull(TenderPointDefence.choose(pool, 0f, 0f, 10f, 0f, 6f, 27f),
                "bound for the hull, but out of this drone's reach");
    }

    @Test
    void theMostUrgentBodyIsBurnedFirstAndTheWingDoesNotSpread() {
        Object soon = new Object();
        Object later = new Object();
        // Both bound for the hull; one is nearly there, one has a long way to go.
        List<Candidate> pool = List.of(new Candidate(later, 3f, 0f, 10f, 0f, 60f, 5f),
                new Candidate(soon, 8f, 0f, 10f, 0f, 60f, 5f));
        assertSame(soon, TenderPointDefence.choose(pool, 0f, 0f, 10f, 0f, 6f, 27f).source());
    }

    @Test
    void aFasterBodyLandsSoonerFromTheSameDistance() {
        Object slow = new Object();
        Object fast = new Object();
        List<Candidate> pool = List.of(new Candidate(slow, 0f, 0f, 20f, 0f, 60f, 5f),
                new Candidate(fast, 0f, 1f, 20f, 0f, 60f, 20f));
        assertSame(fast, TenderPointDefence.choose(pool, 0f, 0f, 20f, 0f, 6f, 27f).source());
    }

    @Test
    void theMountSlewsAtItsTurnRateTheShortWayRound() {
        assertEquals(10f, TenderPointDefence.slew(0f, 90f, 10f), 1e-4f);
        assertEquals(90f, TenderPointDefence.slew(85f, 90f, 10f), 1e-4f);
        assertEquals(-170f, TenderPointDefence.slew(170f, -170f, 20f), 1e-4f, "wraps through 180, not back through 0");
    }

    @Test
    void aBeamBurnsOnlyWhileItIsOnTheBearing() {
        assertTrue(TenderPointDefence.onTarget(10f, 12f));
        assertFalse(TenderPointDefence.onTarget(10f, 40f));
        assertTrue(TenderPointDefence.onTarget(179f, -179f), "across the seam");
    }
}
