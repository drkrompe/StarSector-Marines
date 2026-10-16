package com.dillon.starsectormarines.battle.setup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Greyhook's withdrawal: what her climb is worth under fire, and the circuit she flies over the basin. */
class BlackSluiceDuelWithdrawalTest {

    @Test
    void theClimbIsSlowestUnderTheWolfsGunsWithNobodyToScreenHer() {
        assertEquals(1f, BlackSluiceDuel.climbShare(false, false));
        assertEquals(1f, BlackSluiceDuel.climbShare(false, true));
        assertTrue(BlackSluiceDuel.climbShare(true, true) < 1f);
        assertTrue(BlackSluiceDuel.climbShare(true, false) < BlackSluiceDuel.climbShare(true, true),
                "a living screen buys height");
    }

    @Test
    void fromTheCradleTheCircuitHeadsWestOverTheMiddleOfTheBasin() {
        float[] at = BlackSluiceDuel.circuitStation(BlackSluiceDuel.RIG_X, BlackSluiceDuel.RIG_Y);
        assertTrue(at[0] < BlackSluiceDuel.RIG_X, "west first, toward the Wolfs: " + at[0]);
        float dx = (at[0] - BlackSluiceDuel.CIRCUIT_X) / BlackSluiceDuel.CIRCUIT_RX;
        float dy = (at[1] - BlackSluiceDuel.CIRCUIT_Y) / BlackSluiceDuel.CIRCUIT_RY;
        assertEquals(1f, dx * dx + dy * dy, 1e-4f, "on the circuit");
        // Following it round comes back to the start: a closed circuit, not a run for an edge.
        float[] p = at;
        for (int i = 0; i < 360 / (int) BlackSluiceDuel.CIRCUIT_LEAD_DEGREES + 1; i++) {
            p = BlackSluiceDuel.circuitStation(p[0], p[1]);
            assertTrue(BlackSluiceDuel.AIRSPACE.contains(p[0], p[1]));
        }
    }
}
