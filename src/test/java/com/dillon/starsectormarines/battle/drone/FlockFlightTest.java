package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlockFlightTest {

    /** Open everywhere but the wall column x = 5 and the roofed block x 5..7, y 5..7. */
    private static boolean open(int x, int y) {
        return x != 5 && !(x >= 5 && x <= 7 && y >= 5 && y <= 7);
    }

    @Test
    void aMoveIntoOpenGroundIsTakenAsAsked() {
        assertArrayEquals(new float[]{3.2f, 2.1f}, FlockFlight.slide(3f, 2f, 3.2f, 2.1f, FlockFlightTest::open));
    }

    @Test
    void aMoveIntoAWallSlidesAlongIt() {
        // From (4.8, 2.0) heading north-east into the wall column: keep the northward part, drop the eastward.
        assertArrayEquals(new float[]{4.8f, 2.4f}, FlockFlight.slide(4.8f, 2.0f, 5.1f, 2.4f, FlockFlightTest::open));
    }

    @Test
    void aMoveIntoARoofedCornerIsHeld() {
        // From (4.9, 4.9) into (5.1, 5.1): straight on is the roofed block, and both slides land in the wall column.
        assertArrayEquals(new float[]{4.9f, 4.9f}, FlockFlight.slide(4.9f, 4.9f, 5.1f, 5.1f, (x, y) -> x < 5 && y < 5));
    }

    @Test
    void aMemberCaughtInsideIsLetOut() {
        assertArrayEquals(new float[]{6.4f, 6.4f}, FlockFlight.slide(6.5f, 6.5f, 6.4f, 6.4f, FlockFlightTest::open));
    }

    @Test
    void strikeSlotsAreEvenlySpacedOnTheOrbitAndTurn() {
        int n = 4;
        for (int k = 0; k < n; k++) {
            float[] slot = FlockFlight.orbitSlot(10f, 10f, k, n, 0f);
            assertEquals(FlockFlight.ORBIT_CELLS, (float) Math.hypot(slot[0] - 10f, slot[1] - 10f), 1e-4f);
        }
        float[] a = FlockFlight.orbitSlot(0f, 0f, 0, n, 0f);
        float[] b = FlockFlight.orbitSlot(0f, 0f, 1, n, 0f);
        assertEquals(0f, a[0] * b[0] + a[1] * b[1], 1e-3f, "four slots are a quarter turn apart");
        float[] later = FlockFlight.orbitSlot(0f, 0f, 0, n, 1f);
        double turned = Math.toDegrees(Math.atan2(later[1], later[0]) - Math.atan2(a[1], a[0]));
        assertEquals(FlockFlight.ORBIT_DEG_PER_SEC, turned, 1e-2, "the ring turns");
    }
}
