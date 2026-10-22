package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.ops.battleview.GroundFeather.Shape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroundFeatherTest {

    @Test void onlyStrongerGroundBleedsInWeakestFirst() {
        int[] out = new int[3];
        // Sand (5) under grass (0) and dirt (2), with sand and grass again at the diagonal.
        int n = GroundFeather.winners(5, 0, 2, 0, out);
        assertEquals(2, n);
        assertArrayEquals(new int[]{2, 0}, new int[]{out[0], out[1]});
        assertEquals(0, GroundFeather.winners(2, 5, 2, -1, out), "weaker, equal and non-feathering ground take nothing");
        assertEquals(0, GroundFeather.winners(-1, 0, 0, 0, out), "ground that does not feather receives nothing");
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
