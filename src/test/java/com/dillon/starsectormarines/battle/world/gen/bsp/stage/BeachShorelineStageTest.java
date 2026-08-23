package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeachShorelineStageTest {

    @Test
    void shorelineWaterDoesNotCreateDirectionalCover() {
        int width = 40;
        int height = 30;
        long seed = 17L;
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(seed),
                width, height, seed);
        ctx.put(BspKeys.AXIS, TraversalAxis.SOUTH_TO_NORTH);
        ctx.put(BspKeys.BIOME_MAP, new BiomeMap(width, height,
                TraversalAxis.SOUTH_TO_NORTH, new Random(seed)));
        ctx.put(BspKeys.ROAD_RESERVATION, new boolean[width][height]);

        new BeachShorelineStage().run(ctx);
        new FinalizeStage().run(ctx);

        int testedBanks = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!topology.isWater(x, y)) continue;
                assertTrue(grid.isEdgeCoverSuppressed(x, y));
                for (int[] direction : directions) {
                    int bankX = x + direction[0];
                    int bankY = y + direction[1];
                    if (!grid.inBounds(bankX, bankY) || !grid.isWalkable(bankX, bankY)) continue;
                    assertEquals(0, grid.getCoverAt(bankX, bankY,
                            x - bankX, y - bankY));
                    testedBanks++;
                }
            }
        }
        assertTrue(testedBanks > 0, "representative shoreline should expose a walkable bank");
    }
}
