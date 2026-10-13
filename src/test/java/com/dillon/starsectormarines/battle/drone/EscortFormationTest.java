package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscortFormationTest {

    private static float distance(float[] p, float x, float y) {
        return (float) Math.hypot(p[0] - x, p[1] - y);
    }

    @Test
    void aDroneLoitersAtAboutTheEaseRadiusFromTheShip() {
        for (float t = 0f; t < 60f; t += 1.7f) {
            float[] p = EscortFormation.station(50f, 50f, 0f, 1, 3, t, 0.3f, false);
            // The ring's centre rides LEAD_CELLS ahead of the hull, so the distance to the hull moves by that much.
            float d = distance(p, 50f, 50f);
            assertTrue(d > EscortFormation.EASE_RADIUS - EscortFormation.BREATH - EscortFormation.LEAD_CELLS - 0.01f
                    && d < EscortFormation.EASE_RADIUS + EscortFormation.BREATH + EscortFormation.LEAD_CELLS + 0.01f,
                    "distance " + d + " at t=" + t);
        }
    }

    @Test
    void aThreatenedShipDrawsTheRingInTighterThanAtEase() {
        float easeSum = 0f;
        float threatSum = 0f;
        int n = 0;
        for (float t = 0f; t < 90f; t += 3f) {
            easeSum += distance(EscortFormation.station(0f, 0f, 0f, 0, 3, t, 0.5f, false), 0f, 0f);
            threatSum += distance(EscortFormation.station(0f, 0f, 0f, 0, 3, t, 0.5f, true), 0f, 0f);
            n++;
        }
        assertTrue(threatSum / n < easeSum / n - 1.5f, "threatened mean " + threatSum / n + " vs ease " + easeSum / n);
    }

    @Test
    void slotsOwnDifferentSharesOfTheRing() {
        float[] a = EscortFormation.station(0f, 0f, 0f, 0, 3, 5f, 0.5f, false);
        float[] b = EscortFormation.station(0f, 0f, 0f, 1, 3, 5f, 0.5f, false);
        float[] c = EscortFormation.station(0f, 0f, 0f, 2, 3, 5f, 0.5f, false);
        assertTrue(distance(a, b[0], b[1]) > 4f);
        assertTrue(distance(b, c[0], c[1]) > 4f);
        assertTrue(distance(a, c[0], c[1]) > 4f);
    }

    @Test
    void theRingTurnsWithTheShipsHeading() {
        float[] north = EscortFormation.station(0f, 0f, 0f, 0, 3, 0f, 0f, true);
        float[] west = EscortFormation.station(0f, 0f, 90f, 0, 3, 0f, 0f, true);
        // Heading 90 is a quarter turn counter-clockwise of heading 0.
        assertEquals(-north[1], west[0], 1e-3f);
        assertEquals(north[0], west[1], 1e-3f);
    }

    @Test
    void theRingFollowsTheShipWhereverItIs() {
        float[] at0 = EscortFormation.station(0f, 0f, 30f, 2, 3, 12f, 0.7f, false);
        float[] at100 = EscortFormation.station(100f, -40f, 30f, 2, 3, 12f, 0.7f, false);
        assertEquals(at0[0] + 100f, at100[0], 1e-3f);
        assertEquals(at0[1] - 40f, at100[1], 1e-3f);
    }

    @Test
    void differentPhasesBreatheOutOfStep() {
        float[] a = EscortFormation.station(0f, 0f, 0f, 0, 1, 3f, 0.0f, true);
        float[] b = EscortFormation.station(0f, 0f, 0f, 0, 1, 3f, 0.4f, true);
        assertTrue(distance(a, b[0], b[1]) > 0.2f);
    }
}
