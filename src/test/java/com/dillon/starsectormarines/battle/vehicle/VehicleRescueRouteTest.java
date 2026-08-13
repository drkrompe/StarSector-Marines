package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VehicleRescueRouteTest {

    @Test
    void prefersForwardFirstStepThenSkipsThatAttemptOnTheNextRescue() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        CellTopology topology = new CellTopology(12, 12);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, GroundKind.GRASS);
        }
        TerrainCostField cost = TerrainCostField.from(topology);
        VehicleClearance clearance = VehicleClearance.erode(grid, 0);

        VehicleRoutePlanner.RescueRoute forward = VehicleRoutePlanner.routeAvoidingForwardFirst(
                5, 5, 9, 5, -90f, 0, grid, cost, clearance, 8, 8, 0f);

        assertNotNull(forward);
        assertEquals(Direction.E.bit(), forward.firstStepDirectionBit());
        assertEquals(6.5f, forward.points()[0][1], 0.001f);
        assertEquals(5.5f, forward.points()[1][1], 0.001f);

        VehicleRoutePlanner.RescueRoute next = VehicleRoutePlanner.routeAvoidingForwardFirst(
                5, 5, 9, 5, -90f, 1 << Direction.E.bit(),
                grid, cost, clearance, 8, 8, 0f);

        assertNotNull(next);
        assertEquals(Direction.NE.bit(), next.firstStepDirectionBit(),
                "once straight ahead was attempted, the next rescue must turn before backing up");
    }
}
