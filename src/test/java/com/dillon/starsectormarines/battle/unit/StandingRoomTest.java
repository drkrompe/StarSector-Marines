package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ring walk that finds a displaced body somewhere to stand. */
public class StandingRoomTest {

    private static final StandingRoom.Standable ANYWHERE = (x, y) -> true;
    private static final StandingRoom.Taken NOBODY = (x, y) -> false;

    @Test
    void takesTheNearestRingRatherThanACanonicalCorner() {
        long cell = StandingRoom.nearest(10, 10, 3, ANYWHERE, NOBODY);

        assertEquals(1, Math.max(Math.abs(StandingRoom.cellX(cell) - 10),
                Math.abs(StandingRoom.cellY(cell) - 10)),
                "a free neighbour is one ring out, so the walk should stop there");
    }

    @Test
    void neverAnswersWithTheCellItWasAskedAbout() {
        long cell = StandingRoom.nearest(4, 7, 3, ANYWHERE, NOBODY);

        assertTrue(StandingRoom.cellX(cell) != 4 || StandingRoom.cellY(cell) != 7,
                "the contested cell is the one place the body may not be left");
    }

    @Test
    void reachesPastAFullRingToTheNextOne() {
        long cell = StandingRoom.nearest(0, 0, 3, ANYWHERE,
                (x, y) -> Math.max(Math.abs(x), Math.abs(y)) <= 1);

        assertEquals(2, Math.max(Math.abs(StandingRoom.cellX(cell)),
                Math.abs(StandingRoom.cellY(cell))));
    }

    @Test
    void reportsNowhereRatherThanPickingUnstandableGround() {
        long cell = StandingRoom.nearest(0, 0, 3, (x, y) -> false, NOBODY);

        assertEquals(StandingRoom.NOWHERE, cell,
                "a caller that cannot place the body must be told so, not handed a wall");
    }
}
