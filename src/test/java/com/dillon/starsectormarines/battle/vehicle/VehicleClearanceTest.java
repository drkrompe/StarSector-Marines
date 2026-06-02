package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-0 acceptance for {@link VehicleClearance}: the eroded mask marks a cell
 * passable only when the vehicle's footprint fits centered on it — so a route
 * built on it can't thread a gap too narrow for the truck.
 *
 * <ul>
 *   <li>radius 0 reproduces raw walkability;</li>
 *   <li>a cell one off a wall is excluded at radius ≥1, an open-field center
 *       included;</li>
 *   <li>a 1-cell crack between walls is impassable for a radius that can't fit;</li>
 *   <li>erosion never marks a non-walkable cell passable.</li>
 * </ul>
 */
public class VehicleClearanceTest {

    /** Rectangular block of walkable floor [x0..x1] × [y0..y1] inclusive. */
    private static void carve(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
    }

    @Test
    public void radiusZeroEqualsRawWalkability() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        carve(grid, 2, 2, 6, 6);

        VehicleClearance c = VehicleClearance.erode(grid, 0);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                assertEquals(grid.isWalkable(x, y), c.isPassable(x, y),
                        "radius-0 clearance must equal walkability at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    public void radiusOneErodesWallAdjacentCellsAndKeepsOpenInterior() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        carve(grid, 2, 2, 9, 9);   // 8×8 open block

        VehicleClearance c = VehicleClearance.erode(grid, 1);

        // A cell on the block's edge (a wall — actually a non-walkable neighbor —
        // sits one step out) is eroded away.
        assertFalse(c.isPassable(2, 5), "edge cell adjacent to non-walkable should be eroded");
        assertFalse(c.isPassable(5, 2), "edge cell adjacent to non-walkable should be eroded");
        // A cell well inside the open block fits the footprint.
        assertTrue(c.isPassable(5, 5), "open interior should remain passable");
        // The eroded interior is the block shrunk by 1 on every side → [3..8]×[3..8].
        assertTrue(c.isPassable(3, 3), "first fully-surrounded cell should be passable");
        assertTrue(c.isPassable(8, 8), "first fully-surrounded cell should be passable");
    }

    @Test
    public void oneCellCrackIsImpassableForAVehicleThatCantFit() {
        // A 1-wide vertical corridor: only x=5 walkable across y∈[1..8].
        NavigationGrid grid = new NavigationGrid(11, 10);
        for (int y = 1; y <= 8; y++) grid.setWalkableFloor(5, y);

        // radius 0: the crack is passable (infantry-width).
        VehicleClearance r0 = VehicleClearance.erode(grid, 0);
        assertTrue(r0.isPassable(5, 4), "1-wide crack passable at radius 0");

        // radius 1: a vehicle can't fit — every cell has non-walkable side neighbors.
        VehicleClearance r1 = VehicleClearance.erode(grid, 1);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 11; x++) {
                assertFalse(r1.isPassable(x, y),
                        "no cell of a 1-wide crack should be vehicle-passable at radius 1 (" + x + "," + y + ")");
            }
        }
    }

    @Test
    public void erosionNeverMarksNonWalkablePassable() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        carve(grid, 1, 1, 8, 8);

        for (int r = 0; r <= 2; r++) {
            VehicleClearance c = VehicleClearance.erode(grid, r);
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 10; x++) {
                    if (!grid.isWalkable(x, y)) {
                        assertFalse(c.isPassable(x, y),
                                "non-walkable (" + x + "," + y + ") passable at radius " + r);
                    }
                }
            }
        }
    }

    @Test
    public void radiusForWidthRoundsHalfWidth() {
        assertEquals(1, VehicleClearance.radiusForWidth(1.4f)); // HEAVY_APC width → round(0.7)=1
        assertEquals(0, VehicleClearance.radiusForWidth(0.8f)); // round(0.4)=0
        assertEquals(1, VehicleClearance.radiusForWidth(2.0f)); // round(1.0)=1
        assertEquals(0, VehicleClearance.radiusForWidth(0f));
    }
}
