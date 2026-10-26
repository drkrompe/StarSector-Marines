package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where the outer picket stands, and when it falls back. */
class CordonOuterPicketTest {

    private static final CordonOuterPicket.CellTest OPEN = (x, y) -> true;

    @Test
    void aPostStandsOnTheStreetOutInFrontOfTheMouth() {
        // Front north (+y), front wall on row 100, a street down x = 53.
        int[] at = CordonOuterPicket.postCell(OPEN, (x, y) -> x == 53, 50, 100, 1, List.of());
        assertArrayEquals(new int[]{53, 100 + CordonOuterPicket.DEPTH}, at);
        // Front south: the post is below the wall.
        assertArrayEquals(new int[]{50, 100 - CordonOuterPicket.DEPTH},
                CordonOuterPicket.postCell(OPEN, (x, y) -> false, 50, 100, -1, List.of()));
    }

    @Test
    void aPostKeepsOffTheBoresComingNearerTheLineWhereItMust() {
        int[] bore = {50, 100 + CordonOuterPicket.DEPTH + 20};
        int[] at = CordonOuterPicket.postCell(OPEN, (x, y) -> false, 50, 100, 1, List.of(bore));
        assertTrue(Math.hypot(at[0] - bore[0], at[1] - bore[1]) >= CordonOuterPicket.BORE_CLEAR);
        assertTrue(at[1] > 100 && at[1] >= 100 + CordonOuterPicket.MIN_DEPTH);
        assertNull(CordonOuterPicket.postCell((x, y) -> false, (x, y) -> false, 50, 100, 1, List.of()));
    }

    @Test
    void aPostFallsBackToTheNearestCellInsideItsRedoubtItCanReach() {
        CordonRedoubt r = new CordonRedoubt(2, 10, 20, 30, 40, List.of(), List.of(), 0);
        assertArrayEquals(new int[]{20, 30}, CordonOuterPicket.fallBackCell(OPEN, r));
        // The middle and the ring round it cannot be reached: the next ring out can.
        int[] at = CordonOuterPicket.fallBackCell((x, y) -> Math.max(Math.abs(x - 20), Math.abs(y - 30)) >= 2, r);
        assertTrue(Math.max(Math.abs(at[0] - 20), Math.abs(at[1] - 30)) == 2 && r.contains(at[0], at[1], 0));
        assertArrayEquals(new int[]{20, 30}, CordonOuterPicket.fallBackCell((x, y) -> false, r), "none: the middle");
    }

    @Test
    void aPostFallsBackWhenHalfIsDownOrItIsOutnumberedTwoToOne() {
        assertFalse(CordonOuterPicket.fallsBack(4, 4, 0), "nothing on it");
        assertFalse(CordonOuterPicket.fallsBack(4, 4, 7));
        assertTrue(CordonOuterPicket.fallsBack(4, 4, 8));
        assertTrue(CordonOuterPicket.fallsBack(2, 4, 1));
        assertFalse(CordonOuterPicket.fallsBack(0, 4, 5), "nobody left to fall back");
    }
}
