package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallCornersTest {

    /** {@code #} wall, {@code .} open, {@code D} doorway; row 0 is the top (north) line. */
    private static WallCorners.Cells grid(String... rows) {
        int h = rows.length;
        return new WallCorners.Cells() {
            char at(int x, int y) {
                if (y < 0 || y >= h || x < 0 || x >= rows[0].length()) return 0;
                return rows[h - 1 - y].charAt(x);
            }

            @Override public boolean wall(int x, int y) { return at(x, y) == '#'; }

            @Override public boolean open(int x, int y) { return at(x, y) == '.'; }
        };
    }

    @Test
    void aBuildingRoundsItsOutsideCornersAndNotItsSides() {
        WallCorners.Cells cells = grid(
                ".....",
                ".###.",
                ".###.",
                ".....");
        // The top-right wall cell is (3, 2).
        assertTrue(WallCorners.outside(cells, 3, 2, WallCorners.NE));
        assertFalse(WallCorners.outside(cells, 3, 2, WallCorners.SE));
        assertFalse(WallCorners.outside(cells, 2, 2, WallCorners.NE));
    }

    @Test
    void aRoomFilletsItsInsideCornersOnly() {
        WallCorners.Cells cells = grid(
                "#####",
                "#...#",
                "#...#",
                "#####");
        assertTrue(WallCorners.inside(cells, 3, 2, WallCorners.NE));
        assertTrue(WallCorners.inside(cells, 1, 1, WallCorners.SW));
        assertFalse(WallCorners.inside(cells, 2, 2, WallCorners.NE));
    }

    @Test
    void aDoorJambKeepsItsSquareEdge() {
        WallCorners.Cells cells = grid(
                ".....",
                "##D##",
                ".....");
        assertFalse(WallCorners.outside(cells, 1, 1, WallCorners.NE));
        assertFalse(WallCorners.inside(cells, 2, 1, WallCorners.NE));
    }

    @Test
    void aBreachLeavesItsNewWallEndsSquare() {
        WallCorners.Cells built = grid(
                ".......",
                ".#####.",
                ".......");
        WallCorners.AsBuilt asBuilt = WallCorners.AsBuilt.of(7, 3, built);
        WallCorners.Cells breached = grid(
                ".......",
                ".##.##.",
                ".......");
        assertTrue(WallCorners.outside(breached, 2, 1, WallCorners.NE), "the rule alone would round it");
        assertFalse(asBuilt.outside(breached, 2, 1, WallCorners.NE));
        assertFalse(asBuilt.outside(breached, 4, 1, WallCorners.SW));
        assertTrue(asBuilt.outside(breached, 1, 1, WallCorners.NW), "an untouched corner stays round");
    }

    @Test
    void aFallenWallTakesItsCornersWithIt() {
        WallCorners.AsBuilt asBuilt = WallCorners.AsBuilt.of(4, 4, grid(
                "####",
                "#..#",
                "#..#",
                "####"));
        WallCorners.Cells fallen = grid(
                "###.",
                "#..#",
                "#..#",
                "####");
        assertFalse(asBuilt.inside(fallen, 2, 2, WallCorners.NE));
        assertTrue(asBuilt.inside(fallen, 1, 1, WallCorners.SW));
    }

    @Test
    void everyPieceSamplesInsideItsTile() {
        for (int q = 0; q < 4; q++) {
            for (WallCorners.Piece[] pieces : new WallCorners.Piece[][]{
                    WallCorners.outsidePieces(q), WallCorners.insidePieces(q)}) {
                for (WallCorners.Piece piece : pieces) {
                    float[] p = piece.patch();
                    for (int i = 8; i < 16; i++) {
                        assertTrue(p[i] >= -1e-5f && p[i] <= 1 + 1e-5f, "uv " + p[i] + " in quarter " + q);
                    }
                }
            }
        }
    }

    @Test
    void anOutsideCornerStaysInItsOwnQuarter() {
        for (int q = 0; q < 4; q++) {
            float cx = WallCorners.SX[q] > 0 ? .75f : .25f, cy = WallCorners.SY[q] > 0 ? .75f : .25f;
            for (WallCorners.Piece piece : WallCorners.outsidePieces(q)) {
                float[] p = piece.patch();
                for (int i = 0; i < 8; i += 2) {
                    assertTrue(Math.abs(p[i] - cx) <= .25f + 1e-5f && Math.abs(p[i + 1] - cy) <= .25f + 1e-5f);
                }
            }
        }
    }
}
