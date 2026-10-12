package com.dillon.starsectormarines.battle.story;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DepotHoldProgressTest {
    @Test void captureNeedsCompanyAndContinuousUncontestedOccupancy() {
        var hold = new DepotHoldProgress();
        hold.advance(20, false, true, false, 20);
        assertFalse(hold.committed(), "militia cannot start the player's chapter alone");
        hold.advance(7, true, true, false, 20);
        hold.advance(1, true, true, true, 20);
        hold.advance(7, true, true, false, 20);
        assertFalse(hold.committed());
        hold.advance(1, true, true, false, 20);
        assertTrue(hold.committed());
        assertEquals(0, hold.waves());
    }
    @Test void emptyMapCannotSkipReservesAndRecaptureCannotFarmMoreWaves() {
        var hold = new DepotHoldProgress();
        hold.advance(8, true, true, false, 20);
        hold.advance(17, false, true, false, 20);
        assertEquals(0, hold.waves());
        assertFalse(hold.complete());
        hold.advance(1, false, true, false, 20);
        assertEquals(1, hold.waves());
        hold.advance(40, false, true, true, 12);
        assertEquals(2, hold.waves());
        assertFalse(hold.complete());
        hold.advance(100, false, false, false, 0);
        assertFalse(hold.complete(), "killing raiders while abandoning dispatch is not victory");
        hold.advance(7, false, true, false, 0);
        assertFalse(hold.complete());
        hold.advance(1, false, true, false, 0);
        assertTrue(hold.complete());
        assertEquals(2, hold.waves());
    }
}
