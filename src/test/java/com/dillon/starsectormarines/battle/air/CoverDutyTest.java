package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A cover fighter's bounded means: the ring it can fly, and when it can take work. */
class CoverDutyTest {

    @Test
    void theRingIsNeverTighterThanTheFloorNorThanTheHullCanTurn() {
        assertEquals(CoverDuty.RING_FLOOR_CELLS, CoverDuty.ringRadius(3f));
        assertEquals(CoverDuty.RING_FLOOR_CELLS, CoverDuty.ringRadius(Float.NaN));
        assertEquals(40f * CoverDuty.RING_TURN_MARGIN, CoverDuty.ringRadius(40f), 1e-4f);
    }

    @Test
    void aFreshDutyIsReadyAndRestingOrExpiredIsNot() {
        CoverDuty duty = new CoverDuty(10f, 10f);
        assertTrue(duty.readyForWork());
        duty.restSecondsLeft = 2f;
        assertFalse(duty.readyForWork());
        duty.restSecondsLeft = 0f;
        duty.stationSecondsLeft = 0f;
        assertFalse(duty.readyForWork());
    }

    @Test
    void airRoundsRunOut() {
        CoverDuty duty = new CoverDuty(0f, 0f);
        assertTrue(duty.hasAirRounds());
        duty.airRoundsLeft = 0;
        assertFalse(duty.hasAirRounds());
    }
}
