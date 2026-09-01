package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan.Profile;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.TrunkKind;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the grown graph is mostly made of.
 *
 * <p>A junction graph that branches has most of its junctions on its deepest
 * levels, so the bottom rung of {@code GrownTrunkPlan}'s class ladder — the one
 * that repeats — is the class nearly every arm ends up drawn at. That made the
 * ladder's length a quiet decision about the whole settlement: with only
 * {@link TrunkKind#PRIMARY} and {@link TrunkKind#SECONDARY} on it, a city
 * profile drew every street below the first branch as a width-5 cross-street
 * and the plan alone claimed half the map as carriageway before a building was
 * placed.
 *
 * <p>These ask the plan directly rather than generating maps. What can go wrong
 * is arithmetic — a rung removed, or a budget too small for the graph to reach
 * the bottom one — and a map is a slow and noisy way to see arithmetic.
 */
class GrownTrunkClassLadderTest {

    private static final int W = 80;
    private static final int H = 80;

    /**
     * The narrow rung is reachable, and reached. A ladder whose bottom rung no
     * junction ever gets to is the same settlement as one without it.
     */
    @Test
    void aCityGrowsArmsAtEveryClassOnTheLadder() {
        Set<TrunkKind> seen = EnumSet.noneOf(TrunkKind.class);
        for (long seed : new long[]{1L, 42L, 100L, 777L}) {
            GrownTrunkPlan.generate(W, H, new Random(seed), Profile.city())
                    .plan.trunks.forEach(t -> seen.add(t.kind));
        }
        assertEquals(EnumSet.allOf(TrunkKind.class), seen,
                "a city profile never grew an arm of every class, so the ladder has a rung "
                        + "the graph cannot reach and the settlement is made of the rest");
    }

    /** Most of a settlement is back street, so most of its arms are the bottom rung. */
    @Test
    void mostArmsAreTheNarrowestClass() {
        int narrow = 0;
        int all = 0;
        for (long seed : new long[]{1L, 42L, 100L, 777L}) {
            for (TrunkPlan.TrunkSegment t : GrownTrunkPlan
                    .generate(W, H, new Random(seed), Profile.city()).plan.trunks) {
                all++;
                if (t.kind == TrunkKind.TERTIARY) narrow++;
            }
        }
        assertTrue(narrow * 2 > all, "only " + narrow + " of " + all + " arms are the narrowest "
                + "class, so the settlement is mostly built of arterials");
    }

    /** Seeds the paving mean is taken over. Any fixed set does; this one is arbitrary. */
    private static final long[] SEEDS =
            {1L, 42L, 100L, 777L, 3L, 7L, 11L, 19L, 23L, 31L, 55L, 88L};

    /**
     * The plan is a road network, not the ground the town stands on.
     *
     * <p>Measured on the plan's own mask before BSP frames or any filler, so it
     * is the growth's own appetite and nothing else.
     *
     * <p>Stated as a mean over a fixed seed set rather than a per-seed cap,
     * because a cap cannot tell the two ladders apart. Over twenty seeds the
     * two-rung ladder paved 33–54% of the map and the three-rung one 27–46%:
     * the ranges overlap across a third of their span, so any per-seed
     * threshold either fails good maps or passes bad ones. What the ladder
     * moves is the whole distribution — 47% down to 38% — and the mean is the
     * statistic that says so. 42% sits between them with room on both sides.
     */
    @Test
    void aCityPlanDoesNotPaveMostOfTheMap() {
        double total = 0;
        for (long seed : SEEDS) {
            boolean[][] road = GrownTrunkPlan
                    .generate(W, H, new Random(seed), Profile.city()).plan.roadCells;
            int paved = 0;
            for (boolean[] column : road) {
                for (boolean cell : column) if (cell) paved++;
            }
            total += paved / (double) (W * H);
        }
        double mean = total / SEEDS.length;
        assertTrue(mean < 0.42, "the grown city plan alone paves " + Math.round(mean * 100)
                + "% of the map on average, which leaves the blocks as islands in it");
    }
}
