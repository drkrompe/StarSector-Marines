package com.dillon.starsectormarines.battle.world.gen.ship.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipMaterialStageTest {
    @Test
    void defaultsRespectAuthoredSurfacesVacuumAndPhysicalCells() {
        NavigationGrid grid = new NavigationGrid(6, 2);
        CellTopology topology = new CellTopology(6, 2);
        for (int x = 0; x < 6; x++) {
            grid.setWalkableFloor(x, 0);
            topology.setGroundKind(x, 0, GroundKind.INDOOR);
        }
        topology.setRoomPurpose(0, 0, RoomPurpose.VEHICLE_BAY);
        grid.setDoorway(0, 0, true);
        topology.setRoomPurpose(1, 0, RoomPurpose.BARRACKS);
        topology.setRoomPurpose(2, 0, RoomPurpose.VEHICLE_BAY);
        topology.setSurface(3, 0, topology.surfaceIndex("authored.floor"));
        topology.setGroundKind(4, 0, GroundKind.VOID);
        grid.setWalkable(5, 0, false);
        grid.setWallHp(5, 0, 83);
        topology.setWall(5, 0, true);
        topology.setSurface(5, 0, topology.surfaceIndex("urban.wall"));
        topology.setFixture(2, 0, true);
        new ShipMaterialStage().run(new GenContext(grid, topology, new Random(1), 6, 2, 1));
        assertEquals("ship.passage-deck", topology.getSurfaceId(0, 0));
        assertEquals("ship.crew-deck", topology.getSurfaceId(1, 0));
        assertEquals("auto-shop.concrete", topology.getSurfaceId(2, 0));
        assertEquals("authored.floor", topology.getSurfaceId(3, 0));
        assertNull(topology.getSurfaceId(4, 0));
        assertEquals("ship.neutral-bulkhead", topology.getSurfaceId(5, 0));
        assertTrue(grid.isWalkable(1, 0));
        assertFalse(grid.isWalkable(5, 0));
        assertEquals(83, grid.getWallHp(5, 0));
        assertTrue(topology.isFixture(2, 0));
        assertEquals(GroundKind.VOID, topology.getGroundKind(4, 0));
    }
}
