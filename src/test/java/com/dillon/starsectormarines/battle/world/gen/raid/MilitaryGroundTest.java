package com.dillon.starsectormarines.battle.world.gen.raid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The parade square is laid on the largest open rectangle of the garrison's yard. */
class MilitaryGroundTest {

    /** The widest open block wins over a longer thin strip, and its bounds are inclusive. */
    @Test
    void theParadeSquareTakesTheLargestOpenRectangle() {
        boolean[][] open = new boolean[10][8];
        // A thin strip along the bottom row, 10 long (area 10).
        for (int x = 0; x < 10; x++) open[x][0] = true;
        // A 4 by 4 block at (5..8, 3..6) (area 16), with one hole beside it that must not join it.
        for (int x = 5; x <= 8; x++) for (int y = 3; y <= 6; y++) open[x][y] = true;
        open[4][5] = true;
        assertArrayEquals(new int[]{5, 3, 8, 6}, MilitaryGround.largestRectangle(open));
    }

    /** A side is lined end to end in runs of four, the last flush with its far end; too short a side has none. */
    @Test
    void aSideIsLinedToItsEnd() {
        assertArrayEquals(new int[]{2, 6}, MilitaryGround.runs(2, 9));
        assertArrayEquals(new int[]{2, 6, 7}, MilitaryGround.runs(2, 10));
        assertArrayEquals(new int[0], MilitaryGround.runs(2, 4));
    }

    @Test
    void noOpenGroundIsNoSquare() {
        assertNull(MilitaryGround.largestRectangle(new boolean[5][5]));
    }
}
