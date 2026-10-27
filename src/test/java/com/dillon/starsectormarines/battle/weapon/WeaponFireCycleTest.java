package com.dillon.starsectormarines.battle.weapon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A held trigger's rounds leave on the cycle, spend the load, and the empty load reloads before the next pull. */
class WeaponFireCycleTest {

    @Test
    void aSingleShotRifleSpendsItsLoadOnTheCycleThenReloads() {
        WeaponFireCycle cycle = new WeaponFireCycle(0.5f, 1, 0f, 3, 2f);

        assertEquals(1f, cycle.releaseTime(2), 1e-6f);
        assertEquals(3f, cycle.period(), 1e-6f, "last round at 1.0 s, then a 2 s reload");
        assertEquals(2, cycle.at(0.1f).loaded());
        assertEquals(1, cycle.at(0.6f).loaded());

        WeaponFireCycle.State changing = cycle.at(1.5f);
        assertTrue(changing.reloading());
        assertEquals(0, changing.loaded());
        assertEquals(1.5f, changing.reloadRemaining(), 1e-5f);
        assertEquals(0.25f, changing.cycleFraction(), 1e-5f);

        assertEquals(2, cycle.at(3.1f).loaded(), "the next load's first pull follows the reload");
    }

    @Test
    void theRoundThatEmptiesTheLoadDropsTheRestOfItsBurst() {
        WeaponFireCycle cycle = new WeaponFireCycle(0.6f, 3, 0.1f, 4, 1f);

        assertEquals(4, cycle.releasesPerLoop());
        assertEquals(2, cycle.at(0.05f).burstRemaining(), "two rounds of the first pull still to leave");
        assertEquals(0.05f, cycle.at(0.05f).burstTimer(), 1e-5f);
        assertEquals(0, cycle.at(0.65f).burstRemaining(), "the empty load took the rest of the burst");
        assertEquals(0.6f, cycle.releaseTime(3), 1e-6f, "the fourth round opens the second pull");
        assertTrue(cycle.at(0.65f).reloading());
        assertEquals(1.6f, cycle.period(), 1e-5f);
    }

    @Test
    void aPullWaitsForItsOwnBurstToLeave() {
        WeaponFireCycle cycle = new WeaponFireCycle(0.1f, 4, 0.1f, 0, 0f);

        assertEquals(0.3f, cycle.cycleSeconds(), 1e-6f);
        assertEquals(0.3f, cycle.period(), 1e-6f);
    }

    @Test
    void aGunWithoutAMagazineNeverReloadsAndReadsTheSameEachLoop() {
        WeaponFireCycle cycle = new WeaponFireCycle(0.4f, 1, 0f, 0, 3f);

        assertFalse(cycle.magazineFed());
        WeaponFireCycle.State first = cycle.at(0.1f);
        assertFalse(first.reloading());
        assertEquals(first.sinceRelease(), cycle.at(0.1f + cycle.period() * 5f).sinceRelease(), 1e-4f);
        assertEquals(0.1f, first.sinceRelease(), 1e-5f);
    }
}
