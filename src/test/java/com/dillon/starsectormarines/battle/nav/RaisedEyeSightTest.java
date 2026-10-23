package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A gun on a plinth sees from its height, and is seen back from it, on the hill of {@link ElevationSightTest}: a
 * plateau of tier one on row y=15 with its top at 20-30 and its rim at 30, low ground from 32.
 */
class RaisedEyeSightTest {

    @Test void aGunOnAOneTierPlinthSeesOntoThePlateauAndIsSeenBack() {
        NavigationGrid grid = ElevationSightTest.hill(ElevationMap.SightRule.SKYLINE);
        assertFalse(grid.hasLineOfSight(36, 15, 25, 15), "control: from the ground only the rim shows");

        grid.setRaisedEye(36, 15, 1);
        assertEquals(1, grid.eyeTierAt(36, 15));
        assertTrue(grid.hasLineOfSight(36, 15, 25, 15), "from the plinth the top is level with the eye");
        assertTrue(grid.hasLineOfSight(25, 15, 36, 15), "and the top sees the gun back");
        assertTrue(grid.hasLineOfFire(36.5f, 15.5f, 25.5f, 15.5f), "fire follows sight");
        assertTrue(grid.hasLineOfSight(36, 15, 2, 15), "across the plateau to the low ground beyond");

        grid.setRaisedEye(36, 15, 0);
        assertFalse(grid.hasLineOfSight(36, 15, 25, 15), "a destroyed gun's cell sees from the ground again");
        assertEquals(0, grid.raisedCells().length);
    }
}
