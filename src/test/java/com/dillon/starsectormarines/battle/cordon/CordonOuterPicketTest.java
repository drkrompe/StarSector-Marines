package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where the outer picket and the picket line's teams stand, and when they fall back. */
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

    @Test
    void aStretchOfPicketLineHoldsATeamEveryFiftyCellsOneToThree() {
        assertEquals(1, CordonOuterPicket.lineTeams(20));
        assertEquals(2, CordonOuterPicket.lineTeams(110));
        assertEquals(3, CordonOuterPicket.lineTeams(400));
        // Two teams on a course from x 100 to 199: each at the middle of its half.
        assertArrayEquals(new int[]{125, 175}, CordonOuterPicket.lineStops(100, 199, 2));
    }

    @Test
    void aLineTeamSitsOnTheTrenchNearestItsStopNeverOnAnotherTeamsSeat() {
        // A course along rows 50 (trench) and 51 (bags, not a fighting position) from x 0 to 39.
        List<int[]> course = new ArrayList<>();
        for (int x = 0; x < 40; x++) {
            course.add(new int[]{x, 50});
            course.add(new int[]{x, 51});
        }
        CordonOuterPicket.CellTest trench = (x, y) -> y == 50;
        List<int[]> seats = CordonOuterPicket.lineSeats(course, 20, List.of(new int[]{20, 50}), trench, 4);
        assertEquals(4, seats.size());
        for (int[] c : seats) {
            assertEquals(50, c[1], "the trench before the bags");
            assertTrue(Math.abs(c[0] - 20) <= 2 && c[0] != 20);
        }
    }

    @Test
    void aLineTeamFallsBackToTheNearestRedoubtStillManned() {
        CordonRedoubt west = new CordonRedoubt(1, 0, 0, 10, 10, List.of(), List.of(), 0);
        CordonRedoubt east = new CordonRedoubt(2, 100, 0, 110, 10, List.of(), List.of(), 0);
        assertSame(west, CordonOuterPicket.nearest(List.of(west, east), 40, 5));
        west.setState(CordonRedoubt.State.FALLEN);
        assertSame(east, CordonOuterPicket.nearest(List.of(west, east), 40, 5));
    }
}
