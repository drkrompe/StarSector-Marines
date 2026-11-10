package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingFacesTest {

    static final int GRID_W = 10;

    /** A 2x2 roof at (3,3) whose outer edges stop short by an eave: 0.7 of a cell in from its building's side. */
    private static float[][] rects(int at) {
        int x = at % GRID_W, y = at / GRID_W;
        float x0 = x == 3 ? .3f : 0f, x1 = x == 4 ? .7f : 1f, y0 = y == 3 ? .3f : 0f, y1 = y == 4 ? .7f : 1f;
        return new float[][]{{x0, y0, x1, y1}};
    }

    private static final int[] CELLS = {33, 34, 43, 44};

    @Test
    void theOutlineIsTheRoofAsDrawnNotItsCells() {
        float[] edges = BuildingFaces.outline(CELLS, GRID_W, BuildingFacesTest::rects);
        assertEquals(4 * 6, edges.length, "one straight run a side");
        for (int e = 0; e < edges.length; e += 6) {
            for (int k = 0; k < 4; k += 2) {
                float px = edges[e + k], py = edges[e + k + 1];
                assertTrue(near(px, 3.3f) || near(px, 4.7f), "x on the eave line: " + px);
                assertTrue(near(py, 3.3f) || near(py, 4.7f), "y on the eave line: " + py);
            }
            float mx = (edges[e] + edges[e + 2]) / 2f, my = (edges[e + 1] + edges[e + 3]) / 2f;
            assertTrue(edges[e + 4] * (mx - 4f) + edges[e + 5] * (my - 4f) > 0f, "normals point out");
        }
    }

    @Test
    void aWallsTopIsTheRoofsEdgeAtAnyView() {
        float[] edges = BuildingFaces.outline(CELLS, GRID_W, BuildingFacesTest::rects);
        float lift = BuildingFaces.lift(BuildingFaces.HEIGHTS[2]);
        for (float c : new float[]{-40f, 0f, 3.7f, 4f, 12.5f, 90f}) {
            for (int e = 0; e < edges.length; e += 6) {
                for (int k = 0; k < 4; k += 2) {
                    float p = edges[e + k];
                    // The roof's piece is drawn as its centre lifted and its size scaled by the lift, about the
                    // view's centre; its corner on this edge must be the wall's top.
                    float centre = 4f, half = .7f;
                    float roofCorner = BuildingFaces.lifted(centre, c, lift) + Math.signum(p - centre) * half * lift;
                    assertEquals(roofCorner, BuildingFaces.lifted(p, c, lift), 1e-4f, "view at " + c);
                }
            }
        }
    }

    @Test
    void onlyTheWallsTurnedToTheEyeAreFacesAndTallerRoofsLiftMore() {
        assertTrue(BuildingFaces.facing(1f, 0f, 5f, 0f, 10f, 0f));
        assertFalse(BuildingFaces.facing(-1f, 0f, 2f, 0f, 10f, 0f));
        assertEquals(1f, BuildingFaces.lift(0f), 1e-6f);
        assertTrue(BuildingFaces.lift(BuildingFaces.HEIGHTS[BuildingFaces.heightClass(400)])
                > BuildingFaces.lift(BuildingFaces.HEIGHTS[BuildingFaces.heightClass(20)]));
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }
}
