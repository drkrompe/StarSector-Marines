package com.dillon.starsectormarines.battle.decision.goap.action;

import org.junit.jupiter.api.Test;

import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class DistinctStandsTest {

    private static final int WIDTH = 10;

    /** Everyone prefers (5,5), then the cells east of it in turn. */
    private static int[] convergent(int ordinal, BiPredicate<Integer, Integer> taken) {
        for (int x = 5; x < WIDTH; x++) if (!taken.test(x, 5)) return new int[]{x, 5};
        return new int[]{5, 5};
    }

    @Test
    void laterOrdinalsSkipEarlierPicks() {
        int[] cells = DistinctStands.assign(3, WIDTH, DistinctStandsTest::convergent);
        assertArrayEquals(new int[]{55, 56, 57}, cells);
    }

    @Test
    void aMemberWithNoStandTakesNothing() {
        int[] cells = DistinctStands.assign(3, WIDTH,
                (ordinal, taken) -> ordinal == 0 ? null : convergent(ordinal, taken));
        assertArrayEquals(new int[]{DistinctStands.NONE, 55, 56}, cells);
    }

    @Test
    void withNothingElseFreeACellIsShared() {
        int[] cells = DistinctStands.assign(2, WIDTH, (ordinal, taken) -> new int[]{5, 5});
        assertArrayEquals(new int[]{55, 55}, cells);
    }
}
