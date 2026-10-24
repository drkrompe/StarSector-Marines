package com.dillon.starsectormarines.battle.command.allied;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AlliedCommandTest {

    @Test
    void aRefusedGoalIsPulledBackToTheCornerHalfwayAlongTheWay() {
        // An L: 10 cells east, then 30 north. Halfway (20 of 40) is nearest the corner 10 cells up the second leg.
        float[] xs = {0, 10, 10, 10, 10};
        float[] ys = {0, 0, 10, 20, 30};
        assertArrayEquals(new int[]{10, 10}, AlliedCommand.midCorner(xs, ys));
        assertNull(AlliedCommand.midCorner(new float[]{0, 5}, new float[]{0, 5}));
    }
}
