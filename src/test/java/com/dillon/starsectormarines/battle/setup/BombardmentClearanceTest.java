package com.dillon.starsectormarines.battle.setup;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BombardmentClearanceTest {
    @Test void noGracePeriodOpensFireOnTheFirstClearTick() {
        var gate = new BombardmentClearance(0);
        assertFalse(gate.authorize(50, true, 24, 1, false, true));
        assertTrue(gate.authorize(51, true, 24, 0, false, true));
        assertFalse(gate.authorize(52, true, 24, 1, false, true));
        assertTrue(gate.authorize(53, true, 24, 0, false, true));
    }

    @Test void proofAndWholeSurvivingCompanyAreRequired() {
        var gate = new BombardmentClearance(300);
        assertFalse(gate.authorize(100, false, 20, 0, false, true));
        assertFalse(gate.authorize(500, true, 0, 0, false, true));
        assertFalse(gate.authorize(900, true, 20, 1, false, true));
        assertEquals(-1, gate.warningTick());
        assertFalse(gate.authorize(1000, true, 20, 0, false, true));
        assertFalse(gate.authorize(1299, true, 20, 0, false, true));
        assertTrue(gate.authorize(1300, true, 20, 0, false, true));
    }
    @Test void evenOneReenteringSurvivorCancelsFireAndRequiresAnotherFullWarning() {
        var gate = new BombardmentClearance(300);
        gate.authorize(1, true, 20, 0, false, true);
        assertTrue(gate.authorize(301, true, 20, 0, false, true));
        assertFalse(gate.authorize(302, true, 20, 1, false, true));
        assertEquals(-1, gate.warningTick());
        assertFalse(gate.authorize(303, true, 20, 0, false, true));
        assertFalse(gate.authorize(602, true, 20, 0, false, true));
        assertTrue(gate.authorize(603, true, 20, 0, false, true));
    }
    @Test void inboundLiftAndMovingShipDoNotCountAsClearance() {
        var gate = new BombardmentClearance(300);
        gate.authorize(1, true, 20, 0, false, true);
        assertFalse(gate.authorize(301, true, 20, 0, true, true));
        assertFalse(gate.authorize(900, true, 20, 0, false, false));
        assertEquals(-1, gate.warningTick());
        assertFalse(gate.authorize(1000, true, 20, 0, false, true));
        assertTrue(gate.authorize(1300, true, 20, 0, false, true));
    }
}
