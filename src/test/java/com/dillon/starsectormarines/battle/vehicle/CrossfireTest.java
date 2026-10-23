package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.FireStance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossfireTest {

    @Test
    void twoShootersFromOppositeSidesRattleTheCrew() {
        Crossfire crossfire = new Crossfire();
        crossfire.record(1L, 0f);
        assertFalse(crossfire.active());
        crossfire.record(2L, 180f);
        assertTrue(crossfire.active());
        assertEquals(FireStance.MOVING.accuracyMult, crossfire.accuracyMultiplier());
    }

    @Test
    void twoShootersOnOneApproachAreNotACrossfire() {
        Crossfire crossfire = new Crossfire();
        crossfire.record(1L, 170f);
        crossfire.record(2L, -150f);
        assertFalse(crossfire.active());
        assertEquals(1f, crossfire.accuracyMultiplier());
    }

    @Test
    void oneShooterCirclingIsNeverACrossfire() {
        Crossfire crossfire = new Crossfire();
        crossfire.record(1L, 0f);
        crossfire.record(1L, 120f);
        crossfire.record(1L, 240f);
        assertFalse(crossfire.active());
    }

    @Test
    void theCrewSteadiesOnceOneSideFallsQuiet() {
        Crossfire crossfire = new Crossfire();
        crossfire.record(1L, 0f);
        crossfire.advance(Crossfire.WINDOW_SECONDS - 0.5f);
        crossfire.record(2L, 90f);
        assertTrue(crossfire.active());
        crossfire.advance(0.5f);
        assertFalse(crossfire.active());
        crossfire.record(1L, 0f);
        assertTrue(crossfire.active());
    }

    @Test
    void separationWrapsRoundTheCircle() {
        assertEquals(20f, Crossfire.separation(350f, 10f), 1e-4f);
        assertEquals(180f, Crossfire.separation(-90f, 90f), 1e-4f);
        assertEquals(90f, Crossfire.separation(45f, -45f), 1e-4f);
    }
}
