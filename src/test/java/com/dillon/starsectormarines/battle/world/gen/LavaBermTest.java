package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.elevation.RidgePlanner;

import java.util.List;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a volcanic town's lava river comes from and where its berm stands ({@link LavaBerm}). */
class LavaBermTest {

    private static final int W = 120, H = 90;
    /** A river down columns 58..62; the town's seed east of it, a little south of the middle. */
    private static final int C0 = 58, C1 = 62, TOWN_X = 112, TOWN_Y = 50;

    private static boolean[] river() {
        boolean[] lava = new boolean[W * H];
        for (int y = 0; y < H; y++) for (int x = C0; x <= C1; x++) lava[y * W + x] = true;
        return lava;
    }

    /** The town's own ground: everything from column 100 east. */
    private static final IntPredicate TOWN = i -> i % W >= 100;

    /** Open ground everywhere but the river and its lip, two cells either side, and the town's own ground. */
    private static final IntPredicate FREE = i -> Math.abs(i % W - 60) > 4 && !TOWN.test(i);

    @Test
    void theMeltComesFromTheEndFarthestFromTheTown() {
        assertArrayEquals(new int[]{C0, 0}, LavaBerm.source(W, H, river(), TOWN_X, TOWN_Y));
    }

    @Test
    void theBermStandsUpstreamBetweenTheRiverAndTheTownOnFreeGround() {
        LavaBerm.Plan plan = LavaBerm.plan(W, H, river(), TOWN_X, TOWN_Y, TOWN, FREE);
        assertNotNull(plan, "a berm fits");
        RidgePlanner.Band band = plan.band();
        assertEquals(RidgePlanner.Relief.BERM, band.relief());
        Point a = band.path().get(0), b = band.path().get(1);
        assertTrue(a.y() < TOWN_Y && a.x() > C1, "it leaves the town's bank upstream: " + a);
        assertTrue(b.y() > a.y() && b.x() > a.x(), "it slants out downstream: " + a + " -> " + b);
        assertTrue(Math.max(a.x(), b.x()) < TOWN_X, "it stands short of the town");
        boolean[][] held = RidgePlanner.heldGround(List.of(band), W, H, 0);
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) {
            if (held[x][y]) assertTrue(FREE.test(y * W + x), "held ground on taken ground at " + x + "," + y);
        }
        // The ramp climbs from the downstream face: the melt's source lies on the other side of the berm's line.
        double cross = (b.x() - a.x()) * (plan.sourceY() - a.y()) - (b.y() - a.y()) * (plan.sourceX() - a.x());
        assertEquals(cross > 0 ? -1 : 1, band.rampSide(), "the ramp faces away from the source");
    }

    @Test
    void noRoomLeavesNoBerm() {
        assertNull(LavaBerm.plan(W, H, river(), TOWN_X, TOWN_Y, TOWN, i -> false));
    }

    @Test
    void theAimedFlowPondsAgainstTheBermsFace() {
        boolean[] lava = river();
        LavaBerm.Plan plan = LavaBerm.plan(W, H, lava, TOWN_X, TOWN_Y, TOWN, FREE);
        assertNotNull(plan);
        boolean[] core = LavaBerm.core(plan, W, H);
        float[] aim = LavaBerm.aim(W, H, lava, plan, i -> true);
        assertNotNull(aim, "a breakout above the berm");
        boolean[] toe = LavaFlows.offChannelToward(W, H, lava, i -> FREE.test(i) && !core[i], i -> true,
                aim[0], aim[1], aim[2], (int) aim[3] + LavaBerm.THICKNESS, 5);
        boolean against = false;
        for (int i = 0; i < toe.length; i++) {
            if (!toe[i]) continue;
            assertFalse(core[i], "the flow climbs the berm at " + i % W + "," + i / W);
            int x = i % W, y = i / W;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                against |= nx >= 0 && ny >= 0 && nx < W && ny < H && core[ny * W + nx];
            }
        }
        assertTrue(against, "the flow's toe lies against the berm");
    }
}
