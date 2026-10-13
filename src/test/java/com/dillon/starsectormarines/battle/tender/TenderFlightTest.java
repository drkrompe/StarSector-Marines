package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.drone.SortieFlight;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenderFlightTest {

    private static final float DT = 1f / 30f;

    @Test
    void aLaunchedDroneFliesToTheExitPointBeforeItIsOnTask() {
        TenderFlight flight = new TenderFlight();
        TenderFlight.Sortie s = flight.begin(7L, true, 0);
        assertFalse(flight.step(s, 10f, 10f, 10f, 10f, 0f, 0f, DT));
        assertNotNull(s.goal, "still leaving, so the goal is the exit point");
        assertEquals(TenderFlight.Leg.LAUNCH, s.leg);

        float[] exit = SortieFlight.exitPoint(10f, 10f, 0f, s.phase);
        flight.step(s, exit[0], exit[1], 10f, 10f, 0f, 1f, DT);
        assertEquals(TenderFlight.Leg.TASK, s.leg);
        assertNull(s.goal);
    }

    @Test
    void aLaunchThatNeverReachesItsExitPointIsOnTaskAfterTheTimeout() {
        TenderFlight flight = new TenderFlight();
        TenderFlight.Sortie s = flight.begin(7L, true, 0);
        for (int i = 0; i < (int) ((TenderFlight.LAUNCH_TIMEOUT + 1f) / DT); i++) {
            flight.step(s, 10f, 10f, 10f, 10f, 0f, i * DT, DT);
        }
        assertEquals(TenderFlight.Leg.TASK, s.leg);
    }

    @Test
    void aRecalledDroneFliesTheApproachThenTheBayAndDocksOnlyOnArrival() {
        TenderFlight flight = new TenderFlight();
        TenderFlight.Sortie s = flight.begin(7L, true, 0);
        s.leg = TenderFlight.Leg.TASK;
        flight.recall(s, 0f, false);
        float[] approach = SortieFlight.approachPoint(10f, 10f, 0f);

        // Far away: heads for the approach point, not the bay, and does not dock.
        assertFalse(flight.step(s, 40f, 40f, 10f, 10f, 0f, 0f, DT));
        assertEquals(approach[0], s.goal[0], 1e-3f);
        assertEquals(approach[1], s.goal[1], 1e-3f);

        // At the approach point: turns in to the bay.
        assertFalse(flight.step(s, approach[0], approach[1], 10f, 10f, 0f, 1f, DT));
        assertEquals(10f, s.goal[0], 1e-3f);
        assertEquals(10f, s.goal[1], 1e-3f);

        // Wandering off the line does not send it back out once it has turned in.
        assertFalse(flight.step(s, 14f, 10f, 10f, 10f, 0f, 2f, DT));
        assertEquals(10f, s.goal[0], 1e-3f);

        // In the bay: docks.
        assertTrue(flight.step(s, 10.5f, 10f, 10f, 10f, 0f, 3f, DT));
    }

    @Test
    void aDroneDoesNotDockFromAcrossTheMapOnTheTickItIsRecalled() {
        TenderFlight flight = new TenderFlight();
        TenderFlight.Sortie s = flight.begin(7L, true, 0);
        s.leg = TenderFlight.Leg.TASK;
        flight.recall(s, 0f, false);
        assertFalse(flight.step(s, 30f, 10f, 10f, 10f, 0f, 0f, DT));
    }

    @Test
    void aRecalledDroneKeepsToItsTaskUntilItsTurnComes() {
        TenderFlight flight = new TenderFlight();
        TenderFlight.Sortie s = flight.begin(7L, false, 0);
        s.leg = TenderFlight.Leg.TASK;
        flight.recall(s, 2f, false);
        assertFalse(flight.step(s, 30f, 10f, 10f, 10f, 0f, 1f, DT));
        assertNull(s.goal, "not released yet");
        flight.step(s, 30f, 10f, 10f, 10f, 0f, 2.1f, DT);
        assertNotNull(s.goal);
    }

    @Test
    void aRecallWithdrawnPutsAFlyingDroneBackOnTaskButNotAHurtOne() {
        TenderFlight flight = new TenderFlight();
        TenderFlight.Sortie called = flight.begin(1L, true, 0);
        called.leg = TenderFlight.Leg.TASK;
        flight.recall(called, 0f, false);
        flight.resume(called);
        assertEquals(TenderFlight.Leg.TASK, called.leg);

        TenderFlight.Sortie hurt = flight.begin(2L, true, 1);
        hurt.leg = TenderFlight.Leg.TASK;
        flight.recall(hurt, 0f, true);
        flight.resume(hurt);
        assertEquals(TenderFlight.Leg.RECOVER, hurt.leg);
    }

    @Test
    void aMovingBayIsLedByAShortWayAndNeverMoreThanTheCap() {
        TenderFlight flight = new TenderFlight();
        flight.trackBay(0f, 0f, 1f);
        flight.trackBay(2f, 0f, 1f);
        TenderFlight.Sortie s = flight.begin(7L, true, 0);
        s.leg = TenderFlight.Leg.TASK;
        flight.recall(s, 0f, false);
        s.approached = true;
        flight.step(s, 2f + 100f, 0f, 2f, 0f, 0f, 0f, DT);
        float cap = 2f * TenderFlight.MAX_LEAD_SECONDS;
        assertEquals(2f + cap, s.goal[0], 1e-3f);
    }

    @Test
    void aRepairingDroneIsNotAvailableUntilItsTimeIsUp() {
        TenderFlight flight = new TenderFlight();
        flight.repairing(100f);
        assertEquals(1, flight.underRepair(100f + TenderFlight.REPAIR_SECONDS - 0.1f));
        assertEquals(0, flight.underRepair(100f + TenderFlight.REPAIR_SECONDS + 0.1f));
    }

    @Test
    void theLowestFreeStationIsHandedOutAndAFreedOneIsReused() {
        TenderFlight flight = new TenderFlight();
        flight.begin(10L, false, 0);
        flight.begin(11L, false, 1);
        flight.begin(12L, false, 2);
        assertEquals(3, flight.freeStation(List.of(10L, 11L, 12L), 3));
        flight.forget(11L);
        assertEquals(1, flight.freeStation(List.of(10L, 12L), 3));
    }
}
