package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.hull.HullPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
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

    /** Plan cell {@code (u, v)} of its section laid heading west, as the ground lays the broken hull. */
    private static int laid(HullPlan.Section section, int u, int v) {
        int x = 180 - HullPlan.laidU(section, u), y = 60 - HullPlan.laidV(section, v);
        return y * W + x;
    }

    @Test
    void aBrokenHullsSectionsLieWhereThePlanFitsTheirRoofsEvenWithTheBowsTipUnroofed() {
        // Each section's deck and armour roofed, as the compartments are, but the bow's taper left out.
        HullPlan hull = HullPlan.plan();
        List<BitSet> patches = new ArrayList<>();
        BitSet roofed = new BitSet();
        for (HullPlan.Section section : HullPlan.SECTIONS) {
            BitSet patch = new BitSet();
            for (int u = section.u0(); u <= section.u1(); u++) {
                for (int v = 0; v < HullPlan.BREADTH; v++) {
                    HullPlan.Kind k = hull.at(u, v);
                    if (k == HullPlan.Kind.FLOOR || k == HullPlan.Kind.HULL && u < 70) patch.set(laid(section, u, v));
                }
            }
            roofed.or(patch);
            patches.add(0, patch);
        }
        List<HullRoof.Section> s = HullRoof.fit(patches, W).sections();
        assertEquals(HullPlan.SECTIONS.size(), s.size());
        for (int i = 0; i < s.size(); i++) {
            HullRoof.Section fitted = s.get(i);
            HullPlan.Section section = HullPlan.SECTIONS.get(i);
            assertEquals(section, fitted.plan);
            assertEquals(180f, fitted.angleDeg());
            for (int u : new int[]{section.u0(), (section.u0() + section.u1()) / 2, section.u1()}) {
                for (int v : new int[]{0, 13, HullPlan.BREADTH - 1}) {
                    int at = laid(section, u, v);
                    float x = at % W + .5f, y = at / W + .5f;
                    assertEquals(u + .5f, fitted.planU(x, y), section.name() + " u " + u);
                    assertEquals(v + .5f, fitted.planV(x, y), section.name() + " v " + v);
                }
            }
        }
        // The taper no roof covers is painted as the bow's.
        HullRoof.Section bow = s.get(s.size() - 1);
        int[] solid = HullRoof.solid(bow, roofed, W, 200);
        int tip = -1;
        for (int u = HullPlan.LENGTH - 1; u >= 75 && tip < 0; u--) {
            if (hull.at(u, HullPlan.BREADTH / 2) == HullPlan.Kind.HULL) tip = laid(bow.plan, u, HullPlan.BREADTH / 2);
        }
        assertTrue(tip >= 0, "a solid cell in the bow's taper");
        final int t = tip;
        assertTrue(Arrays.stream(solid).anyMatch(c -> c == t));
        assertTrue(Arrays.stream(solid).noneMatch(roofed::get));
        // A roof running across a break paints each cell as the section it lies in.
        HullRoof ship = HullRoof.fit(patches, W);
        int mid = laid(HullPlan.SECTIONS.get(1), 30, 0);
        assertEquals(HullPlan.SECTIONS.get(1), ship.at(mid % W, mid / W).plan);
        assertEquals(bow.plan, ship.at(t % W, t / W).plan);
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
