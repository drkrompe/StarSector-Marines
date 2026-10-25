package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VehicleClearances}: one held mask per grid and radius, never one that has stopped being true, and never one
 * grid's mask answered for another's.
 */
class VehicleClearancesTest {

    private static NavigationGrid floor() {
        NavigationGrid grid = new NavigationGrid(16, 8);
        for (int y = 1; y <= 6; y++) {
            for (int x = 1; x <= 14; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    @Test
    void anUnchangedGridIsAnsweredFromTheHeldMask() {
        NavigationGrid grid = floor();
        VehicleClearances clearances = new VehicleClearances();

        VehicleClearance first = clearances.of(grid, 1);

        assertSame(first, clearances.of(grid, 1));
        assertNotSame(first, clearances.of(grid, 0));
    }

    @Test
    void aMovedMapIsAnsweredWithTheCellItClosed() {
        NavigationGrid grid = floor();
        VehicleClearances clearances = new VehicleClearances();
        assertTrue(clearances.of(grid, 0).isPassable(7, 3));

        grid.setWalkable(7, 3, false);

        assertFalse(clearances.of(grid, 0).isPassable(7, 3));
    }

    @Test
    void eachGridKeepsItsOwnMask() {
        NavigationGrid open = floor();
        NavigationGrid walled = floor();
        walled.setWalkable(7, 3, false);
        VehicleClearances clearances = new VehicleClearances();

        VehicleClearance openMask = clearances.of(open, 0);
        assertFalse(clearances.of(walled, 0).isPassable(7, 3));

        assertSame(openMask, clearances.of(open, 0));
        assertTrue(openMask.isPassable(7, 3));
    }
}
