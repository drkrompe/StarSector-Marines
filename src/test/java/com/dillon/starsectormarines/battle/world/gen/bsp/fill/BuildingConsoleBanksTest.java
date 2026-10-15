package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout.Cell;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BuildingConsoleBanksTest {
    private final NavigationGrid grid = new NavigationGrid(12, 12);
    private final CellTopology topology = new CellTopology(12, 12);
    private final List<Doodad> doodads = new ArrayList<>();
    private final List<Cell> path = List.of(new Cell(3,3), new Cell(4,3), new Cell(5,3));

    BuildingConsoleBanksTest() {
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
    }

    @Test void lateConnectivityRefusalRollsBackTheWholeBank() {
        // The last module would seal a one-cell window recess, after two legal modules.
        grid.setWalkable(4,4,false); grid.setWalkable(6,4,false); grid.setWalkable(5,5,false);
        assertFalse(BuildingConsoleBanks.place(grid, topology, path, true, doodads));
        for (Cell cell : path) {
            assertTrue(grid.isWalkable(cell.x(), cell.y()));
            assertFalse(topology.isFixture(cell.x(), cell.y()));
        }
        assertTrue(doodads.isEmpty());
    }

    @Test void seatsStayWalkableOnTheControlSideAndPreserveFloorMaterial() {
        int material = topology.surfaceIndex("ship-floor-trim.passage");
        topology.setSurface(4,2,material);
        assertTrue(BuildingConsoleBanks.place(grid, topology, path, true, doodads));
        Doodad seat = doodads.stream().filter(d -> d.definitionId().equals("mess-galley.stool")).findFirst().orElseThrow();
        assertEquals(4, seat.cellX); assertEquals(2, seat.cellY);
        assertTrue(grid.isWalkable(4,2)); assertTrue(grid.isWalkable(4,1));
        assertFalse(topology.isFixture(4,2)); assertEquals(0, seat.cover);
        assertEquals("ship-floor-trim.passage", topology.getSurfaceId(4,2));
        for (Cell cell : path) {
            assertFalse(grid.isWalkable(cell.x(), cell.y()));
            assertTrue(grid.isSeeThrough(cell.x(), cell.y()));
            assertTrue(topology.isFixture(cell.x(), cell.y()));
        }
    }

    @Test void doorwayApproachRefusesBankWithoutPartialFurniture() {
        grid.setDoorway(5,4,true);
        assertFalse(BuildingConsoleBanks.place(grid, topology, path, true, doodads));
        assertTrue(doodads.isEmpty());
        for (Cell cell : path) assertTrue(grid.isWalkable(cell.x(), cell.y()));
    }
}
