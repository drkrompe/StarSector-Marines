package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hull is set down on ground it can drive away from, never in a walled pocket. */
class CordonArmourGroundTest {

    @Test
    void aHullAskedForAPlaceInsideAWalledPenIsSetDownOutsideIt() {
        NavigationGrid grid = new NavigationGrid(40, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        // A pen with walls on cells 4..12, open inside: room for a hull, and no way out.
        for (int i = 4; i <= 12; i++) {
            grid.setWalkable(i, 4, false);
            grid.setWalkable(i, 12, false);
            grid.setWalkable(4, i, false);
            grid.setWalkable(12, i, false);
        }
        boolean[] main = VehicleClearance.mainGround(grid, 1);
        assertFalse(main[8 * 40 + 8], "the pen's middle fits a hull but is not the map's ground");
        assertTrue(main[8 * 40 + 20]);
        int[] cell = CordonArmour.nearest(grid, main, 8, 8, 24);
        boolean outside = cell[0] < 4 || cell[0] > 12 || cell[1] < 4 || cell[1] > 12;
        assertTrue(outside && main[cell[1] * 40 + cell[0]], "set down outside the pen, on the map's ground");
    }

    /** A redoubt on cells 20..30 across, 10..18 down, on a 60 by 40 map. */
    private static int[] through(boolean[] street) {
        return CordonArmour.throughStreet(street, 60, 40, 20, 10, 30, 18, CordonArmour.THROUGH_REACH);
    }

    @Test
    void aTownsFirstHullStandsOnTheStreetThroughItsRedoubt() {
        boolean[] street = new boolean[60 * 40];
        // A street down x = 27 through the redoubt, and another down x = 2, out of reach.
        for (int y = 0; y < 40; y++) {
            street[y * 60 + 27] = true;
            street[y * 60 + 2] = true;
        }
        assertArrayEquals(new int[]{27, 14}, through(street));
    }

    @Test
    void aStreetRunningPastTheRedoubtServesWhereNoneRunsThroughIt() {
        boolean[] street = new boolean[60 * 40];
        // Along y = 24, six cells past the redoubt's wall at 18.
        for (int x = 0; x < 60; x++) street[24 * 60 + x] = true;
        assertArrayEquals(new int[]{25, 24}, through(street));
    }

    @Test
    void noStreetWithinReachLeavesTheHullOnItsFlank() {
        boolean[] street = new boolean[60 * 40];
        for (int x = 0; x < 60; x++) street[34 * 60 + x] = true;
        assertNull(through(street));
    }
}
