package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColdAccountLayoutTest {
    private final NavigationGrid grid = new NavigationGrid(20, 20);
    private final CellTopology topology = new CellTopology(20, 20);
    private final PointOfInterest room = new PointOfInterest(PointOfInterest.Kind.ADMINISTRATIVE,
            2, 2, 15, 13, 1, 5, 3, 3);

    private void floor() {
        for (int y = 3; y <= 12; y++) for (int x = 3; x <= 14; x++) {
            grid.setWalkableFloor(x, y);
            topology.setBuildingId(x, y, 1);
        }
    }

    @Test void cornerAnchorDoesNotBecomeTheCapturePoint() {
        floor();
        assertArrayEquals(new int[]{8, 7}, ColdAccountLayout.evidenceCell(grid, topology, room));
    }

    @Test void furnitureMovesTheWholeClearCaptureFloorRatherThanOnlyTheMarker() {
        floor();
        grid.setWalkable(8, 7, false);
        int[] point = ColdAccountLayout.evidenceCell(grid, topology, room);
        assertTrue(Math.abs(point[0] - 8) > 2 || Math.abs(point[1] - 7) > 2);
        for (int y = point[1] - 2; y <= point[1] + 2; y++)
            for (int x = point[0] - 2; x <= point[0] + 2; x++) {
                assertTrue(grid.isWalkable(x, y));
                assertEquals(1, topology.getBuildingId(x, y));
            }
    }

    @Test void inaccessibleCentralFloorIsRejected() {
        floor();
        grid.setWalkable(4, 3, false);
        grid.setWalkable(3, 4, false);
        assertThrows(IllegalStateException.class, () -> ColdAccountLayout.evidenceCell(grid, topology, room));
    }

    @Test void outdoorFallbackCannotMasqueradeAsSafeInterior() {
        floor();
        topology.setBuildingId(3, 3, 0);
        assertThrows(IllegalStateException.class, () -> ColdAccountLayout.evidenceCell(grid, topology, room));
    }
}
