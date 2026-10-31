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

    /** A berm is graded, its full width to square ends a few cells short of the pass, its faces straight. */
    @Test
    void aBermIsItsFullWidthToSquareEndsAndStraightAlong() {
        RidgePlanner.Band band = RidgePlanner.Band.rect(4, 20, W - 5, 29, true, -1, 5).berm();
        ElevationPlan plan = RidgePlanner.plan(W, H, 1L, List.of(band), RidgePlannerTest::free, layout -> true);
        assertNotNull(plan);
        assertEquals(2, plan.plateaus().size());
        for (ElevationPlan.Plateau plateau : plan.plateaus()) assertTrue(plateau.graded(), "a berm is graded");
        ElevationLayout layout = ElevationLayout.resolve(plan);
        assertTrue(layout.gradedCorner(30, 25), "the art draws it as a built bank");
        int end = 57;
        while (!layout.touched(end, 25)) end--;
        assertTrue(end >= 58 - 1 - RidgePlanner.END_GAP - RidgePlanner.BERM_END - 2, "runs to near the pass: " + end);
        int top = -1, bottom = -1;
        for (int y = 0; y < H; y++) {
            if (!layout.touched(30, y)) continue;
            if (top < 0) top = y;
            bottom = y;
        }
        for (int x = 12; x < end; x++) {
            assertTrue(layout.touched(x, top) && layout.touched(x, bottom), "full width to its square end at " + x);
            assertTrue(!layout.touched(x, top - 1) && !layout.touched(x, bottom + 1), "a straight face at " + x);
        }
    }

    /** Natural ground swells and narrows along its run, past the band's thickness and inside it. */
    @Test
    void naturalGroundVariesItsWidthAlongTheRun() {
        RidgePlanner.Band band = RidgePlanner.Band.rect(4, 20, W - 5, 29, true, -1, 5);
        ElevationPlan plan = RidgePlanner.plan(W, H, 7L, List.of(band), i -> true, layout -> true);
        assertNotNull(plan);
        ElevationLayout layout = ElevationLayout.resolve(plan);
        int widest = 0, narrowest = H;
        for (int x = 30; x <= W - 30; x++) {
            int across = 0;
            for (int y = 0; y < H; y++) if (layout.touched(x, y)) across++;
            widest = Math.max(widest, across);
            narrowest = Math.min(narrowest, across);
        }
        assertTrue(widest > band.thickness() + 2 && narrowest < band.thickness(),
                "widest " + widest + ", narrowest " + narrowest);
    }

    @Test
    void aBandRunsOnThroughAStandingTerraceRatherThanBreakingIntoAPass() {
        // A place across the band, its road coming up to it from the south.
        TerracePlanner.Terrace place = new TerracePlanner.Terrace("place", 52, 18, 66, 31, true);
        IntPredicate road = i -> i % W >= 58 && i % W <= 61 && i / W <= 18;
        TerracePlanner.Planned terrace = TerracePlanner.plan(W, H, 1L, place, road, i -> true, i -> !road.test(i),
                i -> true);
        assertTrue(terrace.stands(), terrace.refusal());
        // A berm, as the Cordon's line is: natural ground would wander off the row the crest is read along.
        RidgePlanner.Band band = RidgePlanner.Band.rect(4, 20, W - 5, 29, true, -1, 5).berm();
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

    /**
     * Sealed ground holds a band's sides and stops at its ends, so the pass a mission leaves between two bands stays
     * as wide as it was drawn whatever the margin; held ground's round ends would close it.
     */
    @Test
    void sideGroundLeavesThePassBetweenTwoBandsOpen() {
        RidgePlanner.Band west = RidgePlanner.Band.rect(4, 20, 40, 29, true, -1, 5);
        RidgePlanner.Band east = RidgePlanner.Band.rect(56, 20, W - 5, 29, true, -1, 5);
        boolean[][] side = RidgePlanner.sideGround(List.of(west, east), W, H, 7);
        boolean[][] held = RidgePlanner.heldGround(List.of(west, east), W, H, 7);
        for (int x = 42; x <= 54; x++) assertTrue(!side[x][24], "the pass is open at " + x);
        assertTrue(held[48][24], "held ground's round ends would close the pass");
        int reach = 44 + west.spread();
        assertTrue(side[20][reach - 6] && !side[20][reach], "the side reaches the band, its swell, its apron and the margin");
    }

    @Test
    void aDescentGradesTheBermDownItsFarSideToo() {
        RidgePlanner.Band band = RidgePlanner.Band.rect(4, 20, W - 5, 29, true, -1, 5).berm()
                .withDescents(List.of(new ElevationPlan.Point(30, 24.5)));
        ElevationPlan plan = RidgePlanner.plan(W, H, 1L, List.of(band), RidgePlannerTest::free, layout -> true);
        assertNotNull(plan);
        assertEquals(2, plan.plateaus().size(), "the descent costs no segment");
        // Wanted where the first segment's ramp up would stand: the ramp up gives way along the run.
        assertEquals(3, plan.ramps().size(), "a ramp up each segment, and one down the first");
        ElevationPlan.Ramp down = null;
        for (ElevationPlan.Ramp ramp : plan.ramps()) if (ramp.foot().y() > ramp.head().y()) down = ramp;
        assertNotNull(down, "climbed from the far side");
        assertEquals(30, down.foot().x(), 1.5);
    }
}
