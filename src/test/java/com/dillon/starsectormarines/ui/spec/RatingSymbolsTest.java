package com.dillon.starsectormarines.ui.spec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** How a rating lights its ten symbols: an absolute scale, in quarters, never rounding up. */
class RatingSymbolsTest {

    @Test
    void aTenthOfTheRatingIsOneSymbolInQuartersFlooredNeverRoundedUp() {
        assertArrayEquals(new float[] {.5f, 0, 0, 0, 0, 0, 0, 0, 0, 0}, RatingSymbols.fills(.05f));
        assertArrayEquals(new float[] {.5f, 0, 0, 0, 0, 0, 0, 0, 0, 0}, RatingSymbols.fills(.07f));
        assertArrayEquals(new float[] {1, 1, 1, .25f, 0, 0, 0, 0, 0, 0}, RatingSymbols.fills(.33f));
        assertArrayEquals(new float[] {1, 1, 1, 1, 1, 1, 1, 1, 1, .75f}, RatingSymbols.fills(.99f));
    }

    @Test
    void ratingsOutsideTheScaleClampToEmptyOrFull() {
        assertArrayEquals(new float[10], RatingSymbols.fills(-1f));
        assertArrayEquals(new float[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, RatingSymbols.fills(2f));
    }
}
