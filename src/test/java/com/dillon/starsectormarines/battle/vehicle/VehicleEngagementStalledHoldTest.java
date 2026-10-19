package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a hull holding in band counts its hold as stalled, and which firing lane it will move to. */
class VehicleEngagementStalledHoldTest {

    @Test
    void aStalledHoldIsThreeVolleysButNeverShorterThanTheFloor() {
        assertEquals(VehicleEngagement.STALL_MIN_SECONDS, VehicleEngagement.stallSeconds(2.2f), 1e-4f);
        assertEquals(15f, VehicleEngagement.stallSeconds(5f), 1e-4f);
    }

    @Test
    void anOpenLaneIsClear() {
        NavigationGrid grid = open();
        assertTrue(VehicleEngagement.laneClear(grid, 2.5f, 10.5f, 30.5f, 10.5f));
    }

    @Test
    void aCornerBesideTheLineFoulsTheLaneThoughTheLineItselfIsClear() {
        NavigationGrid grid = open();
        // A building corner just north of the centre line, the way a target in a front's lee stands.
        grid.setWalkable(26, 11, false);
        grid.setSeeThrough(26, 11, false);
        assertTrue(grid.hasLineOfFire(2.5f, 10.5f, 30.5f, 10.5f));
        assertFalse(VehicleEngagement.laneClear(grid, 2.5f, 10.5f, 30.5f, 10.5f));
    }

    private static NavigationGrid open() {
        NavigationGrid grid = new NavigationGrid(40, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 40; x++) grid.setWalkable(x, y, true);
        }
        return grid;
    }
}
