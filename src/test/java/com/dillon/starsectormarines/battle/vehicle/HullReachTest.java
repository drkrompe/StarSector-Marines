package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hull as something struck from beside it ({@link HullReach}). */
class HullReachTest {

    /** A hull 4 long (north-south) and 2 wide, centred on (10, 10), on open ground. */
    private static final float HX = 10f, HY = 10f, FACING = 0f, HALF_L = 2f, HALF_W = 1f;

    private static NavigationGrid open() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    @Test
    void reachIsToTheSideNotTheCentre() {
        assertEquals(0f, HullReach.gap(HX, HY, FACING, HALF_L, HALF_W, 10.5f, 11f), 1e-6f, "inside the hull");
        assertEquals(1f, HullReach.gap(HX, HY, FACING, HALF_L, HALF_W, 12f, 10f), 1e-5f,
                "two cells from the centre is one from the side");
    }

    @Test
    void aStrikerClosesToTheNearSideWithinItsReachAndClearOfTheHull() {
        int[] side = HullReach.beside(open(), HX, HY, FACING, HALF_L, HALF_W, 16f, 10f, 1.8f, 0.3f);
        assertNotNull(side);
        float gap = HullReach.gap(HX, HY, FACING, HALF_L, HALF_W, side[0] + .5f, side[1] + .5f);
        assertTrue(gap >= 0.3f && gap <= 1.8f, "beside, at " + gap);
        assertTrue(side[0] + .5f > HX + HALF_W, "on the east side, the striker's side: " + side[0]);
    }

    @Test
    void aStrikerTouchesTheSideOnlyWhenAgainstIt() {
        assertTrue(HullReach.touches(HX, HY, FACING, HALF_L, HALF_W, 11.42f, 10f, .3f),
                "held off the side by the shove, its radius and 0.12, it touches");
        assertFalse(HullReach.touches(HX, HY, FACING, HALF_L, HALF_W, 12.5f, 10f, .3f),
                "a cell and a half off the side is in a Runner's old reach, not touching");
    }

    @Test
    void theWayInIsToTheNearestPointOfTheSide() {
        float[] flank = HullReach.toward(HX, HY, FACING, HALF_L, HALF_W, 13f, 11f);
        assertEquals(-1f, flank[0], 1e-5f);
        assertEquals(0f, flank[1], 1e-5f);
        assertEquals(2f, flank[2], 1e-5f, "square on to the east side");
        float[] corner = HullReach.toward(HX, HY, FACING, HALF_L, HALF_W, 13f, 14f);
        assertEquals((float) Math.sqrt(8), corner[2], 1e-5f, "past the bow, to its corner");
        assertEquals(corner[0], corner[1], 1e-5f);
        float[] turned = HullReach.toward(HX, HY, 90f, HALF_L, HALF_W, 10f, 13f);
        assertEquals(-1f, turned[1], 1e-5f);
        assertEquals(2f, turned[2], 1e-5f, "turned east-west, its side faces north");
        assertNull(HullReach.toward(HX, HY, FACING, HALF_L, HALF_W, 10.5f, 11f), "no way in from inside");
    }

    @Test
    void noGroundBesideMeansNowhereToStand() {
        NavigationGrid walled = open();
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) {
            if (HullReach.gap(HX, HY, FACING, HALF_L, HALF_W, x + .5f, y + .5f) > 0.25f) walled.setWalkable(x, y, false);
        }
        assertNull(HullReach.beside(walled, HX, HY, FACING, HALF_L, HALF_W, 16f, 10f, 1.8f, 0.3f));
        assertNull(HullReach.beside(open(), HX, HY, FACING, HALF_L, HALF_W, 16f, 10f, 0.3f, 0.3f),
                "a reach no longer than the clearance reaches nothing");
    }
}
