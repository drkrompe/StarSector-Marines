package com.dillon.starsectormarines.tools.terrainauthoring;

import com.dillon.starsectormarines.ops.battleview.GroundFeather;
import com.dillon.starsectormarines.ops.battleview.GroundFeather.Shape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The rule the feather quarters rest on: two tiles meeting at an edge draw one boundary. */
class GroundFeatherBakerTest {

    @Test void quartersMeetWithoutAStep() {
        // Grass north of A only: A's north-east quarter is an edge, B's north-west an outer corner.
        for (int y = 0; y < 16; y++) {
            int a = GroundFeather.edgeCode(0, y), b = GroundFeather.edgeCode(1, y);
            for (double v = .5; v <= 1; v += 1 / 64d) {
                assertEquals(GroundFeatherBaker.alpha(Shape.EDGE_N, a, 1, v, 0),
                        GroundFeatherBaker.alpha(Shape.OUTER_NW, b, 0, v, 0), 1e-9, "row " + y + " at " + v);
            }
        }
    }

    @Test void noBleedReachesHalfACell() {
        for (int code = 0; code < GroundFeather.EDGE_CODES; code++) {
            for (double u = 0; u <= 1; u += 1 / 32d) {
                assertEquals(0, GroundFeatherBaker.alpha(Shape.EDGE_N, code, u, .5, 3), 1e-9);
                assertEquals(0, GroundFeatherBaker.alpha(Shape.EDGE_W, code, .5, u, 3), 1e-9);
            }
        }
    }
}
