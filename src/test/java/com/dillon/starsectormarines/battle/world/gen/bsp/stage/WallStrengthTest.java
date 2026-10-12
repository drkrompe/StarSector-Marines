package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WallStrengthTest {
    @Test void finalizationFillsMissingStrengthWithoutFlatteningAuthoredFortifications() {
        var grid = new NavigationGrid(5, 1);
        var topology = new CellTopology(5, 1);
        grid.setWallHp(0, 0, 600);
        grid.setWallHp(1, 0, 80);
        topology.setFixture(3, 0, true);
        topology.setGroundKind(4, 0, CellTopology.GroundKind.WATER);
        FinalizeStage.seedWallHp(grid, topology);
        assertEquals(600, grid.getWallHp(0, 0));
        assertEquals(80, grid.getWallHp(1, 0));
        assertEquals(100, grid.getWallHp(2, 0));
        assertEquals(0, grid.getWallHp(3, 0));
        assertEquals(0, grid.getWallHp(4, 0));
        assertFalse(grid.damageCell(0, 0, 100));
        assertEquals(500, grid.getWallHp(0, 0));
        assertTrue(grid.damageCell(0, 0, 500));
        assertTrue(grid.isWalkable(0, 0));
    }
}
