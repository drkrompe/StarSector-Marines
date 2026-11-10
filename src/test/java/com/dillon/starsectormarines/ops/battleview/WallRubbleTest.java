package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallRubbleTest {

    /** A wall sheds nothing until it is badly hurt, and most when breached. */
    @Test
    void aWallShedsRubbleByWhatHasComeDown() {
        assertEquals(0, WallRubble.amount(false, false, .2f));
        assertEquals(WallRubble.HURT, WallRubble.amount(false, false, .6f));
        assertEquals(WallRubble.BROKEN, WallRubble.amount(false, true, .6f));
        assertEquals(WallRubble.BREACHED, WallRubble.amount(true, false, 0f));
    }

    /** A heap's top edge lies on the wall's foot; every other piece lies out on the open ground beyond it. */
    @Test
    void rubbleLiesOnTheOpenGroundOutsideItsWall() {
        float[] out = new float[WallRubble.PIECE * WallRubble.MOST];
        for (int amount = WallRubble.HURT; amount <= WallRubble.BREACHED; amount++) {
            for (int cell = 0; cell < 400; cell += 7) {
                int n = WallRubble.place(cell, amount, out, 0);
                assertTrue(n > 0 && n <= WallRubble.MOST);
                for (int k = 0; k < n; k++) {
                    int p = k * WallRubble.PIECE;
                    float t = out[p], dist = out[p + 1], size = out[p + 2];
                    int row = (int) out[p + 4] / WallRubble.GRID;
                    assertTrue(t >= 0f && t <= 1f, "along its wall");
                    if (row == WallRubble.HEAP) {
                        assertEquals(dist - (.5f - WallRubble.HEAP_TOP) * size, 0f, 1e-5f, "heap against the foot");
                    } else {
                        assertTrue(dist >= size * .4f, "clear of the wall: " + dist + " for " + size);
                    }
                }
            }
        }
    }

    /** Laid at a wall turned any way, every piece lands on its outward side, a heap turned with its top to the wall. */
    @Test
    void rubbleIsLaidOutwardWhicheverWayTheWallFaces() {
        float[][] normals = {{0f, 1f}, {0f, -1f}, {1f, 0f}, {-1f, 0f}};
        for (float[] n : normals) {
            FacePainter painter = new FacePainter();
            painter.reset();
            // A one-cell wall foot at the origin, one cell = 32 px.
            float ux = -n[1], uy = n[0];
            painter.wall(0f, 0f, 32f * ux, 32f * uy, 0f, 0f, 32f * ux, 32f * uy, 500f, 500f, 1.05f,
                    32f * n[0], 32f * n[1], 1f);
            painter.rubble(17, WallRubble.BREACHED, new float[]{.6f, .5f, .4f}, 1f);
            assertTrue(painter.debrisCount() > 0);
            for (int i = 0; i < painter.debrisCount(); i++) {
                int o = i * FacePainter.DEBRIS;
                float x = painter.debris()[o], y = painter.debris()[o + 1];
                assertTrue(x * n[0] + y * n[1] > 0f, "outside the wall");
                if ((int) painter.debris()[o + 4] / WallRubble.GRID == WallRubble.HEAP) {
                    double turned = Math.toRadians(painter.debris()[o + 3]);
                    // The heap's top (+y unturned) points back at the wall, within its small jitter.
                    float topX = (float) -Math.sin(turned), topY = (float) Math.cos(turned);
                    assertTrue(topX * -n[0] + topY * -n[1] > .95f, "heap's top to the wall");
                }
            }
        }
    }
}
