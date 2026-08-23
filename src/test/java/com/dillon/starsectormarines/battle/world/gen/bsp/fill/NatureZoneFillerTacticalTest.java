package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileCover;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NatureZoneFillerTacticalTest {

    private static final int WIDTH = 36;
    private static final int HEIGHT = 28;
    private static final BlockLeaf BEACH = new BlockLeaf(1, 1, 34, 26, true);

    @Test
    void beachRocksApplyAuthoredCoverAndWindowLikeNavigation() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        GenContext ctx = new GenContext(grid, topology, new Random(42L),
                WIDTH, HEIGHT, 42L);

        new NatureZoneFiller(BlockKind.NATURE_BEACH).fill(BEACH, ctx);
        new FinalizeStage().run(ctx);

        TileRegistry registry = TileRegistry.installed();
        int mediumCount = 0;
        int largeCount = 0;
        int firstLargeX = -1;
        int firstLargeY = -1;
        for (int y = BEACH.top; y <= BEACH.bottom; y++) {
            for (int x = BEACH.left; x <= BEACH.right; x++) {
                int overlayIndex = topology.getNatureOverlayIndex(x, y);
                if (overlayIndex < 0) continue;
                TileDef def = registry.byIndex(overlayIndex);
                if (def.cover == TileCover.LIGHT) {
                    mediumCount++;
                    assertTrue(grid.isWalkable(x, y), "medium rock must remain standable");
                    assertFalse(topology.isFixture(x, y));
                }
                if (def.cover != TileCover.HEAVY) continue;
                largeCount++;
                if (firstLargeX < 0) {
                    firstLargeX = x;
                    firstLargeY = y;
                }
                assertFalse(grid.isWalkable(x, y), "large rock must block navigation");
                assertTrue(grid.isSeeThrough(x, y), "large rock must not block sight");
                assertFalse(grid.blocksLineOfSight(x, y));
                assertTrue(topology.isFixture(x, y), "large rock must use non-structural fixture law");
                assertFalse(topology.isWall(x, y), "large rock must not become a wall");
                assertEquals(0, grid.getWallHp(x, y), "large rock must not receive wall HP");
                assertTrue(x > BEACH.left && x < BEACH.right
                                && y > BEACH.top && y < BEACH.bottom,
                        "blocking rocks must leave the leaf perimeter open");
            }
        }

        assertTrue(mediumCount > 0, "representative beach should contain medium cover rocks");
        assertTrue(largeCount > 0, "representative beach should contain large cover rocks");
        assertAllWalkableCellsConnected(grid, BEACH);
        assertAdjacentGridCover(grid, firstLargeX, firstLargeY);
    }

    private static void assertAdjacentGridCover(NavigationGrid grid, int rockX, int rockY) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            int x = rockX + direction[0];
            int y = rockY + direction[1];
            if (!grid.isWalkable(x, y)) continue;
            assertEquals(1, grid.getCoverAt(x, y, rockX - x, rockY - y),
                    "large rock should provide window-like adjacent grid cover");
            return;
        }
        throw new AssertionError("large rock had no adjacent walkable firing cell");
    }

    private static void assertAllWalkableCellsConnected(NavigationGrid grid, BlockLeaf leaf) {
        boolean[][] reached = new boolean[leaf.width()][leaf.height()];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        int expected = 0;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (!grid.isWalkable(x, y)) continue;
                expected++;
                if (queue.isEmpty()) {
                    reached[x - leaf.left][y - leaf.top] = true;
                    queue.add(new int[]{x, y});
                }
            }
        }
        int visited = 0;
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            visited++;
            int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] direction : directions) {
                int x = cell[0] + direction[0];
                int y = cell[1] + direction[1];
                if (!leaf.contains(x, y) || !grid.isWalkable(x, y)) continue;
                int localX = x - leaf.left;
                int localY = y - leaf.top;
                if (reached[localX][localY]) continue;
                reached[localX][localY] = true;
                queue.add(new int[]{x, y});
            }
        }
        assertEquals(expected, visited, "blocking rocks must not create walkable islands");
    }
}
