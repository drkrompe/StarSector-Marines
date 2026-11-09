package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the void backdrop splits a surface into the towers it stands on and the walkways between them, and rounds
 * a footprint's corners.
 */
class VoidBackdropTowersTest {

    static final int MW = 40, MH = 20;

    @Test
    void groundNarrowerThanASpanIsWalkwayAndWiderStandsOnATower() {
        boolean[] surface = new boolean[MW * MH];
        fill(surface, 2, 2, 12, 12);    // a tower roof
        fill(surface, 12, 6, 22, 8);    // a two-cell catwalk off it
        fill(surface, 22, 2, 29, 9);    // a roof just a span wide
        boolean[] towers = VoidBackdropRenderSystem.towers(surface, MW, MH);
        assertTrue(towers[at(2, 2)], "a roof's corner stands on its tower");
        assertTrue(towers[at(11, 11)]);
        assertFalse(towers[at(16, 7)], "a catwalk is a walkway");
        assertTrue(towers[at(25, 5)], "ground a span wide stands on a tower");
        assertFalse(towers[at(30, 5)], "nothing off the surface is a tower");
    }

    @Test
    void onlyConvexCornersRoundAndTheirRunsAreCutBack() {
        // An L: a 20 x 10 bar with a 10 x 10 block on its left end, so five convex corners and one concave.
        List<float[]> runs = new ArrayList<>(List.of(
                new float[]{0, 0, 10, 0, 0, -1}, new float[]{10, 10, 20, 10, 0, -1}, new float[]{0, 20, 20, 20, 0, 1},
                new float[]{0, 0, 0, 20, -1, 0}, new float[]{10, 0, 10, 10, 1, 0}, new float[]{20, 10, 20, 20, 1, 0}));
        List<float[]> corners = new ArrayList<>();
        VoidBackdropRenderSystem.roundCorners(runs, 3, corners);
        assertEquals(5, corners.size(), "the L's inside corner stays square");
        assertArrayEquals(new float[]{3, 3, 3, 0, -1, -1, 0}, corners.get(0), "centred a radius in from the corner");
        assertArrayEquals(new float[]{3, 0, 7, 0, 0, -1}, runs.get(0), "the top run is cut at both its convex ends");
        assertArrayEquals(new float[]{10, 3, 10, 10, 1, 0}, runs.get(4), "and the inner run only at its convex end");
    }

    @Test
    void aCornerNoMoreThanHalfItsShortestRun() {
        List<float[]> runs = new ArrayList<>(List.of(
                new float[]{0, 0, 6, 0, 0, -1}, new float[]{0, 0, 0, 30, -1, 0}));
        List<float[]> corners = new ArrayList<>();
        VoidBackdropRenderSystem.roundCorners(runs, 10, corners);
        assertEquals(3f, corners.get(0)[2]);
    }

    private static void fill(boolean[] mask, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) mask[at(x, y)] = true;
    }

    private static int at(int x, int y) {
        return y * MW + x;
    }
}
