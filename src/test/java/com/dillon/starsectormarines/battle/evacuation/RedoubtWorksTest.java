package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void cornerGunsGoDiagonalFromTheGateThenToTheFarSidesOtherCorner() {
        // The gate near the west end of the south side: the north-east corner is diagonal from it, then the north-west.
        var corners = RedoubtWorks.cornersFromGate(L, T, R, B, L + 2, B);
        assertArrayEquals(new int[]{R, T}, corners.get(0));
        assertArrayEquals(new int[]{L, T}, corners.get(1));
        assertEquals(4, corners.size());
    }

    @Test
    void aStreetCrossingASideLeavesItWhereItIs() {
        // A street three wide running north from the building's middle: the works cross it, as a truck closes it.
        RedoubtWorks.Roadway crossing = (x, y) -> x >= 12 && x <= 14 && y < T;
        assertArrayEquals(new int[]{4, 4, 4, 4}, RedoubtWorks.margins(crossing, L, T, R, B, 4));
    }

    @Test
    void roomIsTheOpenShareOfTheBarricadeAndStepLines() {
        // A building alone in a yard: every cell of both lines is open.
        assertEquals(1f, RedoubtWorks.room((x, y) -> true, L, T, R, B, 4));
        // A street along the west side, over both lines: one side of four lost.
        RedoubtWorks.Ground street = (x, y) -> x > 7;
        float fronting = RedoubtWorks.room(street, L, T, R, B, 4);
        assertTrue(fronting < RedoubtWorks.MIN_ROOM, "a street-fronting block is cramped: " + fronting);
        // Lines 4 and 3 out have perimeters of 60 and 52 cells. The street takes the
        // outer line's west column (16) and the corner cell beside it on each end (2),
        // and the inner line's west column (14).
        assertEquals((60 - 18 + 52 - 14) / 112f, fronting, 1e-6f);
        // A street crossing one side only takes the cells it crosses.
        RedoubtWorks.Ground crossing = (x, y) -> !(x >= 12 && x <= 14 && y < T);
        assertTrue(RedoubtWorks.room(crossing, L, T, R, B, 4) >= RedoubtWorks.MIN_ROOM);
    }
}
