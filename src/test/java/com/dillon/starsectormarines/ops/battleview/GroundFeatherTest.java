package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ops.battleview.GroundFeather.Shape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroundFeatherTest {

    @Test void aStraightEdgeKeepsItsLineAndAStepIsCutToASlope() {
        // Four cells wide, two high: the material along the south row, and one step up on the east.
        byte I = GroundFeather.INSIDE, O = GroundFeather.OUTSIDE;
        byte[] cells = {I, I, I, I, O, O, I, I};
        float[] c = GroundFeather.smoothCorners(cells, 4, 2, 1);
        // Corner (1, 1) sits on the straight edge between the rows: exactly half.
        assertEquals(.5f, c[5 + 1]);
        // Corner (2, 1), at the foot of the step, holds more than half: the contour leaves the cell edge there.
        assertEquals(.75f, c[5 + 2]);
    }

    @Test void aCellIsSplitAlongTheContourIntoItsTwoSides() {
        // Only the south-west corner holds the material, cut halfway along its two sides.
        float[] tri = GroundFeather.contour(1f, 0f, 0f, 0f, true).get(0);
        assertArrayEquals(new float[]{0f, 0f, .5f, 0f, 0f, .5f, 0f, .5f}, tri);
        // The rest of the cell is a pentagon: two pieces.
        assertEquals(2, GroundFeather.contour(1f, 0f, 0f, 0f, false).size());
        // A cell along a straight edge has nothing on the far side.
        assertEquals(0, GroundFeather.contour(.5f, .5f, 0f, 0f, true).size());
    }

    @Test void onlyStrongerGroundBleedsInWeakestFirst() {
        int[] out = new int[3];
        // Sand (5) under grass (0) and dirt (2), with sand and grass again at the diagonal.
        int n = GroundFeather.winners(5, 0, 2, 0, out);
        assertEquals(2, n);
        assertArrayEquals(new int[]{2, 0}, new int[]{out[0], out[1]});
        assertEquals(0, GroundFeather.winners(2, 5, 2, -1, out), "weaker, equal and non-feathering ground take nothing");
        assertEquals(0, GroundFeather.winners(-1, 0, 0, 0, out), "ground that does not feather receives nothing");
    }

    @Test void anIndoorFloorTakesOnlyACoveringsBleed() {
        int[] out = new int[3];
        int ice = GroundFeather.priority("ruins-ground.ice-sheet"), earth = GroundFeather.priority("pavement.dirt.worn");
        assertEquals(1, GroundFeather.winners(GroundFeather.FLOOR, ice, earth, -1, out));
        assertEquals(ice, out[0], "the ice bleeds onto the hall's floor, the trodden earth does not");
    }

    @Test void aCornerReadsAsEdgeInnerOrOuter() {
        assertEquals(Shape.EDGE_N, GroundFeather.shape(GroundFeather.NE, true, false, true));
        assertEquals(Shape.EDGE_W, GroundFeather.shape(GroundFeather.SW, false, true, false));
        assertEquals(Shape.INNER_SE, GroundFeather.shape(GroundFeather.SE, true, true, false));
        assertEquals(Shape.OUTER_NW, GroundFeather.shape(GroundFeather.NW, false, false, true));
        assertNull(GroundFeather.shape(GroundFeather.NE, false, false, false));
    }

    @Test void neighboursAgreeOnTheEdgeBetweenThem() {
        for (int y = -20; y < 20; y++) {
            for (int x = -20; x < 20; x++) {
                int code = GroundFeather.edgeCode(x, y);
                assertEquals(code & 1, GroundFeather.edgeCode(x, y + 1) >> 2 & 1, "north of " + x + "," + y);
                assertEquals(code >> 1 & 1, GroundFeather.edgeCode(x + 1, y) >> 3 & 1, "east of " + x + "," + y);
            }
        }
    }

    @Test void tilesStayInsideTheirBlock() {
        for (Shape shape : Shape.values()) {
            for (int y = -4; y < 4; y++) for (int x = -4; x < 4; x++) {
                int index = GroundFeather.tileIndex(shape, x, y);
                assertEquals(shape.ordinal(), index / GroundFeather.EDGE_CODES);
                assertEquals(true, index < GroundFeather.tilesPerMaterial());
            }
        }
    }
}
