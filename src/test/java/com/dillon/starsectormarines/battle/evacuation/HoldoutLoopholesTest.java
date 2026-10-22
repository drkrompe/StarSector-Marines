package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class HoldoutLoopholesTest {

    @Test
    void loopholesAreSpacedAlongAWallAndCentredOnIt() {
        assertArrayEquals(new int[0], HoldoutLoopholes.positions(HoldoutLoopholes.MIN_RUN - 1), "a stub stays solid");
        assertArrayEquals(new int[]{1}, HoldoutLoopholes.positions(3), "a short wall gets one, in its middle");
        assertArrayEquals(new int[]{0, 4}, HoldoutLoopholes.positions(5));
        assertArrayEquals(new int[]{1, 5}, HoldoutLoopholes.positions(7), "centred, not crowding one end");
        assertArrayEquals(new int[]{0, 4, 8, 12}, HoldoutLoopholes.positions(13));
    }
}
