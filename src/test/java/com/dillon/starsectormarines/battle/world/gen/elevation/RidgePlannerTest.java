package com.dillon.starsectormarines.battle.world.gen.elevation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntPredicate;

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
        RidgePlanner.Band band = RidgePlanner.Band.rect(4, 20, W - 5, 29, true, -1, 5);
        List<int[]> runs = RidgePlanner.runs(band, W, H, RidgePlannerTest::free);
        assertEquals(2, runs.size());
        assertTrue(4 + runs.get(0)[1] < 58 && 4 + runs.get(1)[0] > 61, "the road lies between the segments");
    }

    @Test
    void everySegmentIsRaisedAndRampedFromItsSide() {
        RidgePlanner.Band band = RidgePlanner.Band.rect(4, 20, W - 5, 29, true, -1, 5);
        ElevationPlan plan = RidgePlanner.plan(W, H, 1L, List.of(band), RidgePlannerTest::free, layout -> true);
        assertNotNull(plan);
        assertEquals(2, plan.plateaus().size());
        assertEquals(2, plan.ramps().size());
        for (ElevationPlan.Ramp ramp : plan.ramps()) assertTrue(ramp.foot().y() < ramp.head().y(), "climbed from the low side");
        ElevationLayout layout = ElevationLayout.resolve(plan);
        for (int y = 0; y < H; y++) for (int x = 58; x <= 61; x++) assertTrue(!layout.touched(x, y), "the pass stays open");
    }

    @Test
    void aBandRunsOnThroughAStandingTerraceRatherThanBreakingIntoAPass() {
        // A place across the band, its road coming up to it from the south.
        TerracePlanner.Terrace place = new TerracePlanner.Terrace("place", 52, 18, 66, 31, true);
        IntPredicate road = i -> i % W >= 58 && i % W <= 61 && i / W <= 18;
        TerracePlanner.Planned terrace = TerracePlanner.plan(W, H, 1L, place, road, i -> true, i -> !road.test(i),
                i -> true);
        assertTrue(terrace.stands(), terrace.refusal());
        RidgePlanner.Band band = RidgePlanner.Band.rect(4, 20, W - 5, 29, true, -1, 5);
        ElevationPlan plan = RidgePlanner.plan(W, H, 1L, List.of(band), i -> !road.test(i), layout -> true,
                List.of(terrace));
        assertNotNull(plan);
        ElevationLayout layout = ElevationLayout.resolve(plan);
        for (int x = 36; x <= 84; x++) {
            if (layout.isRamp(x, 25)) continue;
            assertEquals(1, layout.tier(x, 25), "the crest runs on through the place at " + x);
            assertTrue(!layout.isFace(x, 25), "no cliff across the crest at " + x);
        }
        for (ElevationPlan.Ramp ramp : plan.ramps()) {
            if (terrace.ramps().contains(ramp)) continue;
            int fx = (int) Math.round(ramp.foot().x());
            assertTrue(fx < 44 || fx > 74, "no segment's ramp is cut into the terrace: " + ramp);
        }
    }

    @Test
    void heldGroundCoversEverythingTheBandCanRaise() {
        RidgePlanner.Band band = RidgePlanner.Band.rect(4, 20, W - 5, 29, true, -1, 5);
        boolean[][] held = RidgePlanner.heldGround(List.of(band), W, H, 3);
        ElevationPlan plan = RidgePlanner.plan(W, H, 1L, List.of(band), i -> true, layout -> true);
        ElevationLayout layout = ElevationLayout.resolve(plan);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (layout.touched(x, y)) assertTrue(held[x][y], "raised ground is held at " + x + "," + y);
        }
        assertTrue(!held[W / 2][2] && !held[W / 2][H - 3], "ground well clear of the band is not held");
    }
}
