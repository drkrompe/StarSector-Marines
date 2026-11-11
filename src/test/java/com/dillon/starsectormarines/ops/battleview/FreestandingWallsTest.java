package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreestandingWallsTest {

    @Test
    void aWallsHeightFollowsItsKind() {
        assertEquals(FreestandingWalls.Kind.SEALED_PERIMETER, FreestandingWalls.kind("colony.ceramic-wall", 0));
        assertEquals(FreestandingWalls.Kind.SEALED_PERIMETER,
                FreestandingWalls.kind("installation.light-concrete-wall@brutalist", 0));
        assertEquals(FreestandingWalls.Kind.INSTALLATION, FreestandingWalls.kind("installation.dark-concrete-wall", 0));
        assertEquals(FreestandingWalls.Kind.BLAST, FreestandingWalls.kind("installation.light-concrete-wall", 0));
        assertEquals(FreestandingWalls.Kind.BLAST, FreestandingWalls.kind("colony.masonry-wall", 3));
        assertEquals(FreestandingWalls.Kind.COMPOUND, FreestandingWalls.kind("colony.masonry-wall", 0));
        assertEquals(FreestandingWalls.Kind.GARDEN, FreestandingWalls.kind("colony.olive-wall", 0));
        assertEquals(FreestandingWalls.Kind.ENVELOPE, FreestandingWalls.kind("habitat.envelope-wall", 0));
        assertEquals(FreestandingWalls.Kind.ENVELOPE, FreestandingWalls.kind("habitat.envelope-wall@buttress", 0));
        assertEquals(FreestandingWalls.Kind.ENVELOPE_PLINTH, FreestandingWalls.kind("habitat.envelope-wall@plinth", 0));
        assertEquals(FreestandingWalls.Kind.UNKNOWN, FreestandingWalls.kind(null, 0));
        assertEquals(FreestandingWalls.Kind.NONE, FreestandingWalls.kind("ship.neutral-bulkhead", 0));
        int standing = FreestandingWalls.PERIMETER_CELLS;
        for (FreestandingWalls.Kind k : FreestandingWalls.Kind.values())
            assertEquals(k.meters, FreestandingWalls.height(k, standing));
    }

    @Test
    void theSealedPerimeterStandsAboveAGardenWallButNotAPodInItsShell() {
        int standing = FreestandingWalls.PERIMETER_CELLS;
        float sealed = FreestandingWalls.height(FreestandingWalls.Kind.SEALED_PERIMETER, standing);
        assertTrue(sealed > FreestandingWalls.height(FreestandingWalls.Kind.INSTALLATION, standing));
        assertTrue(sealed > FreestandingWalls.height(FreestandingWalls.Kind.GARDEN, standing));
        assertTrue(FreestandingWalls.height(FreestandingWalls.Kind.SEALED_PERIMETER, standing - 1)
                <= FreestandingWalls.height(FreestandingWalls.Kind.COMPOUND, standing));
        assertTrue(FreestandingWalls.height(FreestandingWalls.Kind.INSTALLATION, FreestandingWalls.STUB_CELLS - 1)
                <= FreestandingWalls.height(FreestandingWalls.Kind.COMPOUND, standing), "a ruin's stub");
    }

    @Test
    void anEnvelopeStandsAboveEveryOtherWallOverAPlinthBelowIt() {
        int standing = FreestandingWalls.PERIMETER_CELLS;
        float body = FreestandingWalls.height(FreestandingWalls.Kind.ENVELOPE, standing);
        float plinth = FreestandingWalls.height(FreestandingWalls.Kind.ENVELOPE_PLINTH, standing);
        assertTrue(body >= FreestandingWalls.height(FreestandingWalls.Kind.SEALED_PERIMETER, standing));
        assertTrue(plinth < body);
        assertEquals(FreestandingWalls.Part.BODY, FreestandingWalls.part("habitat.envelope-wall"));
        assertEquals(FreestandingWalls.Part.BUTTRESS, FreestandingWalls.part("habitat.envelope-wall@buttress"));
        assertEquals(FreestandingWalls.Part.PLINTH, FreestandingWalls.part("habitat.envelope-wall@plinth"));
        assertEquals(null, FreestandingWalls.part("installation.dark-concrete-wall"));
    }

    @Test
    void aRunsOutlineIsTheUnionOfItsCells() {
        // An L of three masonry cells, a lone olive cell beside it, a building's wall left out.
        //   y=1: M . .
        //   y=0: M M O
        int w = 4, h = 3;
        Set<Integer> masonry = Set.of(0, 1, w);
        List<FreestandingWalls.Run> runs = FreestandingWalls.runs(w, h,
                at -> masonry.contains(at) || at == 2 || at == 3,
                at -> at == 3 ? FreestandingWalls.Kind.NONE
                        : at == 2 ? FreestandingWalls.Kind.GARDEN : FreestandingWalls.Kind.COMPOUND);
        assertEquals(2, runs.size());
        assertEquals(FreestandingWalls.Kind.GARDEN, runs.get(0).kind(), "lowest first");
        FreestandingWalls.Run l = runs.get(1);
        assertEquals(Set.of(0, 1, w), cells(l.cells()));
        // The L's boundary: 8 unit sides, none between two of its own cells.
        Set<String> sides = new HashSet<>();
        float[] s = l.segments();
        for (int e = 0; e < s.length; e += BuildingFaces.SEG) {
            float mx = (s[e] + s[e + 2]) / 2f, my = (s[e + 1] + s[e + 3]) / 2f;
            sides.add(mx + "," + my + "," + s[e + 4] + "," + s[e + 5]);
            int inside = (int) Math.floor(mx - s[e + 4] * .5f) + (int) Math.floor(my - s[e + 5] * .5f) * w;
            int outside = (int) Math.floor(mx + s[e + 4] * .5f) + (int) Math.floor(my + s[e + 5] * .5f) * w;
            assertTrue(masonry.contains(inside), "inside is the run");
            assertTrue(!masonry.contains(outside) || mx + s[e + 4] * .5f < 0 || my + s[e + 5] * .5f < 0,
                    "outside is not");
        }
        assertEquals(8, sides.size());
        assertEquals(8, s.length / BuildingFaces.SEG);
    }

    private static Set<Integer> cells(int[] cells) {
        Set<Integer> out = new HashSet<>();
        for (int c : cells) out.add(c);
        return out;
    }

    /**
     * A diagonal wall standing free is one slanted stroke, both ends free and cut square across, each reaching only
     * to the middles of its end cell's far sides: no pier's square corner.
     */
    @Test
    void aFreeDiagonalEndsCutSquareInsideItsCell() {
        int w = 12, h = 12;
        int[] cells = {2 + 2 * w, 3 + 3 * w, 4 + 4 * w, 5 + 5 * w, 6 + 6 * w};
        Set<Integer> in = set(cells);
        float[] s = FreestandingWalls.strokes(cells, w, h, in::contains);
        assertArrayEquals(new float[]{2.5f, 2.5f, 6.5f, 6.5f, 1f, 1f}, s, "one stroke, no piers");
        float[] q = new float[8];
        FreestandingWalls.corners(s, 0, FreestandingWalls.HALF, q);
        // The far end's cut has its corners on cell (6, 6)'s right and top sides, inside the cell.
        for (int c = 2; c <= 4; c += 2) {
            assertTrue(q[c] <= 7f + 1e-4f && q[c + 1] <= 7f + 1e-4f, "inside the cell");
            assertEquals(7f, Math.max(q[c], q[c + 1]), 1e-4f, "on its far side");
        }
    }

    /** A diagonal joined to a straight wall stops inside that wall's cell; two diagonals meeting stand on a pier. */
    @Test
    void aJoinedDiagonalEndsInTheWallItMeetsOrOnAPier() {
        int w = 12, h = 12;
        int[] into = {0, 1, 2, 3 + w, 4 + 2 * w};
        Arrays.sort(into);
        Set<Integer> a = set(into);
        float[] s = FreestandingWalls.strokes(into, w, h, a::contains);
        assertEquals(2 * FreestandingWalls.STROKE, s.length, "a straight wall and a diagonal, no pier");
        int[] vee = {0, 1 + w, 2 + 2 * w, 3 + w, 4};
        Arrays.sort(vee);
        Set<Integer> v = set(vee);
        float[] t = FreestandingWalls.strokes(vee, w, h, v::contains);
        assertEquals(3 * FreestandingWalls.STROKE, t.length, "two diagonals and the pier where they meet");
    }

    /**
     * An L of wall is two straight strokes meeting at the corner, each end there joined; a lone cell is a post; a
     * stair of cells joined side to side stays square, not slanted.
     */
    @Test
    void straightWallsAndPostsStaySquare() {
        int w = 12, h = 12;
        int[] l = {1 + 1 * w, 2 + 1 * w, 3 + 1 * w, 1 + 2 * w, 1 + 3 * w};
        Arrays.sort(l);
        Set<Integer> in = set(l);
        float[] s = FreestandingWalls.strokes(l, w, h, in::contains);
        assertEquals(2 * FreestandingWalls.STROKE, s.length);
        for (int o = 0; o < s.length; o += FreestandingWalls.STROKE) {
            assertTrue(s[o] == s[o + 2] || s[o + 1] == s[o + 3], "straight");
            assertEquals(0f, s[o + 4], "joined at the corner");
            assertEquals(1f, s[o + 5], "free at its far end");
        }
        float[] post = FreestandingWalls.strokes(new int[]{5 + 5 * w}, w, h, at -> at == 5 + 5 * w);
        assertEquals(FreestandingWalls.STROKE, post.length);
        assertEquals(post[0], post[2]);
        int[] stair = {1 + 1 * w, 2 + 1 * w, 2 + 2 * w, 3 + 2 * w};
        Set<Integer> st = set(stair);
        float[] t = FreestandingWalls.strokes(stair, w, h, st::contains);
        for (int o = 0; o < t.length; o += FreestandingWalls.STROKE) {
            assertTrue(t[o] == t[o + 2] || t[o + 1] == t[o + 3], "no diagonal across a side-joined stair");
        }
    }

    /**
     * A diagonal stands exactly as thick as a straight wall, and stops at its end cells' centres, so every corner of
     * its footprint lies inside its end cells' piers: it snaps to the grid where it meets a straight wall.
     */
    @Test
    void aDiagonalIsAsThickAsAStraightWallAndEndsInsideItsPiers() {
        float[] straight = new float[8], diagonal = new float[8];
        FreestandingWalls.corners(2.5f, 2.5f, 6.5f, 2.5f, FreestandingWalls.HALF, straight);
        FreestandingWalls.corners(2.5f, 2.5f, 6.5f, 6.5f, FreestandingWalls.HALF, diagonal);
        assertEquals(dist(straight, 0, 6), dist(diagonal, 0, 6), 1e-5f, "same thickness");
        assertEquals(1f, dist(straight, 0, 6), 1e-5f, "a cell thick");
        for (int c = 0; c < 4; c++) {
            float x = diagonal[c * 2], y = diagonal[c * 2 + 1];
            float cx = c == 0 || c == 3 ? 2.5f : 6.5f;
            assertTrue(Math.abs(x - cx) <= .5f + 1e-5f && Math.abs(y - cx) <= .5f + 1e-5f, "corner " + c + " in its pier");
        }
        for (int k = 1; k < 4; k++) assertTrue(inside(diagonal, 2.5f + k, 2.5f + k), "cell " + k);
    }

    private static float dist(float[] q, int a, int b) {
        return (float) Math.hypot(q[a] - q[b], q[a + 1] - q[b + 1]);
    }

    private static boolean inside(float[] q, float x, float y) {
        boolean sign = false;
        for (int i = 0; i < 4; i++) {
            float ax = q[i * 2], ay = q[i * 2 + 1], bx = q[(i * 2 + 2) % 8], by = q[(i * 2 + 3) % 8];
            float cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax);
            if (i == 0) sign = cross > 0;
            else if ((cross > 0) != sign) return false;
        }
        return true;
    }

    /**
     * Where strokes meet (an L, a T, a diagonal into a straight wall, a diagonal that jogs) the run stands as one wall:
     * its outline closes, every piece of it has open ground outside and wall inside, and no piece is drawn twice, so
     * no face or coping crosses a joint.
     */
    @Test
    void whereStrokesMeetTheRunStandsAsOneOutline() {
        int w = 16;
        int[][] shapes = {
                {0, 0, 1, 0, 2, 0, 3, 0, 0, 1, 0, 2, 0, 3},                 // an L
                {0, 2, 1, 2, 2, 2, 3, 2, 4, 2, 2, 3, 2, 4, 2, 5},           // a T
                {0, 0, 1, 0, 2, 0, 3, 0, 4, 1, 5, 2, 6, 3, 7, 3, 8, 3},     // straight, diagonal, straight
                {0, 0, 1, 1, 2, 2, 3, 2, 4, 3, 5, 4},                       // a diagonal that jogs
                {2, 2, 3, 3, 4, 4},                                         // a diagonal alone, on its piers
        };
        for (int[] xy : shapes) {
            int[] cells = new int[xy.length / 2];
            for (int k = 0; k < cells.length; k++) cells[k] = xy[2 * k] + xy[2 * k + 1] * w;
            Arrays.sort(cells);
            Set<Integer> in = set(cells);
            float[] strokes = FreestandingWalls.strokes(cells, w, w, in::contains);
            float[] edges = FreestandingWalls.edges(strokes, w);
            String shape = Arrays.toString(xy);
            assertTrue(edges.length > 0, shape);
            int e = FreestandingWalls.EDGE;
            for (int a = 0; a < edges.length; a += e) {
                float mx = (edges[a] + edges[a + 2]) / 2f, my = (edges[a + 1] + edges[a + 3]) / 2f;
                float nx = edges[a + 4], ny = edges[a + 5];
                assertTrue(!wall(strokes, mx + nx * .01f, my + ny * .01f), shape + " open outside " + a);
                assertTrue(wall(strokes, mx - nx * .01f, my - ny * .01f), shape + " wall inside " + a);
                assertTrue(ends(edges, a, edges[a], edges[a + 1]) && ends(edges, a, edges[a + 2], edges[a + 3]),
                        shape + " closed at " + a);
                for (int b = a + e; b < edges.length; b += e) {
                    float bx = (edges[b] + edges[b + 2]) / 2f, by = (edges[b + 1] + edges[b + 3]) / 2f;
                    assertTrue(Math.hypot(mx - bx, my - by) > 1e-3, shape + " drawn twice " + a);
                }
            }
        }
    }

    /** Whether a point stands on any stroke's footprint. */
    private static boolean wall(float[] strokes, float x, float y) {
        float[] q = new float[8];
        for (int o = 0; o < strokes.length; o += FreestandingWalls.STROKE) {
            FreestandingWalls.corners(strokes, o, FreestandingWalls.HALF, q);
            if (FreestandingWalls.contains(q, x, y)) return true;
        }
        return false;
    }

    /** Whether another piece of the outline ends where piece {@code a} does at {@code (x, y)}. */
    private static boolean ends(float[] edges, int a, float x, float y) {
        for (int b = 0; b < edges.length; b += FreestandingWalls.EDGE) {
            if (b == a) continue;
            if (Math.hypot(edges[b] - x, edges[b + 1] - y) < 1e-3 || Math.hypot(edges[b + 2] - x, edges[b + 3] - y) < 1e-3)
                return true;
        }
        return false;
    }

    private static Set<Integer> set(int[] cells) {
        Set<Integer> out = new HashSet<>();
        for (int c : cells) out.add(c);
        return out;
    }
}
