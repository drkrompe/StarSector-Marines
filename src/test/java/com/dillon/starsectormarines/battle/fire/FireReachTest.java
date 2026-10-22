package com.dillon.starsectormarines.battle.fire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireReachTest {

    private static final int W = 40, H = 20;

    /** Grass everywhere except the columns given, which are bare. */
    private static FireField grassWithBreak(int fromX, int toX) {
        int[] fuel = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            fuel[y * W + x] = x >= fromX && x <= toX ? 0 : FireFuel.GRASS.bit;
        return new FireField(W, H, 1L, idx -> fuel[idx]);
    }

    @Test
    void aFireLitInTheMeadowYouStandInComesToYou() {
        FireReach reach = FireReach.of(grassWithBreak(-1, -1), 20, 10);
        assertTrue(reach.threatens(14, 10));
        assertTrue(reach.threatens(20 + FireReach.RADIUS, 10));
    }

    @Test
    void aBreakWiderThanHeatCrossesShieldsTheFarSide() {
        // Bare from 10 to 14: five cells, wider than the jump, so the reach stops at 15.
        FireReach reach = FireReach.of(grassWithBreak(10, 14), 18, 10);
        assertTrue(reach.threatens(14, 10), "the cell beside reached ground is threatened");
        assertFalse(reach.threatens(13, 10));
        assertFalse(reach.threatens(9, 10), "fuel beyond the break is not reached");
    }

    @Test
    void aGapHeatCrossesDoesNotShield() {
        FireReach reach = FireReach.of(grassWithBreak(13, 14), 20, 10);
        assertTrue(reach.threatens(11, 10));
    }

    @Test
    void bareGroundLitOnReachesNothing() {
        FireReach reach = FireReach.of(grassWithBreak(0, W - 1), 20, 10);
        assertFalse(reach.threatens(20, 10));
    }
}
