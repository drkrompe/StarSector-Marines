package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneSeparationTest {

    private static final long[] IDS = {1L, 2L, 3L};

    @Test
    void aNeighbourOutsideTheRadiusPushesNothing() {
        float[] push = DroneSeparation.push(1L, 0f, 0f, IDS, new float[]{0f, 5f, 9f}, new float[]{0f, 0f, 9f}, 3, 2f);
        assertArrayEquals(new float[]{0f, 0f}, push, 1e-6f);
    }

    @Test
    void aNeighbourInsideTheRadiusPushesDirectlyAway() {
        float[] push = DroneSeparation.push(1L, 0f, 0f, IDS, new float[]{0f, 1f, 90f}, new float[]{0f, 0f, 90f}, 3, 2f);
        assertTrue(push[0] < -0.5f, "pushed " + push[0]);
        assertEquals(0f, push[1], 1e-6f);
    }

    @Test
    void closerNeighboursPushHarder() {
        float[] near = DroneSeparation.push(1L, 0f, 0f, IDS, new float[]{0f, 0.5f, 90f}, new float[]{0f, 0f, 90f}, 3, 2f);
        float[] far = DroneSeparation.push(1L, 0f, 0f, IDS, new float[]{0f, 1.5f, 90f}, new float[]{0f, 0f, 90f}, 3, 2f);
        assertTrue(Math.abs(near[0]) > Math.abs(far[0]));
    }

    @Test
    void thePushNeverExceedsTheRadius() {
        float[] push = DroneSeparation.push(1L, 0f, 0f, IDS, new float[]{0f, 0.1f, -0.1f}, new float[]{0f, 0.1f, 0.1f}, 3, 2f);
        assertTrue(Math.hypot(push[0], push[1]) <= 2f + 1e-4f);
    }

    @Test
    void twoDronesOnTheSamePointLeaveOppositeWays() {
        float[] a = DroneSeparation.push(1L, 5f, 5f, IDS, new float[]{5f, 5f, 90f}, new float[]{5f, 5f, 90f}, 3, 2f);
        float[] b = DroneSeparation.push(2L, 5f, 5f, IDS, new float[]{5f, 5f, 90f}, new float[]{5f, 5f, 90f}, 3, 2f);
        assertTrue(Math.hypot(a[0], a[1]) > 0.5f);
        assertEquals(-a[0], b[0], 1e-5f);
        assertEquals(-a[1], b[1], 1e-5f);
    }

    @Test
    void aDroneDoesNotPushItself() {
        float[] push = DroneSeparation.push(2L, 5f, 5f, IDS, new float[]{9f, 5f, 90f}, new float[]{9f, 5f, 90f}, 3, 2f);
        assertArrayEquals(new float[]{0f, 0f}, push, 1e-6f);
    }
}
