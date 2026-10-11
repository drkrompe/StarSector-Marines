package com.dillon.starsectormarines.battle.world;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WallRemnantTest {
    @Test void onlyCollapseRemembersTheMaterialAndRevealsTheNeighbouringFloor() {
        NavigationGrid grid = new NavigationGrid(5, 5);
        CellTopology topology = new CellTopology(5, 5);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
            grid.setWalkableFloor(x, y);
            topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
            topology.setSurface(x, y, topology.surfaceIndex("colony.limestone"));
        }
        wall(grid, topology, 1, 2); wall(grid, topology, 2, 2); wall(grid, topology, 3, 2);
        MapEditor editor = new MapEditor(new NavigationService(grid, topology));
        assertFalse(editor.damageWall(2, 2, 30));
        assertNull(topology.getWallRemnant(2, 2));
        assertTrue(editor.damageWall(2, 2, 70));
        var remnant = topology.getWallRemnant(2, 2);
        assertEquals("colony.masonry-wall", remnant.materialId());
        assertEquals("colony.limestone", remnant.groundSurfaceId());
        assertEquals(CellTopology.WALL_DIR_N | CellTopology.WALL_DIR_S, remnant.exposedFaces());
        assertTrue(grid.isWalkable(2, 2));
        assertTrue(topology.isRubble(2, 2));
        assertFalse(topology.isWall(2, 2));
        assertNull(topology.getSurfaceId(2, 2), "an intact-wall surface must never leak into floor rendering");
        assertFalse(editor.damageWall(2, 2, 100));
        assertEquals(remnant, topology.getWallRemnant(2, 2));
        assertTrue(editor.damageWall(1, 2, 100));
        assertEquals(CellTopology.WALL_DIR_N | CellTopology.WALL_DIR_S | CellTopology.WALL_DIR_W,
                topology.getWallRemnant(1, 2).exposedFaces(), "a collapsed neighbour still joins the original run");
        assertEquals(remnant, topology.getWallRemnant(2, 2), "later breaches cannot rotate earlier rubble");
    }

    @Test void ordinaryStructureRubbleDoesNotClaimAFormerWall() {
        NavigationGrid grid = new NavigationGrid(3, 3);
        CellTopology topology = new CellTopology(3, 3);
        MapEditor editor = new MapEditor(new NavigationService(grid, topology));
        editor.flipCellToRubble(1, 1);
        assertNull(topology.getWallRemnant(1, 1));
        assertTrue(topology.isRubble(1, 1));
    }

    private static void wall(NavigationGrid grid, CellTopology topology, int x, int y) {
        grid.setWalkable(x, y, false); grid.setWallHp(x, y, 100);
        topology.setWall(x, y, true);
        topology.setSurface(x, y, topology.surfaceIndex("colony.masonry-wall"));
    }
}
