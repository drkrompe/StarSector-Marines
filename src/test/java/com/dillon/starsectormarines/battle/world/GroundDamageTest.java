package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundDamageTest {
    @Test void blastMarksItsCircleAndFreshBreachButNotFloorBehindSurvivingWalls() {
        NavigationGrid grid = new NavigationGrid(9,7);
        CellTopology topology = new CellTopology(9,7);
        for(int y=0;y<7;y++) for(int x=0;x<9;x++) {
            grid.setWalkableFloor(x,y);
            topology.setGroundKind(x,y,CellTopology.GroundKind.GRASS);
        }
        for(int y=0;y<7;y++) {
            grid.setWalkable(4,y,false); grid.setSeeThrough(4,y,false);
            topology.setWall(4,y,true);
        }
        MapEditor editor = new MapEditor(new NavigationService(grid,topology));
        editor.damageGroundInRadius(2.5f,3.5f,3);
        assertTrue(topology.hasTag(2,3,CellTopology.Tag.GROUND_DAMAGED));
        assertTrue(topology.hasTag(2,6,CellTopology.Tag.GROUND_DAMAGED));
        assertFalse(topology.hasTag(0,0,CellTopology.Tag.GROUND_DAMAGED),"outside the circle");
        assertFalse(topology.hasTag(5,3,CellTopology.Tag.GROUND_DAMAGED),"wall shields floor");
        grid.setWallHp(4,3,100);
        assertTrue(editor.damageWall(4,3,200));
        editor.damageGroundInRadius(4.5f,3.5f,2);
        assertTrue(topology.hasTag(5,3,CellTopology.Tag.GROUND_DAMAGED),"opened target exposes adjacent floor");
        assertFalse(topology.hasTag(4,3,CellTopology.Tag.GROUND_DAMAGED),"wall rubble keeps its own art");
        editor.damageGroundInRadius(0.2f,0.2f,0);
        assertTrue(topology.hasTag(0,0,CellTopology.Tag.GROUND_DAMAGED),"point structural hit still leaves a mark");
    }

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
