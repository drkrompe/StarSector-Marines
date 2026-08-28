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
}
