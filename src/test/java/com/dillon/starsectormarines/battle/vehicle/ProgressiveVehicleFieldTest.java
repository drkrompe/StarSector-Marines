package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressiveVehicleFieldTest {

    @Test
    void derivesOnlyAskedCellsAndMatchesEagerFields() {
        NavigationGrid grid = new NavigationGrid(31, 19);
        CellTopology topology = new CellTopology(31, 19);
        GroundKind[] kinds = GroundKind.values();
        Random random = new Random(19L);
        for (int y = 0; y < 19; y++) {
            for (int x = 0; x < 31; x++) {
                if (random.nextInt(5) != 0) grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, kinds[random.nextInt(kinds.length)]);
            }
        }
        ProgressiveVehicleField field = ProgressiveVehicleField.capture(grid, topology, 1);
        VehicleClearance eagerClearance = VehicleClearance.erode(grid, 1);
        TerrainCostField eagerCost = TerrainCostField.from(topology);

        assertEquals(0, field.clearanceEvaluations());
        assertEquals(0, field.costEvaluations());
        assertEquals(31 * 19, field.unexploredCells());

        int first = 7 * 31 + 8;
        assertEquals(eagerClearance.isPassable(8, 7), field.isPassable(first));
        assertEquals(eagerCost.costAt(8, 7), field.costAt(first));
        assertEquals(eagerClearance.isPassable(8, 7), field.isPassable(first));
        assertEquals(eagerCost.costAt(8, 7), field.costAt(first));
        assertEquals(1, field.clearanceEvaluations());
        assertEquals(1, field.costEvaluations());

        for (int y = 0; y < 19; y++) {
            for (int x = 0; x < 31; x++) {
                int index = y * 31 + x;
                assertEquals(eagerClearance.isPassable(x, y), field.isPassable(index));
                assertEquals(eagerCost.costAt(x, y), field.costAt(index));
            }
        }
        assertEquals(31 * 19, field.clearanceEvaluations());
        assertEquals(31 * 19, field.costEvaluations());
        assertEquals(0, field.unexploredCells());
    }

    @Test
    void unexploredCellsReadTheCapturedWorldAfterLiveChanges() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        CellTopology topology = new CellTopology(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STREET);
            }
        }
        ProgressiveVehicleField field = ProgressiveVehicleField.capture(grid, topology, 1);
        grid.setWalkable(6, 6, false);
        topology.setGroundKind(6, 6, GroundKind.RUBBLE);

        assertTrue(field.isPassable(6, 6));
        assertEquals(TerrainCostField.COST_ROAD, field.costAt(6 * 12 + 6));
        assertTrue(field.grid().isWalkable(6, 6));
        assertFalse(VehicleClearance.fitsAt(grid, 6, 6, 1));
    }

    @Test
    void localRouteDoesNotDeriveTheRestOfTheMap() {
        NavigationGrid grid = new NavigationGrid(80, 50);
        CellTopology topology = new CellTopology(80, 50);
        for (int y = 0; y < 50; y++) {
            for (int x = 0; x < 80; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STREET);
            }
        }
        ProgressiveVehicleField field = ProgressiveVehicleField.capture(grid, topology, 1);
        int[] path = GridPathfinder.findPathOnDemand(field.grid(),
                10, 20, 30, 20, field, field);

        assertTrue(path.length >= 4);
        assertTrue(field.clearanceEvaluations() < 500,
                "a short route must not erode the surrounding empty city");
        assertTrue(field.costEvaluations() < 500,
                "a short route must not price the surrounding empty city");
    }
}
