package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LivingGroundTest {

    @Test
    void tundraGroundIsSnowThenBareEarthThenGrass() {
        assertEquals(GroundKind.SNOW, LivingGround.tundraGround(LivingGround.SNOW - .01f, 0f));
        assertEquals(GroundKind.DIRT, LivingGround.tundraGround(LivingGround.SNOW, LivingGround.PERMAFROST - .01f));
        assertEquals(GroundKind.GRASS, LivingGround.tundraGround(LivingGround.SNOW, LivingGround.PERMAFROST));
    }

    @Test
    void edgesCountInwardFromEachSide() {
        assertArrayEquals(new int[]{2, 5}, LivingGround.cell(0, 5, 2, 10, 8));
        assertArrayEquals(new int[]{7, 5}, LivingGround.cell(1, 5, 2, 10, 8));
        assertArrayEquals(new int[]{5, 2}, LivingGround.cell(2, 5, 2, 10, 8));
        assertArrayEquals(new int[]{5, 5}, LivingGround.cell(3, 5, 2, 10, 8));
        assertNull(LivingGround.cell(0, 5, 12, 10, 8));
    }

    @Test
    void seaDepthsStayInBounds() {
        for (int depth : LivingGround.seaDepths(300, 42L)) {
            assertTrue(depth >= LivingGround.SEA_MIN && depth <= LivingGround.SEA_MAX, "depth " + depth);
        }
    }

    /** A sea along an edge leaves the country whole; one cut right across it strands the far side. */
    @Test
    void aSeaIsLaidOnlyWhereTheCountryStaysWhole() {
        NavigationGrid grid = new NavigationGrid(10, 8);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 10; x++) {
            grid.setWalkable(x, y, true);
            for (Direction d : Direction.CARDINALS) grid.setEdgePassable(x, y, d, true);
        }
        boolean[] strip = new boolean[80], across = new boolean[80];
        for (int y = 0; y < 8; y++) { strip[y * 10] = true; strip[y * 10 + 1] = true; across[y * 10 + 5] = true; }
        assertTrue(LivingGround.keepsWhole(grid, strip));
        assertFalse(LivingGround.keepsWhole(grid, across));
    }
}
