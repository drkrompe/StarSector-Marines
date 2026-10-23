package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterShoreTest {

    /** Grass, with water wherever {@code x <= y} below row 6: a staircase bank. */
    private static CellTopology staircase() {
        CellTopology topology = new CellTopology(10, 10);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                topology.setGroundKind(x, y, x <= y && y < 6 ? GroundKind.WATER : GroundKind.GRASS);
            }
        }
        return topology;
    }

    @Test
    void noCellCentreIsDrawnAsTheOtherSide() {
        CellTopology topology = staircase();
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                for (int t = 0; t < WaterShore.TRIANGLES; t++) {
                    if (topology.isWater(x, y)) {
                        float[] water = WaterShore.patch(topology, x, y, WaterShore.Band.WATER, t);
                        assertNotNull(water);
                        assertTrue(hasCentre(water), "water centre " + x + "," + y);
                    } else {
                        float[] wet = WaterShore.patch(topology, x, y, WaterShore.Band.WET, t);
                        assertTrue(wet == null || !hasCentre(wet), "land centre " + x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void aStepsOuterCornerIsCutAndItsInnerCornerFlooded() {
        CellTopology topology = staircase();
        // Land cell (3,2) sits in a step: water west of it, north of it and north-west.
        // Its north-west corner, (3,3), has three water cells of four round it.
        assertTrue(WaterShore.corner(topology, 3, 3) > 0, "the step's inner corner floods");
        // Water cell (3,3) has land east and south: its south-east corner, (4,3), has one of four.
        assertTrue(WaterShore.corner(topology, 4, 3) < 0, "outer corner of water cell (3,3) is cut");
        // So land cell (3,2) shows water in its north triangle, toward that corner...
        assertNotNull(WaterShore.patch(topology, 3, 2, WaterShore.Band.SHALLOW, 2));
        // ...and water cell (3,3) does not reach its south-east corner.
        float[] cut = WaterShore.patch(topology, 3, 3, WaterShore.Band.SHALLOW, 1);
        assertNotNull(cut);
        assertFalse(hasPoint(cut, 1f, 0f), "water cell (3,3) keeps its south-east corner dry");
    }

    @Test
    void neighboursAgreeWhereTheirTrianglesMeet() {
        CellTopology topology = staircase();
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                // The east triangle of (x,y) and the west triangle of (x+1,y) share the edge x+1.
                assertArrayEquals(crossing(topology, x, y, 1, 1f), crossing(topology, x + 1, y, 3, 0f), 1e-5f,
                        "edge east of " + x + "," + y);
            }
        }
    }

    @Test
    void groundAwayFromTheWaterDrawsNoShore() {
        CellTopology topology = staircase();
        assertFalse(WaterShore.takesPart(topology, 9, 0));
        for (WaterShore.Band band : WaterShore.Band.values()) {
            for (int t = 0; t < WaterShore.TRIANGLES; t++) assertNull(WaterShore.patch(topology, 9, 0, band, t));
        }
    }

    @Test
    void waterRunsSquareUpToSomethingBuilt() {
        CellTopology topology = new CellTopology(4, 3);
        for (int y = 0; y < 3; y++) {
            topology.setGroundKind(0, y, GroundKind.GRASS);
            topology.setGroundKind(1, y, GroundKind.WATER);
            topology.setGroundKind(2, y, GroundKind.WATER);
            topology.setGroundKind(3, y, GroundKind.STREET);
        }
        assertFalse(WaterShore.takesPart(topology, 3, 1), "a quay draws no shore");
        // The water cell's east triangle, against the street, is water to its edge.
        float[] east = WaterShore.patch(topology, 2, 1, WaterShore.Band.WATER, 1);
        assertTrue(hasPoint(east, 1f, 0f) && hasPoint(east, 1f, 1f));
        // Water with no natural ground beside it keeps its own tile.
        assertFalse(WaterShore.isShoreWater(topology, 2, 1));
        assertTrue(WaterShore.isShoreWater(topology, 1, 1));
    }

    /** Where the shallows' waterline crosses the edge {@code x = edgeX} of triangle {@code t} in cell (x,y). */
    private static float[] crossing(CellTopology topology, int x, int y, int t, float edgeX) {
        float[] p = WaterShore.patch(topology, x, y, WaterShore.Band.SHALLOW, t);
        if (p == null) return new float[]{-1};
        float lo = 2, hi = -1;
        for (int i = 0; i < 8; i += 2) {
            if (Math.abs(p[i] - edgeX) < 1e-5f) {
                lo = Math.min(lo, p[i + 1]);
                hi = Math.max(hi, p[i + 1]);
            }
        }
        return hi < 0 ? new float[]{-1} : new float[]{lo, hi};
    }

    private static boolean hasCentre(float[] patch) {
        return hasPoint(patch, .5f, .5f);
    }

    private static boolean hasPoint(float[] patch, float x, float y) {
        for (int i = 0; i < 8; i += 2) if (Math.abs(patch[i] - x) < 1e-5f && Math.abs(patch[i + 1] - y) < 1e-5f) return true;
        return false;
    }
}
