package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where an off-map sortie comes from and goes back to.
 *
 * <p>The invariant worth a test is the one a bug once broke in play: an
 * aircraft that appears somewhere on the map has been conjured, and a corridor
 * exists precisely so there is nowhere on the map for it to appear.
 */
class AirCorridorTest {

    private static final int W = 60;
    private static final int H = 40;

    private static void assertOffMap(String what, float x, float y) {
        assertTrue(x < 0f || x > W || y < 0f || y > H,
                what + " (" + x + "," + y + ") is on the map — a sortie was conjured");
    }

    @Test
    void bothEndsAreOffTheMapFromAnywhere() {
        float[][] homes = {{2f, 2f}, {58f, 3f}, {30f, 38f}, {30f, 1f}, {1f, 20f}, {30f, 20f}};
        float[][] targets = {{30f, 20f}, {5f, 35f}, {55f, 5f}, {0f, 0f}, {60f, 40f}};
        for (float[] home : homes) {
            for (float[] target : targets) {
                AirCorridor c = AirCorridor.acrossNearestEdge(
                        "wing", W, H, home[0], home[1], target[0], target[1]);
                assertNotNull(c);
                assertOffMap("entry", c.entryX, c.entryY);
                assertOffMap("exit", c.exitX, c.exitY);
            }
        }
    }

    @Test
    void crossesTheEdgeNearestItsOwnForce() {
        AirCorridor west = AirCorridor.acrossNearestEdge("w", W, H, 3f, 20f, 50f, 20f);
        assertEquals(-AirCorridor.OFF_MAP_MARGIN_CELLS, west.entryX, 0.001f);
        assertEquals(-AirCorridor.OFF_MAP_MARGIN_CELLS, west.exitX, 0.001f);

        AirCorridor east = AirCorridor.acrossNearestEdge("e", W, H, 57f, 20f, 5f, 20f);
        assertEquals(W + AirCorridor.OFF_MAP_MARGIN_CELLS, east.entryX, 0.001f);

        AirCorridor south = AirCorridor.acrossNearestEdge("s", W, H, 30f, 2f, 30f, 35f);
        assertEquals(-AirCorridor.OFF_MAP_MARGIN_CELLS, south.entryY, 0.001f);

        AirCorridor north = AirCorridor.acrossNearestEdge("n", W, H, 30f, 38f, 30f, 5f);
        assertEquals(H + AirCorridor.OFF_MAP_MARGIN_CELLS, north.entryY, 0.001f);
    }

    /** In abeam what it was sent for, out abeam its own force. */
    @Test
    void entersAbeamTheTargetAndLeavesAbeamHome() {
        AirCorridor c = AirCorridor.acrossNearestEdge("w", W, H, 4f, 8f, 45f, 31f);
        assertEquals(31f, c.entryY, 0.001f);
        assertEquals(8f, c.exitY, 0.001f);
    }

    /** A target off the corner does not drag the corridor away with it. */
    @Test
    void staysWithinTheMapSpanAlongItsEdge() {
        AirCorridor c = AirCorridor.acrossNearestEdge("s", W, H, 30f, 1f, 900f, 5f);
        assertEquals(W, c.entryX, 0.001f);
        AirCorridor back = AirCorridor.acrossNearestEdge("s", W, H, 30f, 1f, -900f, 5f);
        assertEquals(0f, back.entryX, 0.001f);
    }

    /** No map means no statable origin, which is a decline rather than a guess. */
    @Test
    void declinesWithoutAMap() {
        assertNull(AirCorridor.acrossNearestEdge("w", 0, H, 1f, 1f, 2f, 2f));
        assertNull(AirCorridor.acrossNearestEdge("w", W, 0, 1f, 1f, 2f, 2f));
    }
}
