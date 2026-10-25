package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Bombard's lob rules: the band it can reach, and how scatter and flight widen across it. */
class SwarmBombardBehaviorTest {

    private static final float RANGE = 55f;

    @Test
    void itLobsOnlyBetweenItsMinimumRangeAndItsReach() {
        float min = SwarmBombardBehavior.MIN_RANGE;
        assertFalse(SwarmBombardBehavior.inLobRange(min - 0.1f, RANGE), "inside the minimum range");
        assertTrue(SwarmBombardBehavior.inLobRange(min, RANGE));
        assertTrue(SwarmBombardBehavior.inLobRange(RANGE, RANGE));
        assertFalse(SwarmBombardBehavior.inLobRange(RANGE + 0.1f, RANGE), "past its reach");
    }

    @Test
    void scatterAndFlightRunFromTheNearValueToTheFarAcrossTheBand() {
        float min = SwarmBombardBehavior.MIN_RANGE;
        assertEquals(SwarmBombardBehavior.NEAR_SCATTER, SwarmBombardBehavior.scatterRadius(min, RANGE), 1e-5f);
        assertEquals(SwarmBombardBehavior.FAR_SCATTER, SwarmBombardBehavior.scatterRadius(RANGE, RANGE), 1e-5f);
        assertEquals(SwarmBombardBehavior.NEAR_FLIGHT_SECONDS, SwarmBombardBehavior.flightSeconds(min, RANGE), 1e-5f);
        assertEquals(SwarmBombardBehavior.FAR_FLIGHT_SECONDS, SwarmBombardBehavior.flightSeconds(RANGE, RANGE), 1e-5f);
        float mid = (min + RANGE) / 2f;
        assertTrue(SwarmBombardBehavior.scatterRadius(mid, RANGE) > SwarmBombardBehavior.NEAR_SCATTER
                && SwarmBombardBehavior.scatterRadius(mid, RANGE) < SwarmBombardBehavior.FAR_SCATTER);
        assertEquals(SwarmBombardBehavior.FAR_SCATTER, SwarmBombardBehavior.scatterRadius(RANGE * 2f, RANGE), 1e-5f,
                "clamped past the reach");
    }
}
