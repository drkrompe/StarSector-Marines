package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The segment-segment closest points that capsule separation pushes along. */
class BodyCapsuleTest {

    private static final float EPS = 1e-5f;

    private static float[] closest(float px, float py, float phx, float phy,
                                   float qx, float qy, float qhx, float qhy) {
        float[] out = new float[4];
        BodyCapsule.closestPoints(px, py, phx, phy, qx, qy, qhx, qhy, out);
        return out;
    }

    private static float gap(float[] c) {
        return (float) Math.hypot(c[0] - c[2], c[1] - c[3]);
    }

    @Test
    void twoPointsAreTheirOwnClosestPoints() {
        float[] c = closest(1f, 2f, 0f, 0f, 4f, 6f, 0f, 0f);
        assertEquals(1f, c[0], EPS);
        assertEquals(2f, c[1], EPS);
        assertEquals(4f, c[2], EPS);
        assertEquals(6f, c[3], EPS);
    }

    @Test
    void aPointBesideASegmentMeetsItSquareOn() {
        // Segment along y from -1 to 1; a point off its flank at y = 0.4.
        float[] c = closest(0f, 0f, 0f, 1f, 0.5f, 0.4f, 0f, 0f);
        assertEquals(0f, c[0], EPS);
        assertEquals(0.4f, c[1], EPS);
        assertEquals(0.5f, gap(c), EPS);
    }

    @Test
    void aPointPastTheEndMeetsTheEnd() {
        float[] c = closest(0f, 0f, 0f, 1f, 0f, 1.6f, 0f, 0f);
        assertEquals(0f, c[0], EPS);
        assertEquals(1f, c[1], EPS);
        assertEquals(0.6f, gap(c), EPS);
    }

    @Test
    void crossingSegmentsMeetAtTheCrossing() {
        float[] c = closest(0f, 0f, 1f, 0f, 0.3f, 0f, 0f, 1f);
        assertEquals(0f, gap(c), EPS);
        assertEquals(0.3f, c[0], EPS);
        assertEquals(0f, c[1], EPS);
    }

    @Test
    void noseToTailInLineMeetsEndToEnd() {
        // Two bodies on one line along y, the second 2.5 ahead: ends at 1 and 1.5.
        float[] c = closest(0f, 0f, 0f, 1f, 0f, 2.5f, 0f, 1f);
        assertEquals(1f, c[1], EPS);
        assertEquals(1.5f, c[3], EPS);
        assertEquals(0.5f, gap(c), EPS);
    }

    @Test
    void sideBySideParallelBodiesMeetAcrossTheMiddleOfTheirOverlap() {
        // Flanks 0.4 apart, the second slid half a length forward.
        float[] c = closest(0f, 0f, 0f, 1f, 0.4f, 1f, 0f, 1f);
        assertEquals(0.4f, gap(c), EPS);
        // The overlap along y runs from 0 to 1: its middle.
        assertEquals(0.5f, c[1], EPS);
        assertEquals(0.5f, c[3], EPS);
    }

    @Test
    void theAnswerDoesNotDependOnWhichBodyIsAsked() {
        float[] pq = closest(0.2f, -0.3f, 0.6f, 0.2f, 0.9f, 0.5f, -0.1f, 0.7f);
        float[] qp = closest(0.9f, 0.5f, -0.1f, 0.7f, 0.2f, -0.3f, 0.6f, 0.2f);
        assertEquals(pq[0], qp[2], EPS);
        assertEquals(pq[1], qp[3], EPS);
        assertEquals(pq[2], qp[0], EPS);
        assertEquals(pq[3], qp[1], EPS);
    }
}
