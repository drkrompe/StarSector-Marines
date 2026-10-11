package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundDamageTest {
    @Test void floorDamagePreservesMaterialAndNavigationAndRecordsOnlyTheFirstHit() {
        NavigationGrid grid = new NavigationGrid(3, 3);
        CellTopology topology = new CellTopology(3, 3);
        grid.setWalkableFloor(1, 1);
        topology.setGroundKind(1, 1, CellTopology.GroundKind.TILE);
        topology.setSurface(1, 1, topology.surfaceIndex("colony.sage"));
        MapEditor editor = new MapEditor(new NavigationService(grid, topology));
        long before = topology.changeCount();
        assertTrue(editor.damageGround(1, 1));
        assertTrue(topology.hasTag(1, 1, CellTopology.Tag.GROUND_DAMAGED));
        assertEquals(before + 1, topology.changeCount(), "resident rendering must see the changed cell");
        assertEquals(4, topology.changedCellAt(before));
        assertEquals("colony.sage", topology.getSurfaceId(1, 1));
        assertEquals(CellTopology.GroundKind.TILE, topology.getGroundKind(1, 1));
        assertTrue(grid.isWalkable(1, 1));
        assertFalse(editor.damageGround(1, 1));
        assertEquals(before + 1, topology.changeCount());
    }

    @Test void wallsWaterVoidAndRubbleCannotBecomeDamagedFloor() {
        NavigationGrid grid = new NavigationGrid(3, 3);
        CellTopology topology = new CellTopology(3, 3);
        MapEditor editor = new MapEditor(new NavigationService(grid, topology));
        for (var kind : new CellTopology.GroundKind[]{CellTopology.GroundKind.WATER,
                CellTopology.GroundKind.VOID, CellTopology.GroundKind.RUBBLE}) {
            topology.setGroundKind(1, 1, kind);
            assertFalse(editor.damageGround(1, 1));
        }
        topology.setGroundKind(1, 1, CellTopology.GroundKind.INDOOR);
        topology.setWall(1, 1, true);
        assertFalse(editor.damageGround(1, 1));
        assertFalse(editor.damageGround(-1, 0));
        assertFalse(editor.damageGround(3, 3));
    }
}
