package com.dillon.starsectormarines.battle.world.gen;

import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a volcanic world's older flows lie ({@link LavaFlows}). */
class LavaFlowsTest {

    private static final int W = 120, H = 90;
    /** A channel down the middle, columns 58..62; a building's block east of it that no flow may take. */
    private static final int C0 = 58, C1 = 62, BX0 = 70, BX1 = 80, BY0 = 30, BY1 = 50;

    private static boolean[] channel() {
        boolean[] lava = new boolean[W * H];
        for (int y = 0; y < H; y++) for (int x = C0; x <= C1; x++) lava[y * W + x] = true;
        return lava;
    }

    private static final IntPredicate FREE = i -> {
        int x = i % W, y = i / W;
        return !(x >= C0 && x <= C1) && !(x >= BX0 && x <= BX1 && y >= BY0 && y <= BY1);
    };

    @Test
    void flowsBreakOutOfTheChannelAndTakeOnlyFreeGround() {
        boolean[] lava = channel();
        for (int seed = 1; seed <= 6; seed++) {
            boolean[] flows = LavaFlows.offChannel(W, H, lava, FREE, i -> false, seed);
            int cells = 0, onBank = 0;
            for (int i = 0; i < flows.length; i++) {
                if (!flows[i]) continue;
                cells++;
                assertTrue(FREE.test(i), "seed " + seed + ": a flow on taken ground at " + i % W + "," + i / W);
                int x = i % W;
                if (x == C0 - 1 || x == C1 + 1) onBank++;
            }
            assertTrue(cells > 40, "seed " + seed + ": flows are laid: " + cells);
            assertTrue(onBank > 0, "seed " + seed + ": a flow is rooted on the bank");
        }
    }

    @Test
    void aFlowCrossesTheLipToTheMeltButNeverACrossing() {
        boolean[] lava = channel();
        // Two cells of lip each side of the channel are kept clear: only the lip predicate lets a flow onto them,
        // and rows 40..43 are a crossing that it never lets.
        IntPredicate free = i -> FREE.test(i) && Math.abs(i % W - 60) > 4;
        IntPredicate lip = i -> i / W < 40 || i / W > 43;
        for (int seed = 1; seed <= 6; seed++) {
            boolean[] flows = LavaFlows.offChannel(W, H, lava, free, lip, seed);
            boolean touches = false;
            for (int i = 0; i < flows.length; i++) {
                if (!flows[i]) continue;
                assertTrue(free.test(i) || lip.test(i) && Math.abs(i % W - 60) <= 2 + LavaFlows.ROOT_REACH,
                        "seed " + seed + ": a flow off its ground at " + i % W + "," + i / W);
                touches |= i % W == C0 - 1 || i % W == C1 + 1;
            }
            assertTrue(touches, "seed " + seed + ": a flow reaches the melt");
        }
    }

    @Test
    void noGroundLeavesNoFlow() {
        boolean[] flows = LavaFlows.offChannel(W, H, channel(), i -> false, i -> false, 1);
        for (boolean f : flows) assertFalse(f);
    }
}
