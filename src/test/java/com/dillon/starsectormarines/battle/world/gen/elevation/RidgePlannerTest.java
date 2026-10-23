package com.dillon.starsectormarines.battle.world.gen.elevation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RidgePlannerTest {

    private static final int W = 120, H = 60;
    /** A road crossing the band at x 58-61: protected ground the ridge must not touch. */
    private static boolean free(int i) {
        int x = i % W;
        return x < 58 || x > 61;
    }

    @Test
    void protectedGroundAcrossTheBandIsAPassBetweenTwoSegments() {
        RidgePlanner.Band band = new RidgePlanner.Band(4, 20, W - 5, 29, true, -1, 5);
        List<int[]> runs = RidgePlanner.runs(band, W, H, RidgePlannerTest::free);
        assertEquals(2, runs.size());
        assertTrue(runs.get(0)[1] < 58 && runs.get(1)[0] > 61, "the road lies between the segments");
    }

    @Test
    void everySegmentIsRaisedAndRampedFromItsSide() {
        RidgePlanner.Band band = new RidgePlanner.Band(4, 20, W - 5, 29, true, -1, 5);
        ElevationPlan plan = RidgePlanner.plan(W, H, 1L, List.of(band), RidgePlannerTest::free, layout -> true);
        assertNotNull(plan);
        assertEquals(2, plan.plateaus().size());
        assertEquals(2, plan.ramps().size());
        for (ElevationPlan.Ramp ramp : plan.ramps()) assertTrue(ramp.foot().y() < ramp.head().y(), "climbed from the low side");
        ElevationLayout layout = ElevationLayout.resolve(plan);
        for (int y = 0; y < H; y++) for (int x = 58; x <= 61; x++) assertTrue(!layout.touched(x, y), "the pass stays open");
    }
}
