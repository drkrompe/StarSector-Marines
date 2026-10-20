package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class SteadyRalliesTest {

    private static final int[] BAND = {40, 100};
    private static final int[] WORKS = {60, 70};
    private static final int[] FURTHER = {40, 20};

    private static SteadyRallies<int[]> rallies() {
        return new SteadyRallies<>(new SteadyRallies.Cell<>() {
            @Override public int x(int[] post) { return post[0]; }
            @Override public int y(int[] post) { return post[1]; }
        });
    }

    @Test
    void aSwingBackToThePostJustLeftKeepsTheCurrentPost() {
        SteadyRallies<int[]> steady = rallies();
        assertSame(BAND, steady.steady(0, BAND, 0));
        assertSame(WORKS, steady.steady(0, WORKS, 75));
        assertSame(WORKS, steady.steady(0, BAND, 150), "back to the band within the dwell: keep the works");
        assertSame(WORKS, steady.steady(0, WORKS, 225));
    }

    @Test
    void aGenuinelyNewPostIsTakenAtOnce() {
        SteadyRallies<int[]> steady = rallies();
        steady.steady(0, BAND, 0);
        steady.steady(0, WORKS, 75);
        assertSame(FURTHER, steady.steady(0, FURTHER, 150));
    }

    @Test
    void theOldPostIsTakenOnceTheDwellHasRunOut() {
        SteadyRallies<int[]> steady = rallies();
        steady.steady(0, BAND, 0);
        steady.steady(0, WORKS, 75);
        assertSame(BAND, steady.steady(0, BAND, 75 + SteadyRallies.DWELL_TICKS));
    }

    @Test
    void aPostThatDriftsAFewCellsIsTheSamePostAndTracksAreIndependent() {
        SteadyRallies<int[]> steady = rallies();
        steady.steady(0, BAND, 0);
        steady.steady(0, WORKS, 75);
        int[] nearBand = {BAND[0] + SteadyRallies.KEEP_CELLS, BAND[1]};
        assertSame(WORKS, steady.steady(0, nearBand, 150));
        assertSame(BAND, steady.steady(1, BAND, 150), "another track has its own memory");
    }
}
