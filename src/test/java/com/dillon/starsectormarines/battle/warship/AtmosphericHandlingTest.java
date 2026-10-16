package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every hull is a lumbering giant in atmosphere: fast hulls are pulled toward a battleship's pace and every hull spools. */
class AtmosphericHandlingTest {

    @Test
    void aBattleshipKeepsItsPaceAndAFrigateIsPulledTowardIt() {
        // Onslaught: 25 speed, 10 accel, 4 deg/s, 4 deg/s^2.
        AtmosphericHandling.Figures onslaught = AtmosphericHandling.of(25f, 10f, 10f, 4f, 4f);
        assertEquals(25f, onslaught.maxSpeed(), 1e-4f);
        assertEquals(4f, onslaught.turnRate(), 1e-4f);
        // Wolf: 150 speed, 130 accel, 60 deg/s.
        AtmosphericHandling.Figures wolf = AtmosphericHandling.of(150f, 130f, 100f, 60f, 120f);
        assertEquals(25f * (float) Math.sqrt(6f), wolf.maxSpeed(), 1e-3f);
        assertEquals(4f * (float) Math.sqrt(15f), wolf.turnRate(), 1e-3f);
        assertTrue(wolf.maxSpeed() > onslaught.maxSpeed() && wolf.turnRate() > onslaught.turnRate(),
                "the ladder survives: a frigate still outruns and outturns a battleship");
    }

    @Test
    void noHullGathersOrShedsItsWayFasterThanTheSpool() {
        AtmosphericHandling.Figures wolf = AtmosphericHandling.of(150f, 130f, 100f, 60f, 120f);
        assertEquals(AtmosphericHandling.SPOOL_SECONDS, wolf.maxSpeed() / wolf.accel(), 1e-3f);
        assertEquals(AtmosphericHandling.SPOOL_SECONDS, wolf.maxSpeed() / wolf.decel(), 1e-3f);
        assertEquals(AtmosphericHandling.TURN_SPOOL_SECONDS, wolf.turnRate() / wolf.turnAccel(), 1e-3f);
        // A hull already slower to spool than that keeps its own figure.
        AtmosphericHandling.Figures slow = AtmosphericHandling.of(20f, 1f, 1f, 3f, 0.5f);
        assertEquals(1f, slow.accel(), 1e-4f);
        assertEquals(0.5f, slow.turnAccel(), 1e-4f);
    }
}
