package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementGuardsTest {

    @Test
    void protectsBothCellsOwnedByAnAuthoredEdgeBarrier() {
        NavigationGrid grid = new NavigationGrid(3, 1);
        for (int x = 0; x < 3; x++) grid.setWalkableFloor(x, 0);
        grid.placeEdgeBarrier(0, 0, Direction.E,
                SharedEdgeBarrier.Kind.WINDOW);

        assertTrue(PlacementGuards.touchesEdgeBarrier(grid, 0, 0));
        assertTrue(PlacementGuards.touchesEdgeBarrier(grid, 1, 0));
        assertFalse(PlacementGuards.touchesEdgeBarrier(grid, 2, 0));
    }

    /**
     * The second crate is the one that seals the cage. A two-cell-wide room
     * crossed by a single row: a fixture on either half of that row leaves the
     * other half as a way past, and the fixture that takes the remaining half
     * cuts the far end off from the rest of the room. Nothing about either cell
     * being free separates the two placements, which is why the guard has to be
     * a reachability question rather than an occupancy one.
     */
    @Test
    void refusesTheFixtureThatSealsTheRowItSharesWithAnother() {
        //  y=3  # . . #      the rest of the room
        //  y=2  # . . #
        //  y=1  # A B #      the row two fixtures compete for
        //  y=0  # . . #      the far end that gets cut off
        NavigationGrid grid = new NavigationGrid(4, 4);
        for (int y = 0; y < 4; y++) {
            grid.setWalkableFloor(1, y);
            grid.setWalkableFloor(2, y);
        }

        assertFalse(PlacementGuards.wouldStrandGround(grid, 1, 1),
                "one fixture leaves the other half of the row open");

        grid.setWalkable(1, 1, false);

        assertTrue(PlacementGuards.wouldStrandGround(grid, 2, 1),
                "the second fixture closes the row and cuts the far end off");
        assertTrue(grid.isWalkable(2, 1), "the guard must leave the grid as it found it");
    }
}
