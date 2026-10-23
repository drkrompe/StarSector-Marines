package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        boolean[] main = CordonArmour.mainGround(grid, 1);
        assertFalse(main[8 * 40 + 8], "the pen's middle fits a hull but is not the map's ground");
        assertTrue(main[8 * 40 + 20]);
        int[] cell = CordonArmour.nearest(grid, main, 8, 8, 24);
        boolean outside = cell[0] < 4 || cell[0] > 12 || cell[1] < 4 || cell[1] > 12;
        assertTrue(outside && main[cell[1] * 40 + cell[0]], "set down outside the pen, on the map's ground");
    }
}
