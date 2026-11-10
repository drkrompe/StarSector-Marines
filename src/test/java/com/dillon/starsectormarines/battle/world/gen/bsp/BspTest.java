package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BspTest {

    private static final int W = 72, H = 56;

    /**
     * Every lane one partition cuts is the width it was asked for, so the lanes read as one class of street: two
     * blocks facing each other over nothing but road, along more than a lane's width of their sides, stand one lane
     * apart.
     */
    @Test
    void everyLaneACutCarvesIsOneWidth() {
        for (int lane : new int[]{Bsp.LANE_WIDTH_WITH_TRUNKS, Bsp.LANE_WIDTH}) {
            for (long seed = 1; seed <= 8; seed++) {
                boolean[][] road = new boolean[W][H];
                List<BlockLeaf> leaves = new ArrayList<>();
                Bsp.partitionRect(1, 1, W - 2, H - 2, W, H, lane, road, leaves, new Random(seed));
                assertTrue(leaves.size() > 4, "the partition cut nothing");
                int facing = 0;
                for (BlockLeaf a : leaves) for (BlockLeaf b : leaves) {
                    int gap = facing(a, b, road);
                    if (gap < 0) continue;
                    assertEquals(lane, gap, "seed " + seed + ": blocks at " + a.left + "," + a.top + " and " + b.left + "," + b.top);
                    facing++;
                }
                assertTrue(facing > 0, "no two blocks face each other");
            }
        }
    }

    /**
     * The road between {@code a} and {@code b} where {@code b} lies east or south of {@code a} and they overlap along
     * more than a lane with only road between them; -1 where they do not face each other so.
     */
    private static int facing(BlockLeaf a, BlockLeaf b, boolean[][] road) {
        if (b.left > a.right) {
            int lo = Math.max(a.top, b.top), hi = Math.min(a.bottom, b.bottom);
            if (hi - lo + 1 <= Bsp.LANE_WIDTH) return -1;
            for (int x = a.right + 1; x < b.left; x++) for (int y = lo; y <= hi; y++) if (!road[x][y]) return -1;
            return b.left - a.right - 1;
        }
        if (b.top > a.bottom) {
            int lo = Math.max(a.left, b.left), hi = Math.min(a.right, b.right);
            if (hi - lo + 1 <= Bsp.LANE_WIDTH) return -1;
            for (int y = a.bottom + 1; y < b.top; y++) for (int x = lo; x <= hi; x++) if (!road[x][y]) return -1;
            return b.top - a.bottom - 1;
        }
        return -1;
    }
}
