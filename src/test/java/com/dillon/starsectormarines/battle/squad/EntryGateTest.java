package com.dillon.starsectormarines.battle.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryGateTest {

    @Test
    void waitsWhileTheRoomIsFullAndTheSquadIsNear() {
        EntryGate gate = new EntryGate();
        assertTrue(gate.decide(7, 100, true, 2, 2, 900));
        assertTrue(gate.holding());
    }

    @Test
    void goesInWhenARoomOpens() {
        EntryGate gate = new EntryGate();
        gate.decide(7, 100, true, 2, 2, 900);
        assertFalse(gate.decide(7, 130, true, 1, 2, 900));
    }

    @Test
    void neverWaitsFromFarAway() {
        EntryGate gate = new EntryGate();
        assertFalse(gate.decide(7, 100, false, 5, 2, 900));
    }

    @Test
    void waitsNoLongerThanTheBoundAndStaysReleased() {
        EntryGate gate = new EntryGate();
        assertTrue(gate.decide(7, 100, true, 3, 2, 900));
        assertFalse(gate.decide(7, 1000, true, 3, 2, 900));
        assertFalse(gate.decide(7, 1030, true, 3, 2, 900));
    }

    @Test
    void aNewRoomStartsAFreshWait() {
        EntryGate gate = new EntryGate();
        gate.decide(7, 100, true, 3, 2, 900);
        gate.decide(7, 1000, true, 3, 2, 900);
        assertTrue(gate.decide(8, 1030, true, 3, 2, 900));
    }

    @Test
    void reusesAnAnswerUntilItGoesStale() {
        EntryGate gate = new EntryGate();
        gate.decide(7, 100, true, 3, 2, 900);
        assertTrue(gate.isFresh(7, 120, 30));
        assertFalse(gate.isFresh(7, 130, 30));
        assertFalse(gate.isFresh(8, 110, 30));
    }
}
