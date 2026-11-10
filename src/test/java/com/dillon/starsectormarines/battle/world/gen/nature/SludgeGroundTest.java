package com.dillon.starsectormarines.battle.world.gen.nature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a toxic world's sludge pools may lie, and the sulphur that rings them. */
class SludgeGroundTest {

    private static final int W = 72, H = 72;

    /** A pool neither takes nor borders ground the world refused it: a road, a building, a reservation. */
    @Test
    void aPoolKeepsItsRingClearOfRefusedGround() {
        boolean[] pools = SludgeGround.pools(W, H, 11L, i -> i % W != 30, (e, l, b, r, t, c) -> true);
        boolean any = false;
        for (int i = 0; i < W * H; i++) {
            if (!pools[i]) continue;
            any = true;
            assertTrue(Math.abs(i % W - 30) > SludgeGround.RING, "pool cell " + i % W + "," + i / W);
        }
        assertTrue(any, "open ground on both sides still holds pools");
    }

    /** A pool that would cut the ground around it in two is not laid. */
    @Test
    void aPoolThatWouldStrandGroundIsRefused() {
        boolean[] pools = SludgeGround.pools(W, H, 11L, i -> true, (e, l, b, r, t, c) -> false);
        for (boolean pool : pools) assertFalse(pool);
    }

    /** Every pool is ringed with crusted sulphur, so a pool never meets bare rock. */
    @Test
    void sulphurRingsEveryPool() {
        boolean[] pools = SludgeGround.pools(W, H, 5L, i -> true, (e, l, b, r, t, c) -> true);
        boolean[] sulphur = SludgeGround.flats(W, H, 5L, pools);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (!pools[y * W + x]) continue;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int i = (y + dy) * W + x + dx;
                assertTrue(pools[i] || sulphur[i], "bare rock beside the pool at " + (x + dx) + "," + (y + dy));
            }
        }
    }
}
