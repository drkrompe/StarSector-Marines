package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan.Profile;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan.Seed;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A place on a knoll grows only on its top, and no other place grows onto its top or its ring
 * ({@code elevation-nouns.md}, Knolls).
 */
class GrownTrunkBoundsTest {

    private static final int W = 120, H = 120;
    /** The bounded seed's top {x0, y0, x1, y1, ring}. */
    private static final int[] TOP = {40, 40, 80, 80, 10};

    @Test
    void anArmsWholeBandKeepsInsideItsOwnTopAndOffAnothersGround() {
        int[][] bounds = {TOP, null};
        // Its own: the band's edge, not only its middle line, stays on the top.
        assertTrue(GrownTrunkPlan.withinBounds(TOP, bounds, 0, 42, 60, 2));
        assertFalse(GrownTrunkPlan.withinBounds(TOP, bounds, 0, 41, 60, 2));
        // Another's: kept off the top and the ring, band and all.
        assertTrue(GrownTrunkPlan.withinBounds(null, bounds, 1, 27, 60, 2));
        assertFalse(GrownTrunkPlan.withinBounds(null, bounds, 1, 28, 60, 2));
    }

    @Test
    void aBoundedPlaceGrowsOnItsTopAndTheOthersAroundIt() {
        Profile town = Profile.of(0.7f, SettlementLink.ROAD);
        List<Seed> seeds = List.of(new Seed(60, 60, town), new Seed(15, 100, town));
        for (int seed = 0; seed < 8; seed++) {
            GrownTrunkPlan.Grown grown = GrownTrunkPlan.grow(W, H, new Random(seed), seeds, null,
                    new int[][]{TOP, null}, null);
            int[][] owner = grown.owner();
            for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) {
                boolean top = x >= TOP[0] && x <= TOP[2] && y >= TOP[1] && y <= TOP[3];
                boolean ground = x >= TOP[0] - TOP[4] && x <= TOP[2] + TOP[4] && y >= TOP[1] - TOP[4] && y <= TOP[3] + TOP[4];
                if (owner[x][y] == 0) assertTrue(top, "seed " + seed + ": the bounded place grew off its top at " + x + "," + y);
                if (owner[x][y] == 1) assertFalse(ground, "seed " + seed + ": another place grew onto the knoll at " + x + "," + y);
            }
        }
    }
}
