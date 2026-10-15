package com.dillon.starsectormarines.battle.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Facing -90 puts the chassis nose on +x, the way the Thunder Run column drives. */
class VehicleRepulsionGeometryTest {
    private static final float DT = 1f / 30f, HALF_L = 2.4f, HALF_W = .8f, R = .3f;
    private final float[] out = new float[2];

    @Test void aBodyUnderAMovingNoseIsShovedAsideAndCarriedForward() {
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, 3f, HALF_L, HALF_W, 12f, 10.3f, R, DT, 1L, out));
        assertTrue(out[1] > 0f, "Out the side it was already on");
        assertTrue(out[0] > 0f, "Riding the bow, so the hull cannot overtake it");
        assertEquals(HALF_W + R + VehicleRepulsionSystem.CLEARANCE - .3f, out[1], 1e-3f);
    }

    @Test void aParkedHullPushesOutAlongTheShallowerAxis() {
        // Just inside the tail end: a short way out backwards, a long way out sideways.
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, 0f, HALF_L, HALF_W, 7.5f, 10.1f, R, DT, 1L, out));
        assertTrue(out[0] < 0f);
        assertEquals(0f, out[1], 1e-4f);
    }

    @Test void theBowWaveClearsTheRoadAheadOnlyWhileMoving() {
        float ahead = 10 + HALF_L + R + VehicleRepulsionSystem.CLEARANCE + 1f;
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, 3f, HALF_L, HALF_W, ahead, 9.8f, R, DT, 1L, out));
        assertTrue(out[1] < 0f, "Off the centerline on its own side");
        assertEquals(0f, out[0], 1e-4f);
        float moving = Math.abs(out[1]);
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, 1f, HALF_L, HALF_W, ahead, 9.8f, R, DT, 1L, out));
        assertTrue(Math.abs(out[1]) < moving, "A slower hull pushes more gently");
        assertFalse(VehicleRepulsionSystem.displacement(10, 10, -90f, 0f, HALF_L, HALF_W, ahead, 9.8f, R, DT, 1L, out));
    }

    @Test void nothingBehindOrBesideTheWaveIsTouched() {
        assertFalse(VehicleRepulsionSystem.displacement(10, 10, -90f, 3f, HALF_L, HALF_W, 5f, 10f, R, DT, 1L, out));
        assertFalse(VehicleRepulsionSystem.displacement(10, 10, -90f, 3f, HALF_L, HALF_W, 13f, 13f, R, DT, 1L, out));
    }

    @Test void reversingCarriesTheBowWaveToTheTail() {
        float behind = 10 - HALF_L - R - VehicleRepulsionSystem.CLEARANCE - .5f;
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, -1.5f, HALF_L, HALF_W, behind, 10.2f, R, DT, 1L, out));
        assertTrue(out[1] > 0f);
    }
}
