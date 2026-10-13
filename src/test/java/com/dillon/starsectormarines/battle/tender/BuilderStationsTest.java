package com.dillon.starsectormarines.battle.tender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderStationsTest {

    private static float gap(float[] a, float[] b) {
        return (float) Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    @Test
    void everyBuilderHasItsOwnStationOnTheRing() {
        float min = BuilderStations.RING_CELLS * 1.732f - 2f * BuilderStations.JITTER_CELLS * 1.5f;
        for (float t = 0f; t < 20f; t += 0.7f) {
            float[] a = BuilderStations.station(10f, 10f, 0f, 0, 3, t, 0.1f);
            float[] b = BuilderStations.station(10f, 10f, 0f, 1, 3, t, 0.5f);
            float[] c = BuilderStations.station(10f, 10f, 0f, 2, 3, t, 0.9f);
            assertTrue(gap(a, b) > min && gap(b, c) > min && gap(a, c) > min, "stations crowded at t=" + t);
        }
    }

    @Test
    void theFirstStationFacesTheTender() {
        float[] s = BuilderStations.station(0f, 0f, 90f, 0, 3, 0f, 0f);
        assertTrue(s[0] < -BuilderStations.RING_CELLS * 0.8f, "x was " + s[0]);
    }

    @Test
    void aStationStaysWithinTheJitterOfItsPoint() {
        float[] rest = BuilderStations.station(0f, 0f, 0f, 1, 3, 0f, 0f);
        for (float t = 0f; t < 10f; t += 0.3f) {
            float[] s = BuilderStations.station(0f, 0f, 0f, 1, 3, t, 0f);
            assertTrue(gap(s, rest) < BuilderStations.JITTER_CELLS * 3f);
            assertTrue(Math.abs(Math.hypot(s[0], s[1]) - BuilderStations.RING_CELLS) < BuilderStations.JITTER_CELLS * 1.5f);
        }
    }

    @Test
    void aStationMovesWithTheSite() {
        float[] a = BuilderStations.station(0f, 0f, 20f, 2, 3, 4f, 0.3f);
        float[] b = BuilderStations.station(7f, -3f, 20f, 2, 3, 4f, 0.3f);
        assertEquals(a[0] + 7f, b[0], 1e-4f);
        assertEquals(a[1] - 3f, b[1], 1e-4f);
    }

    @Test
    void theTorchFlickersAndIsNotInStepAcrossDrones() {
        int lit = 0;
        int differing = 0;
        for (int tick = 0; tick < 600; tick++) {
            boolean a = BuilderStations.torchLit(5L, tick);
            if (a) lit++;
            if (a != BuilderStations.torchLit(6L, tick)) differing++;
        }
        assertTrue(lit > 250 && lit < 550, "lit " + lit + " of 600");
        assertTrue(differing > 50, "differing " + differing);
    }

    @Test
    void aBurstHoldsForItsWholeLength() {
        int start = BuilderStations.FLICKER_TICKS * 5;
        boolean first = BuilderStations.torchLit(9L, start);
        for (int i = 1; i < BuilderStations.FLICKER_TICKS; i++) {
            assertEquals(first, BuilderStations.torchLit(9L, start + i));
        }
    }
}
