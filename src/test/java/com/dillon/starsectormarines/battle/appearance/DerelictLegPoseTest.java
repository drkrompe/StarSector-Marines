package com.dillon.starsectormarines.battle.appearance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictLegPoseTest {

    private static float length(float x0, float y0, float z0, float x1, float y1, float z1) {
        return (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
    }

    @Test
    void theKneeKeepsBothSegmentsTheirLengthAndBendsUp() {
        float[] k = new float[3];
        DerelictLegPose.knee(0f, 0f, 1.5f, 0f, 0f, k);
        float hz = DerelictLegPose.HIP_HEIGHT_CELLS;
        assertEquals(DerelictLegPose.THIGH_CELLS, length(0f, 0f, hz, k[0], k[1], k[2]), 1e-3f);
        assertEquals(DerelictLegPose.SHIN_CELLS, length(k[0], k[1], k[2], 1.5f, 0f, 0f), 1e-3f);
        assertTrue(k[2] > hz, "the knee stands above the hip");
        assertEquals(0f, k[1], 1e-5f, "the knee stays in the plane through hip and foot");
    }

    @Test
    void aFootFurtherOutLowersTheKnee() {
        float[] near = new float[3];
        float[] far = new float[3];
        DerelictLegPose.knee(0f, 0f, 1.2f, 0f, 0f, near);
        DerelictLegPose.knee(0f, 0f, 2.4f, 0f, 0f, far);
        assertTrue(far[2] < near[2]);
    }

    @Test
    void aFootOutOfReachLeavesTheLegStraightTowardIt() {
        float[] k = new float[3];
        DerelictLegPose.knee(0f, 0f, 0f, 10f, 0f, k);
        assertEquals(0f, k[0], 1e-5f);
        assertTrue(k[1] > 0f && k[1] < 10f);
    }
}
