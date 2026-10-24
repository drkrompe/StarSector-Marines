package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Facing -90 puts the chassis nose on +x, the way the Thunder Run column drives. */
class VehicleRepulsionGeometryTest {
    private static final float DT = 1f / 30f, HALF_L = 2.4f, HALF_W = .8f, R = .3f;
    private final float[] out = new float[4];

    @Test void aBodyUnderAMovingNoseIsShovedAsideAndCarriedForward() {
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, 3f, HALF_L, HALF_W, 12f, 10.3f, R, DT, 1L, true, out));
        assertTrue(out[1] > 0f, "Out the side it was already on");
        assertTrue(out[0] > 0f, "Riding the bow, so the hull cannot overtake it");
        assertEquals(HALF_W + R + VehicleRepulsionSystem.CLEARANCE - .3f, out[1], 1e-3f);
    }

    @Test void aParkedHullPushesOutAlongTheShallowerAxis() {
        // Just inside the tail end: a short way out backwards, a long way out sideways.
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, 0f, HALF_L, HALF_W, 7.5f, 10.1f, R, DT, 1L, true, out));
        assertTrue(out[0] < 0f);
        assertEquals(0f, out[1], 1e-4f);
    }

    @Test void theBowWaveClearsTheRoadAheadOnlyWhileMoving() {
        float ahead = 10 + HALF_L + R + VehicleRepulsionSystem.CLEARANCE + 1f;
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, 3f, HALF_L, HALF_W, ahead, 9.8f, R, DT, 1L, true, out));
        assertTrue(out[1] < 0f, "Off the centerline on its own side");
        assertEquals(0f, out[0], 1e-4f);
        float moving = Math.abs(out[1]);
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, 1f, HALF_L, HALF_W, ahead, 9.8f, R, DT, 1L, true, out));
        assertTrue(Math.abs(out[1]) < moving, "A slower hull pushes more gently");
        assertFalse(VehicleRepulsionSystem.displacement(10, 10, -90f, 0f, HALF_L, HALF_W, ahead, 9.8f, R, DT, 1L, true, out));
    }

    @Test void aBodyTheHullMeansToHitGetsNoBowWaveButIsMarkedOnTheBow() {
        float ahead = 10 + HALF_L + R + VehicleRepulsionSystem.CLEARANCE + 1f;
        assertFalse(VehicleRepulsionSystem.displacement(10, 10, -90f, 3f, HALF_L, HALF_W, ahead, 9.8f, R, DT, 1L, false, out));
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, 3f, HALF_L, HALF_W, 12f, 10.3f, R, DT, 1L, false, out));
        assertEquals(1f, out[2]);
        assertEquals(1f, out[3]);
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, 0f, HALF_L, HALF_W, 12f, 10.3f, R, DT, 1L, false, out));
        assertEquals(0f, out[3], "a parked hull has no bow");
    }

    @Test void aWalkingMechAtAParkedHullsTailSlidesRoundItsSideAndIsHeldOffTheEnd() {
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, 0f, HALF_L, HALF_W, 7.5f, 10.1f, R, DT, 1L, true,
                true, out));
        assertTrue(out[1] > 0f, "round the side it is already on");
        assertTrue(out[0] < 0f, "and back off the tail, not into it");
    }

    @Test void nothingBehindOrBesideTheWaveIsTouched() {
        assertFalse(VehicleRepulsionSystem.displacement(10, 10, -90f, 3f, HALF_L, HALF_W, 5f, 10f, R, DT, 1L, true, out));
        assertFalse(VehicleRepulsionSystem.displacement(10, 10, -90f, 3f, HALF_L, HALF_W, 13f, 13f, R, DT, 1L, true, out));
    }

    @Test void reversingCarriesTheBowWaveToTheTail() {
        float behind = 10 - HALF_L - R - VehicleRepulsionSystem.CLEARANCE - .5f;
        assertTrue(VehicleRepulsionSystem.displacement(10, 10, -90f, -1.5f, HALF_L, HALF_W, behind, 10.2f, R, DT, 1L, true, out));
        assertTrue(out[1] > 0f);
    }

    @Test void aStoppedHullKeepsRoutesOffItsFootprintAndAMovingOneDoesNot() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        Set<Integer> cells = new HashSet<>();
        // Nose on +x: 4.8 long on x, 1.6 wide on y, widened by the margin to x 7.3..12.7, y 8.9..11.1.
        VehicleRepulsionSystem.standingHullCells(10, 10, -90f, 0f, 2 * HALF_L, 2 * HALF_W, grid,
                (x, y) -> cells.add(y * 20 + x));
        assertTrue(cells.contains(10 * 20 + 7) && cells.contains(10 * 20 + 12), "end to end");
        assertTrue(cells.contains(8 * 20 + 10) && cells.contains(11 * 20 + 10), "side to side");
        assertFalse(cells.contains(10 * 20 + 6) || cells.contains(10 * 20 + 13) || cells.contains(12 * 20 + 10));
        cells.clear();
        VehicleRepulsionSystem.standingHullCells(10, 10, -90f, 3f, 2 * HALF_L, 2 * HALF_W, grid,
                (x, y) -> cells.add(y * 20 + x));
        assertTrue(cells.isEmpty(), "a moving hull is left to its bow wave");
    }
}
