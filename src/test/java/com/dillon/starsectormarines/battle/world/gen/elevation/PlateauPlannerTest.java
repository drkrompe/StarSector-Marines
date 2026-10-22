package com.dillon.starsectormarines.battle.world.gen.elevation;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlateauPlannerTest {

    private static final int W = 90, H = 60, ROAD_Y = 6;

    /** Open ground with a road along the south edge, which the host keeps open. */
    private static boolean road(int idx) { return Math.abs(idx / W - ROAD_Y) <= 1; }

    private static boolean free(int idx) {
        int x = idx % W, y = idx / W;
        return !road(idx) && Math.abs(y - ROAD_Y) > 3 && x > 2 && y > 2 && x < W - 3 && y < H - 3;
    }

    @Test
    void plateausStayOnFreeGroundAndAreClimbedFromTheRoad() {
        ElevationPlan plan = PlateauPlanner.plan(W, H, 7L, 2, new Random(7L),
                PlateauPlannerTest::free, PlateauPlannerTest::road, layout -> true);
        assertNotNull(plan);
        ElevationLayout layout = ElevationLayout.resolve(plan);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
            if (layout.touched(x, y)) assertTrue(free(y * W + x), "touched " + x + "," + y);
        for (ElevationPlan.Ramp ramp : plan.ramps())
            assertTrue(ramp.foot().y() < ramp.head().y(), "a ramp climbs away from the road to its south");
    }

    @Test
    void aRefusedClosureOrNoRoomPlansNothing() {
        assertNull(PlateauPlanner.plan(W, H, 7L, 2, new Random(7L),
                PlateauPlannerTest::free, PlateauPlannerTest::road, layout -> false));
        assertNull(PlateauPlanner.plan(W, H, 7L, 2, new Random(7L),
                idx -> false, PlateauPlannerTest::road, layout -> true));
    }

    @Test
    void plateausNeverMerge() {
        ElevationPlan plan = PlateauPlanner.plan(W, H, 3L, 3, new Random(3L),
                PlateauPlannerTest::free, PlateauPlannerTest::road, layout -> true);
        assertNotNull(plan);
        assertFalse(plan.plateaus().isEmpty());
        // Resolving succeeds only if every plateau kept its own ramp.
        ElevationLayout.resolve(plan);
    }
}
