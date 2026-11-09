package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlockSystemTest {

    /** A 20x10 field; a roofed block x 10..17, y 2..8 with a 2x2 light well at x 13..14, y 4..5 open to the sky. */
    private static boolean open(int x, int y) {
        if (x < 0 || y < 0 || x >= 20 || y >= 10) return false;
        boolean block = x >= 10 && x <= 17 && y >= 2 && y <= 8;
        boolean well = x >= 13 && x <= 14 && y >= 4 && y <= 5;
        return !block || well;
    }

    @Test
    void aLightWellInsideIsNotTheOutside() {
        boolean[] outside = FlockSystem.outside(20, 10, FlockSystemTest::open);
        assertTrue(outside[5 * 20 + 2], "the open field is the outside");
        assertTrue(outside[9 * 20 + 15], "the street past the block is the outside");
        assertFalse(outside[4 * 20 + 13], "the light well is open sky but not the outside");
    }

    @Test
    void aGuardiansFlockIsKeptToItsOwnLightWell() {
        boolean[] well = FlockSystem.body(20, 10, FlockSystemTest::open, 13, 4);
        FlockField.Open inWell = FlockSystem.within(FlockSystemTest::open, well, 20);
        assertTrue(inWell.open(14, 5), "the whole well");
        assertFalse(inWell.open(2, 5), "never the outside");
        assertFalse(FlockSystem.body(20, 10, FlockSystemTest::open, 12, 4)[4 * 20 + 13],
                "a roofed cell has no body of its own");
    }

    @Test
    void launchCellsComeFromTheOpenGroundBeyondACrampedHome() {
        // Home at the mouth of a one-cell alley (x 0, y 0..4) that opens onto a yard at y 5.
        FlockField.Open alley = (x, y) -> (x == 0 && y >= 0 && y <= 4) || (x >= 0 && x <= 5 && y >= 5 && y <= 8);
        List<int[]> cells = FlockSystem.openAround(0, 0, alley, 14);
        assertEquals(14, cells.size(), "the yard beyond the alley gives the rest of the cells");
        assertEquals(0, cells.get(0)[0]);
        assertEquals(0, cells.get(0)[1]);
    }

    @Test
    void aLowFlierCrossesLowCoverButNeverAWallATreeOrARoof() {
        NavigationGrid grid = new NavigationGrid(8, 1);
        CellTopology topology = new CellTopology(8, 1);
        for (int x = 0; x < 8; x++) {
            grid.setWalkableFloor(x, 0);
            topology.setGroundKind(x, 0, CellTopology.GroundKind.DIRT);
        }
        grid.setWalkable(1, 0, false);                                   // sandbags: closed to walking, nothing else
        grid.setWalkable(2, 0, false);
        topology.setWall(2, 0, true);                                    // a wall
        grid.setWalkable(3, 0, false);
        grid.setTag(3, 0, NavigationGrid.CellTag.NATURAL_SOLID, true);   // a tree
        topology.setBuildingId(4, 0, 7);                                 // under an intact roof
        grid.setWalkable(5, 0, false);
        topology.setGroundKind(5, 0, CellTopology.GroundKind.VOID);      // off the map's ground
        FlockField.Open open = FlockSystem.open(grid, topology);
        assertTrue(open.open(0, 0), "open ground");
        assertTrue(open.open(1, 0), "low cover is flown over");
        assertFalse(open.open(2, 0), "a wall is not");
        assertFalse(open.open(3, 0), "a tree is not");
        assertFalse(open.open(4, 0), "a roof is not");
        assertFalse(open.open(5, 0), "the void is not");
        assertFalse(FlockSystem.ground(grid, open).open(1, 0), "a cradle never stands on the low cover");
        assertTrue(FlockSystem.ground(grid, open).open(0, 0));
    }
}
