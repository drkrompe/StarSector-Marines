package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HullRoofTest {

    private static final int W = 120;

    /** A hull roof {@code along} x {@code across} at (10, 20), its open-cornered bow at the high end when {@code bowHigh}. */
    private static BitSet hull(int spanX, int spanY, boolean alongX, boolean bowHigh) {
        BitSet cells = new BitSet();
        for (int y = 20; y < 20 + spanY; y++) {
            for (int x = 10; x < 10 + spanX; x++) cells.set(y * W + x);
        }
        int bowEnd = alongX ? (bowHigh ? 10 + spanX - 1 : 10) : (bowHigh ? 20 + spanY - 1 : 20);
        if (alongX) {
            cells.clear(20 * W + bowEnd);
            cells.clear((20 + spanY - 1) * W + bowEnd);
        } else {
            cells.clear(bowEnd * W + 10);
            cells.clear(bowEnd * W + 10 + spanX - 1);
        }
        return cells;
    }

    @Test
    void theBowIsTheEndWithOpenCornersAndThePlanRunsFromTheStern() {
        HullRoof east = HullRoof.fit(hull(HullPlan.LENGTH, HullPlan.BREADTH, true, true), W);
        assertEquals(0f, east.angleDeg());
        assertEquals(0f, east.planU(10f, 20f));
        assertEquals(HullPlan.LENGTH, east.planU(10f + HullPlan.LENGTH, 20f));
        assertEquals(1f, east.planV(10f, 21f));

        HullRoof west = HullRoof.fit(hull(HullPlan.LENGTH, HullPlan.BREADTH, true, false), W);
        assertEquals(180f, west.angleDeg());
        assertEquals(0f, west.planU(10f + HullPlan.LENGTH, 20f + HullPlan.BREADTH));
        assertEquals(1f, west.planV(10f + HullPlan.LENGTH, 20f + HullPlan.BREADTH - 1));

        HullRoof north = HullRoof.fit(hull(HullPlan.BREADTH, HullPlan.LENGTH, false, true), W);
        assertEquals(90f, north.angleDeg());
        assertEquals(0f, north.planU(10f, 20f));
        // Facing north, the plan's v runs west.
        assertEquals(1f, north.planV(10f + HullPlan.BREADTH - 1, 20f));

        HullRoof south = HullRoof.fit(hull(HullPlan.BREADTH, HullPlan.LENGTH, false, false), W);
        assertEquals(270f, south.angleDeg());
        assertEquals(0f, south.planU(10f, 20f + HullPlan.LENGTH));
        assertEquals(1f, south.planV(11f, 20f));
    }

    @Test
    void aRoofWithoutThePlansBoundsIsNoShip() {
        assertNull(HullRoof.fit(hull(12, 6, true, true), W));
    }
}
