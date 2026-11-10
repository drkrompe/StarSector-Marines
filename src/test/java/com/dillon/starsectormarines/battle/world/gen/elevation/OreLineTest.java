package com.dillon.starsectormarines.battle.world.gen.elevation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreLineTest {

    static final int W = 20, H = 12;

    /** A building in columns 14..17, rows 3..8; the line feeds it from the west. */
    static boolean building(int i) {
        int x = i % W, y = i / W;
        return x >= 14 && x <= 17 && y >= 3 && y <= 8;
    }

    static boolean besideBuilding(int i) {
        int x = i % W, y = i / W;
        return !building(i) && y >= 3 && y <= 8 && x == 13;
    }

    /** The course never crosses what it may not and reaches the building, turning as little as it can. */
    @Test
    void theLineRunsStraightAroundWhatItMayNotCross() {
        // A pit's edge in columns 4..5 from row 0 to row 9: the line has to go round its south end.
        List<int[]> path = OreLine.route(W, H, 1, 2, i -> !building(i) && !(i % W >= 4 && i % W <= 5 && i / W <= 9),
                OreLineTest::besideBuilding);
        assertFalse(path.isEmpty());
        int[] last = path.get(path.size() - 1);
        assertEquals(13, last[0], "the line ends beside the building");
        int turns = 0;
        for (int i = 1; i + 1 < path.size(); i++) {
            int[] a = path.get(i - 1), b = path.get(i), c = path.get(i + 1);
            int[] p = path.get(i);
            assertFalse(p[0] >= 4 && p[0] <= 5 && p[1] <= 9, "through the pit");
            if (b[0] - a[0] != c[0] - b[0] || b[1] - a[1] != c[1] - b[1]) turns++;
        }
        assertTrue(turns <= 4, "a stair of corners: " + turns + " turns");
    }

    /**
     * A straight piece is turned to the way the ore flows, a corner to the two sides it joins, and the last piece is
     * the drive facing the building.
     */
    @Test
    void piecesFollowTheFlow() {
        // East along row 2, a corner, north up column 3, ending into the cell above.
        List<int[]> path = List.of(new int[]{1, 2}, new int[]{2, 2}, new int[]{3, 2}, new int[]{3, 3},
                new int[]{3, 4});
        List<OreLine.Piece> pieces = OreLine.pieces(path, 3, 5, true);
        assertEquals(OreLine.Shape.STRAIGHT, pieces.get(0).shape());
        assertEquals(0, pieces.get(0).quarterTurns(), "flowing east is the art's own way");
        assertEquals(OreLine.Shape.CORNER, pieces.get(2).shape());
        // Coming in from the west, leaving north: the corner's own art.
        assertEquals(0, pieces.get(2).quarterTurns());
        assertEquals(OreLine.Shape.STRAIGHT, pieces.get(3).shape());
        assertEquals(1, pieces.get(3).quarterTurns(), "flowing north is one turn on");
        assertEquals(OreLine.Shape.DRIVE, pieces.get(4).shape());
        assertEquals(1, pieces.get(4).quarterTurns(), "the drive faces the building to the north");
        // With y down the map's north is -y: the same course climbs south.
        assertEquals(3, OreLine.pieces(path, 3, 5, false).get(3).quarterTurns());
    }

    /** Legs stand on straight pieces a run apart, and only where a leg may stand. */
    @Test
    void legsStandAlongStraightRunsWhereTheyMay() {
        List<int[]> path = new ArrayList<>();
        for (int x = 0; x < 19; x++) path.add(new int[]{x, 5});
        List<OreLine.Piece> pieces = OreLine.pieces(path, 19, 5, true);
        List<int[]> legs = OreLine.legs(pieces, i -> i % W != 10, W);
        assertTrue(legs.size() >= 3, "too few legs: " + legs.size());
        for (int i = 1; i < legs.size(); i++) {
            int gap = legs.get(i)[0] - legs.get(i - 1)[0];
            assertTrue(gap >= OreLine.LEG_EVERY - 2 && gap <= OreLine.LEG_EVERY + 2, "gap " + gap);
        }
        for (int[] leg : legs) assertTrue(leg[0] != 10, "a leg where none may stand");
    }
}
