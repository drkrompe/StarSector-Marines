package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingFacesTest {

    @Test
    void aBlocksOutlineIsItsOuterCellSidesWithNormalsOutward() {
        int gridW = 10;
        // A 2x2 block at (3,3).
        float[] edges = BuildingFaces.edges(new int[]{33, 34, 43, 44}, gridW);
        assertEquals(8 * 6, edges.length, "two sides of each outer cell, none between them");
        for (int e = 0; e < edges.length; e += 6) {
            float mx = (edges[e] + edges[e + 2]) / 2f, my = (edges[e + 1] + edges[e + 3]) / 2f;
            // Out from the block's centre at (4,4).
            assertTrue(edges[e + 4] * (mx - 4f) + edges[e + 5] * (my - 4f) > 0f);
        }
    }

    @Test
    void onlyTheWallsTurnedToTheEyeAreFacesAndARoofLiftsWithItsHeight() {
        // A wall on the east of a building west of the eye faces it; the west wall does not.
        assertTrue(BuildingFaces.facing(1f, 0f, 5f, 0f, 10f, 0f));
        assertFalse(BuildingFaces.facing(-1f, 0f, 2f, 0f, 10f, 0f));
        assertEquals(1f, BuildingFaces.lift(0f), 1e-6f);
        assertTrue(BuildingFaces.lift(BuildingFaces.heightMeters(400)) > BuildingFaces.lift(BuildingFaces.heightMeters(20)));
    }
}
