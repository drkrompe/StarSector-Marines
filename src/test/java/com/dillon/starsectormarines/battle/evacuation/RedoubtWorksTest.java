package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RedoubtWorksTest {

    /** A building on cells 10..17 both ways. */
    private static final int L = 10, T = 10, R = 17, B = 17;

    @Test
    void aSideLyingAlongAStreetIsDrawnInOffItsLanes() {
        // A street three wide down the west side, its near kerb a cell past where the fence would stand.
        RedoubtWorks.Roadway west = (x, y) -> x >= 4 && x <= 6;
        assertArrayEquals(new int[]{3, 4, 4, 4}, RedoubtWorks.margins(west, L, T, R, B, 4));
        // A road up to the step's line: the barricade stands at the kerb, with no step on that side.
        RedoubtWorks.Roadway kerb = (x, y) -> x >= 6 && x <= 8;
        assertArrayEquals(new int[]{1, 4, 4, 4}, RedoubtWorks.margins(kerb, L, T, R, B, 4));
        // A road against the building: drawn in as far as it goes; what lies on the road is left open.
        RedoubtWorks.Roadway against = (x, y) -> x >= 7 && x <= 9;
        assertArrayEquals(new int[]{RedoubtWorks.MIN_MARGIN, 4, 4, 4}, RedoubtWorks.margins(against, L, T, R, B, 4));
    }

    @Test
    void aStreetCrossingASideLeavesItWhereItIs() {
        // A street three wide running north from the building's middle: the works cross it, as a truck closes it.
        RedoubtWorks.Roadway crossing = (x, y) -> x >= 12 && x <= 14 && y < T;
        assertArrayEquals(new int[]{4, 4, 4, 4}, RedoubtWorks.margins(crossing, L, T, R, B, 4));
    }
}
