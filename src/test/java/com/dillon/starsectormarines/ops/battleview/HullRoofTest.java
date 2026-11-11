package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullRoofTest {

    private static final int W = 200;

    /** A block of roof {@code spanX x spanY} at {@code (x, y)}; its corners at the {@code open} end cleared (the bow). */
    private static BitSet block(int x, int y, int spanX, int spanY, boolean alongX, Boolean openHigh) {
        BitSet cells = new BitSet();
        for (int yy = y; yy < y + spanY; yy++) {
            for (int xx = x; xx < x + spanX; xx++) cells.set(yy * W + xx);
        }
        if (openHigh != null) {
            if (alongX) {
                int end = openHigh ? x + spanX - 1 : x;
                cells.clear(y * W + end);
                cells.clear((y + spanY - 1) * W + end);
            } else {
                int end = openHigh ? y + spanY - 1 : y;
                cells.clear(end * W + x);
                cells.clear(end * W + x + spanX - 1);
            }
        }
        return cells;
    }

    private static HullRoof.Section whole(boolean alongX, boolean bowHigh) {
        BitSet hull = alongX ? block(10, 20, HullPlan.LENGTH, HullPlan.BREADTH, true, bowHigh)
                : block(10, 20, HullPlan.BREADTH, HullPlan.LENGTH, false, bowHigh);
        return HullRoof.fit(List.of(hull), W).sections().get(0);
    }

    @Test
    void theBowIsTheEndWithOpenCornersAndThePlanRunsFromTheStern() {
        HullRoof.Section east = whole(true, true);
        assertEquals(0f, east.angleDeg());
        assertEquals(0f, east.planU(10f, 20f));
        assertEquals(HullPlan.LENGTH, east.planU(10f + HullPlan.LENGTH, 20f));
        assertEquals(1f, east.planV(10f, 21f));

        HullRoof.Section west = whole(true, false);
        assertEquals(180f, west.angleDeg());
        assertEquals(0f, west.planU(10f + HullPlan.LENGTH, 20f + HullPlan.BREADTH));
        assertEquals(1f, west.planV(10f + HullPlan.LENGTH, 20f + HullPlan.BREADTH - 1));

        HullRoof.Section north = whole(false, true);
        assertEquals(90f, north.angleDeg());
        assertEquals(0f, north.planU(10f, 20f));
        // Facing north, the plan's v runs west.
        assertEquals(1f, north.planV(10f + HullPlan.BREADTH - 1, 20f));

        HullRoof.Section south = whole(false, false);
        assertEquals(270f, south.angleDeg());
        assertEquals(0f, south.planU(10f, 20f + HullPlan.LENGTH));
        assertEquals(1f, south.planV(11f, 20f));
    }

    @Test
    void aBrokenHullsSectionsTakeTheNextRunsOfThePlanFromTheSternSharingWhatTheBreaksTook() {
        // Stern 20 long, a midsection 30 long slid 2 cells across, the bow 30 long with its taper: 80 of 88 cells,
        // so each of the two breaks took 4. Laid out of order, apart along the trench.
        BitSet bow = block(140, 20, 30, HullPlan.BREADTH, true, true);
        BitSet stern = block(10, 20, 20, HullPlan.BREADTH, true, null);
        BitSet mid = block(60, 22, 30, HullPlan.BREADTH - 4, true, null);
        List<HullRoof.Section> s = HullRoof.fit(List.of(bow, stern, mid), W).sections();
        assertEquals(3, s.size());
        assertEquals(0f, s.get(0).planU(10f, 20f));
        assertEquals(24f, s.get(1).planU(60f, 22f));
        assertEquals(58f, s.get(2).planU(140f, 20f));
        assertEquals(HullPlan.LENGTH, s.get(2).planU(170f, 20f));
        // The narrower midsection keeps the plan's centre line on its own.
        assertEquals(HullPlan.BREADTH / 2f, s.get(1).planV(60f, 22f + (HullPlan.BREADTH - 4) / 2f));
    }

    @Test
    void aCompartmentIsTheShipsWhenItsRoofLiesWithinASection() {
        HullRoof.Section east = whole(true, true);
        assertTrue(east.inside(new int[]{30 * W + 40, 31 * W + 41}, W));
        assertFalse(east.inside(new int[]{30 * W + 40, 60 * W + 41}, W));
    }

    @Test
    void roofsWithoutThePlansBreadthOrLongerThanItAreNoShip() {
        assertNull(HullRoof.fit(List.of(block(10, 20, 40, 6, true, true)), W));
        assertNull(HullRoof.fit(List.of(block(10, 20, 60, HullPlan.BREADTH, true, true),
                block(80, 20, 60, HullPlan.BREADTH, true, null)), W));
    }
}
