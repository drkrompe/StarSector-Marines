package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClearanceComponents} on hand-built masks: what it labels together must
 * be what the router can actually drive between, and nothing else. The whole
 * value of the class is that a mismatched answer is worse than no answer — a
 * component that joins two rooms the chassis cannot pass between reinstates the
 * flood it exists to avoid, and one that splits a room the chassis can cross
 * refuses a delivery that was possible.
 */
public class ClearanceComponentsTest {

    /** Rectangular block of walkable floor [x0..x1] × [y0..y1] inclusive. */
    private static void carve(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
    }

    @Test
    public void oneOpenRoomIsOneComponent() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        carve(grid, 1, 1, 6, 6);

        ClearanceComponents components =
                ClearanceComponents.of(grid, VehicleClearance.erode(grid, 0));

        assertEquals(1, components.componentCount());
        assertTrue(components.connected(1, 1, 6, 6));
        assertEquals(ClearanceComponents.NONE, components.labelAt(0, 0));
        assertEquals(ClearanceComponents.NONE, components.labelAt(-1, 3));
        assertEquals(ClearanceComponents.NONE, components.labelAt(8, 3));
    }

    @Test
    public void twoRoomsWithNoWayBetweenThemAreTwoComponents() {
        NavigationGrid grid = new NavigationGrid(12, 6);
        carve(grid, 1, 1, 4, 4);
        carve(grid, 7, 1, 10, 4);

        ClearanceComponents components =
                ClearanceComponents.of(grid, VehicleClearance.erode(grid, 0));

        assertEquals(2, components.componentCount());
        assertFalse(components.connected(2, 2, 8, 2));
        assertNotEquals(components.labelAt(2, 2), components.labelAt(8, 2));
        assertTrue(components.connected(1, 1, 4, 4));
    }

    /**
     * The case the convoy dispatch was paying a full grid flood to discover: a
     * corridor the map connects and the chassis does not fit down. Raw
     * walkability is one room; the eroded mask is two.
     */
    @Test
    public void aCorridorTooNarrowForTheChassisSeparatesWhatWalkabilityJoins() {
        NavigationGrid grid = new NavigationGrid(15, 9);
        carve(grid, 1, 1, 5, 7);
        carve(grid, 9, 1, 13, 7);
        carve(grid, 6, 4, 8, 4); // one-cell-tall neck joining the two halves

        ClearanceComponents onFoot =
                ClearanceComponents.of(grid, VehicleClearance.erode(grid, 0));
        assertEquals(1, onFoot.componentCount());
        assertTrue(onFoot.connected(3, 4, 11, 4));

        ClearanceComponents forABody =
                ClearanceComponents.of(grid, VehicleClearance.erode(grid, 1));
        assertFalse(forABody.connected(3, 4, 11, 4),
                "a one-cell neck must not join two components for a body that cannot fit in it");
        assertEquals(2, forABody.componentCount());
    }

    /**
     * A closed edge is a wall the mask cannot see: both cells fit the chassis
     * and neither can be reached from the other. Labelling on the mask alone
     * would join them, which is why the flood expands through the grid's own
     * step rule.
     */
    @Test
    public void aClosedEdgeSplitsAMaskThatLooksOpen() {
        NavigationGrid grid = new NavigationGrid(9, 5);
        carve(grid, 0, 0, 8, 4);
        for (int y = 0; y < 5; y++) {
            grid.setSharedEdgePassable(4, y, Direction.E, false);
        }

        VehicleClearance clearance = VehicleClearance.erode(grid, 0);
        assertTrue(clearance.isPassable(4, 2));
        assertTrue(clearance.isPassable(5, 2));

        ClearanceComponents components = ClearanceComponents.of(grid, clearance);

        assertEquals(2, components.componentCount());
        assertFalse(components.connected(4, 2, 5, 2));
    }

    @Test
    public void aMaskNothingFitsHasNoComponents() {
        NavigationGrid grid = new NavigationGrid(6, 6);
        carve(grid, 2, 2, 3, 3);

        ClearanceComponents components =
                ClearanceComponents.of(grid, VehicleClearance.erode(grid, 2));

        assertEquals(0, components.componentCount());
        assertEquals(ClearanceComponents.NONE, components.labelAt(2, 2));
        assertFalse(components.connected(2, 2, 2, 2));
    }
}
