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
    @Test void coverClocksAreIndependentOfWhichGoalOwnsSteering() {
        CoverDuty duty = new CoverDuty(0, 0);
        duty.stationSecondsLeft = 2;
        duty.restSecondsLeft = .2f;
        duty.interceptSecondsLeft = 1;
        duty.advanceClocks(ShuttleState.ORBIT, .5f);
        assertEquals(1.5f, duty.stationSecondsLeft);
        assertEquals(0f, duty.restSecondsLeft);
        assertEquals(1f, duty.interceptSecondsLeft);
        duty.advanceClocks(ShuttleState.INTERCEPT, .5f);
        assertEquals(1f, duty.stationSecondsLeft);
        assertEquals(.5f, duty.interceptSecondsLeft);
        duty.advanceClocks(ShuttleState.DEPARTING, .5f);
        assertEquals(1f, duty.stationSecondsLeft, "egress never spends another station tick");
        duty.airRoundsLeft = 0; duty.groundPassesLeft = 0;
        assertTrue(duty.spent());
    }

}
