package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a hover skirt floats over, the view its planning and motion read, and the route cost it pays. */
class HoverSkirtTest {

    private static final int SIZE = 8;

    @Test void floatsOverOpenWaterAndNothingElse() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        water(grid, topology, 1, 1);
        // A wall: blocked, but dry.
        grid.setWalkable(2, 1, false);
        topology.setTag(2, 1, CellTopology.Tag.WALL, true);
        // A rock standing in the water.
        water(grid, topology, 3, 1);
        grid.setTag(3, 1, NavigationGrid.CellTag.NATURAL_SOLID, true);

        assertTrue(HoverSkirt.floats(grid, topology, 1, 1), "open water");
        assertFalse(HoverSkirt.floats(grid, topology, 2, 1), "a wall");
        assertFalse(HoverSkirt.floats(grid, topology, 3, 1), "a rock in the water");
        assertFalse(HoverSkirt.floats(grid, topology, 4, 1), "dry floor is not floated over, it is driven on");
        assertFalse(HoverSkirt.floats(grid, topology, -1, 1), "off the map");
    }

    @Test void hoverViewOpensTheWaterClearsDebrisAndLeavesTheLiveGrid() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        water(grid, topology, 2, 2);
        grid.setWalkable(3, 2, false);
        grid.setTag(5, 5, NavigationGrid.CellTag.CRASH_DEBRIS, true);

        NavigationGrid view = HoverSkirt.hoverView(grid, topology);

        assertTrue(view.isWalkable(2, 2), "water is going for a skirt");
        assertFalse(view.isWalkable(3, 2), "a wall is not");
        assertEquals(1f, view.groundSpeedMultiplier(5.5f, 5.5f), "debris does not slow a skirt");
        assertFalse(grid.isWalkable(2, 2), "nobody else gains the water");
        assertEquals(.5f, grid.groundSpeedMultiplier(5.5f, 5.5f), "debris still slows a wheel");
    }

    @Test void aClosedShoreEdgeStaysClosed() {
        NavigationGrid grid = floor();
        CellTopology topology = new CellTopology(SIZE, SIZE);
        water(grid, topology, 2, 2);
        // A fence along the shore cell's west edge, facing the water.
        grid.blockEdge(3, 2, Direction.W);

        NavigationGrid view = HoverSkirt.hoverView(grid, topology);

        assertFalse(view.isSharedEdgePassable(2, 2, Direction.E), "low cover on the shore still stops it");
        assertTrue(view.isSharedEdgePassable(2, 2, Direction.N), "open shore does not");
    }

    @Test void waterAndRubbleCostWhatOpenGroundDoes() {
        float open = TerrainCostField.costFor(GroundKind.GRASS);
        assertEquals(open, HoverSkirt.cost(GroundKind.WATER), 1e-6f);
        assertEquals(open, HoverSkirt.cost(GroundKind.RUBBLE), 1e-6f);
        assertEquals(TerrainCostField.costFor(GroundKind.STREET), HoverSkirt.cost(GroundKind.STREET), 1e-6f);
        assertEquals(TerrainCostField.costFor(GroundKind.INDOOR), HoverSkirt.cost(GroundKind.INDOOR), 1e-6f);

        CellTopology topology = new CellTopology(SIZE, SIZE);
        topology.setGroundKind(1, 1, GroundKind.WATER);
        TerrainCostField field = HoverSkirt.costField(topology);
        assertEquals(open, field.costAt(1, 1), 1e-6f);
        assertTrue(TerrainCostField.from(topology).costAt(1, 1) > open, "a wheel still avoids it");
    }

    @Test void onlyTheHoverLineHasASkirtAndOnlyItPushesNobody() {
        for (VehicleType type : VehicleType.values()) {
            boolean hover = type == VehicleType.HOVER_SCOUT || type == VehicleType.DRONE_SKIMMER;
            assertEquals(hover, type.isHover(), type.name());
            assertEquals(!hover, type.repelsBodies(), type.name());
            if (hover) assertFalse(type.hasAnimatedTreads(), type.name());
        }
    }

    private static NavigationGrid floor() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    private static void water(NavigationGrid grid, CellTopology topology, int x, int y) {
        grid.setWalkable(x, y, false);
        topology.setGroundKind(x, y, GroundKind.WATER);
    }
}
