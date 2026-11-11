package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacePainterDoorTest {

    /**
     * Once a door is cut through a wall, nothing painted on the wall crosses its hole, shaded or flat, while the wall
     * round it is still painted.
     */
    @Test
    void nothingIsPaintedAcrossADoorsHole() {
        float meters = BuildingFaces.HEIGHTS[2], lift = BuildingFaces.lift(meters);
        FacePainter painter = new FacePainter();
        painter.reset();
        painter.wall(10f, 20.3f, 13f, 20.3f, 10f, 20f, 13f, 20f, 11.5f, 5f, lift, 0f, -1f, 1f);
        float[] middle = new float[8];
        painter.corners(.49f, .51f, .19f, .21f, middle);
        float px = (middle[0] + middle[4]) / 2f, py = (middle[1] + middle[5]) / 2f;
        float[] beside = new float[8];
        painter.corners(.09f, .11f, .19f, .21f, beside);
        float qx = (beside[0] + beside[4]) / 2f, qy = (beside[1] + beside[5]) / 2f;

        painter.cut(.26f, .74f, 0f, .4f);
        painter.shaded(0f, 1f, 0f, 1f, .2f, .2f, .2f, .8f, .8f, .8f);
        painter.quad(0f, 1f, .1f, .3f, .5f, .5f, .5f);
        boolean hole = false, wall = false;
        for (int q = 0; q < painter.faces.quadCount(); q++) {
            hole |= inside(painter, q, px, py);
            wall |= inside(painter, q, qx, qy);
        }
        assertFalse(hole, "the door's hole is left open");
        assertTrue(wall, "the wall beside it is painted");
    }

    /** Whether {@code (x, y)} lies inside quad {@code q}, a convex quad wound either way. */
    private static boolean inside(FacePainter painter, int q, float x, float y) {
        int sign = 0;
        for (int k = 0; k < 4; k++) {
            float ax = painter.faces.vertexX(q, k), ay = painter.faces.vertexY(q, k);
            float bx = painter.faces.vertexX(q, (k + 1) % 4), by = painter.faces.vertexY(q, (k + 1) % 4);
            float cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax);
            int s = cross > 1e-6f ? 1 : cross < -1e-6f ? -1 : 0;
            if (s == 0) continue;
            if (sign == 0) sign = s;
            else if (s != sign) return false;
        }
        return true;
    }
}
